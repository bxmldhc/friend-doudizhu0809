package com.loves.doudizhu.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loves.doudizhu.BuildConfig
import com.loves.doudizhu.audio.AudioCue
import com.loves.doudizhu.audio.VoiceEventReducer
import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.model.Card
import com.loves.doudizhu.model.GameConfig
import com.loves.doudizhu.network.AccountStats
import com.loves.doudizhu.network.ApiResult
import com.loves.doudizhu.network.AuthApi
import com.loves.doudizhu.network.AuthSession
import com.loves.doudizhu.network.ConnectionState
import com.loves.doudizhu.network.InviteLink
import com.loves.doudizhu.network.OnlineClient
import com.loves.doudizhu.network.PublicPlayerStats
import com.loves.doudizhu.network.RemoteAction
import com.loves.doudizhu.network.AppUpdateInfo
import com.loves.doudizhu.network.UpdateApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.File

enum class AppConnectionState {
    OFFLINE,
    CONNECTING,
    ONLINE,
    RECONNECTING
}

data class HistoryEntry(
    val time: Long,
    val roomCode: String,
    val winner: String,
    val multiplier: Int,
    val scoreDelta: List<Int>,
    val players: List<String>
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: AppUpdateInfo) : UpdateUiState
    data class Downloading(val info: AppUpdateInfo, val progress: Int) : UpdateUiState
    data class Ready(val info: AppUpdateInfo, val apk: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

sealed interface PlayerProfileUiState {
    data object Hidden : PlayerProfileUiState
    data class Loading(val nickname: String) : PlayerProfileUiState
    data class Ready(val stats: PublicPlayerStats) : PlayerProfileUiState
    data class Unavailable(val nickname: String, val message: String) : PlayerProfileUiState
}

sealed interface GameUiState {
    data object Splash : GameUiState

    data class Auth(
        val serverUrl: String,
        val pendingRoom: String? = null,
        val loading: Boolean = false,
        val error: String? = null
    ) : GameUiState

    data class Home(
        val nickname: String,
        val serverUrl: String = DEFAULT_SERVER_URL,
        val connection: AppConnectionState = AppConnectionState.OFFLINE,
        val voiceEnabled: Boolean = true
    ) : GameUiState

    data class CreateRoom(
        val nickname: String,
        val serverUrl: String,
        val aiCount: Int = 2
    ) : GameUiState

    data class JoinRoom(
        val nickname: String,
        val serverUrl: String,
        val initialRoom: String = ""
    ) : GameUiState

    data class Connecting(val message: String, val canRetry: Boolean = false) : GameUiState

    data class Lobby(
        val config: GameConfig,
        val roomCode: String,
        val yourSeat: Int,
        val players: List<String>,
        val aiCount: Int,
        val requiredHumans: Int,
        val isHost: Boolean,
        val connection: AppConnectionState = AppConnectionState.ONLINE
    ) : GameUiState

    data class Playing(
        val config: GameConfig,
        val state: EngineState,
        val mySeat: Int,
        val roomCode: String,
        val players: List<String>,
        val isHost: Boolean,
        val voiceEnabled: Boolean = true,
        val connection: AppConnectionState = AppConnectionState.ONLINE
    ) : GameUiState

    data class History(
        val entries: List<HistoryEntry>,
        val stats: AccountStats? = null,
        val loading: Boolean = true,
        val error: String? = null
    ) : GameUiState
}

const val DEFAULT_SERVER_URL = "ws://114.55.142.169:18080"

class DoudizhuViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Splash)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _inviteConfirmation = MutableStateFlow<String?>(null)
    val inviteConfirmation: StateFlow<String?> = _inviteConfirmation.asStateFlow()

    private val _audioCues = MutableSharedFlow<AudioCue>(extraBufferCapacity = 8)
    val audioCues: SharedFlow<AudioCue> = _audioCues.asSharedFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _playerProfile = MutableStateFlow<PlayerProfileUiState>(PlayerProfileUiState.Hidden)
    val playerProfile: StateFlow<PlayerProfileUiState> = _playerProfile.asStateFlow()

    private val preferences = application.getSharedPreferences("friend_doudizhu", 0)
    private val authApi = AuthApi()
    private val updateApi = UpdateApi()
    private val legacyPlayerId = preferences.getString("playerId", null)
        ?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString().also { generatedId ->
            preferences.edit().putString("playerId", generatedId).apply()
        }

    private var authSession = loadAuthSession()
    private var initialized = false
    private var pendingInviteRoom: String? = null
    private var onlineClient: OnlineClient? = null
    private var config = GameConfig()
    private var roomCode = ""
    private var resumeToken: String? = null
    private var mySeat = 0
    private var playerNames = emptyList<String>()
    private var isHost = false
    private var lastRoomWasCreate = false
    private var lastRequestedRoom: String? = null
    private var lastRecordedGame = -1
    private var lastServerUrl = preferences.getString("serverUrl", DEFAULT_SERVER_URL)
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_SERVER_URL
    private var connection = AppConnectionState.OFFLINE
    private var voiceEnabled = preferences.getBoolean("voiceEnabled", true)
    private var updateChecked = false
    private val voiceEventReducer = VoiceEventReducer()
    private var voiceRoomCode = ""

    fun initialize() {
        if (initialized) return
        initialized = true
        val session = authSession
        if (session == null) {
            showAuth()
            return
        }
        viewModelScope.launch {
            when (val result = authApi.me(httpBaseUrl(lastServerUrl), session.authToken)) {
                is ApiResult.Success -> {
                    saveAuthSession(result.data)
                    continueAfterAuthentication()
                }
                is ApiResult.Failure -> {
                    clearAuthSession()
                    _uiState.value = GameUiState.Auth(
                        serverUrl = lastServerUrl,
                        pendingRoom = pendingInviteRoom,
                        error = if (result.isUnauthorized) {
                            "登录已过期，请重新登录"
                        } else {
                            "暂时无法验证登录状态：${result.message}"
                        }
                    )
                }
            }
        }
    }

    fun showHome() {
        if (!initialized) {
            initialize()
            return
        }
        if (authSession == null) {
            showAuth()
        } else {
            _uiState.value = homeState()
        }
    }

    fun submitAuth(register: Boolean, nickname: String, password: String, serverUrl: String) {
        val cleanName = nickname.trim()
        val cleanUrl = normalizeServerBase(serverUrl)
        when {
            cleanName.length !in 2..12 -> {
                updateAuthError("昵称需要 2 到 12 个字符")
                return
            }
            password.length !in 6..64 -> {
                updateAuthError("密码需要 6 到 64 个字符")
                return
            }
        }
        lastServerUrl = cleanUrl
        preferences.edit().putString("serverUrl", cleanUrl).apply()
        _uiState.value = GameUiState.Auth(
            serverUrl = cleanUrl,
            pendingRoom = pendingInviteRoom,
            loading = true
        )
        viewModelScope.launch {
            val result = if (register) {
                authApi.register(httpBaseUrl(cleanUrl), cleanName, password)
            } else {
                authApi.login(httpBaseUrl(cleanUrl), cleanName, password)
            }
            when (result) {
                is ApiResult.Success -> {
                    saveAuthSession(result.data)
                    continueAfterAuthentication()
                }
                is ApiResult.Failure -> {
                    _uiState.value = GameUiState.Auth(
                        serverUrl = cleanUrl,
                        pendingRoom = pendingInviteRoom,
                        error = result.message
                    )
                }
            }
        }
    }

    fun logout() {
        val session = authSession ?: return
        if (hasActiveRoom()) {
            _toast.value = "请先退出当前房间再退出账号"
            return
        }
        clearAuthSession()
        showAuth()
        viewModelScope.launch {
            authApi.logout(httpBaseUrl(lastServerUrl), session.authToken)
        }
    }

    fun handleInvite(rawUri: String?) {
        val room = InviteLink.parse(rawUri) ?: return
        pendingInviteRoom = room
        if (hasActiveRoom()) {
            _inviteConfirmation.value = room
            return
        }
        if (!initialized || authSession == null) {
            if (initialized) showAuth()
            return
        }
        joinRoom(room, lastServerUrl)
    }

    fun confirmInvite() {
        val room = _inviteConfirmation.value ?: return
        _inviteConfirmation.value = null
        leaveCurrentRoom()
        pendingInviteRoom = room
        if (authSession == null) showAuth() else joinRoom(room, lastServerUrl)
    }

    fun dismissInvite() {
        _inviteConfirmation.value = null
        pendingInviteRoom = null
    }

    fun openCreateRoom() {
        val session = authSession ?: return showAuth()
        val home = _uiState.value as? GameUiState.Home ?: return
        _uiState.value = GameUiState.CreateRoom(session.nickname, home.serverUrl, config.aiCount)
    }

    fun openJoinRoom() {
        val session = authSession ?: return showAuth()
        val home = _uiState.value as? GameUiState.Home ?: return
        _uiState.value = GameUiState.JoinRoom(
            nickname = session.nickname,
            serverUrl = home.serverUrl,
            initialRoom = pendingInviteRoom.orEmpty()
        )
    }

    fun openHistory() {
        val session = authSession ?: return showAuth()
        _uiState.value = GameUiState.History(entries = loadHistory())
        viewModelScope.launch {
            when (val result = authApi.stats(httpBaseUrl(lastServerUrl), session.authToken)) {
                is ApiResult.Success -> {
                    val current = _uiState.value as? GameUiState.History ?: return@launch
                    _uiState.value = current.copy(stats = result.data, loading = false)
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized) {
                        clearAuthSession()
                        showAuth("登录已过期，请重新登录")
                    } else {
                        val current = _uiState.value as? GameUiState.History ?: return@launch
                        _uiState.value = current.copy(loading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        voiceEnabled = enabled
        preferences.edit().putBoolean("voiceEnabled", enabled).apply()
        when (val current = _uiState.value) {
            is GameUiState.Home -> _uiState.value = current.copy(voiceEnabled = enabled)
            is GameUiState.Playing -> _uiState.value = current.copy(voiceEnabled = enabled)
            else -> Unit
        }
    }

    fun openPlayerProfile(seatIndex: Int) {
        val playing = _uiState.value as? GameUiState.Playing ?: return
        val seat = playing.state.seats.getOrNull(seatIndex) ?: return
        val entry = playing.state.leaderboard.firstOrNull { it.seatIndex == seatIndex }
        if (!seat.isHuman || entry?.isAi == true) {
            _playerProfile.value = PlayerProfileUiState.Unavailable(
                nickname = seat.name,
                message = "服务器 AI 没有长期战绩"
            )
            return
        }
        val playerId = entry?.playerId.orEmpty()
        val session = authSession
        if (playerId.isBlank() || session == null) {
            _playerProfile.value = PlayerProfileUiState.Unavailable(
                nickname = seat.name,
                message = "该玩家暂无可查询的账号战绩"
            )
            return
        }
        _playerProfile.value = PlayerProfileUiState.Loading(seat.name)
        viewModelScope.launch {
            when (val result = authApi.playerStats(httpBaseUrl(lastServerUrl), session.authToken, playerId)) {
                is ApiResult.Success -> _playerProfile.value = PlayerProfileUiState.Ready(result.data)
                is ApiResult.Failure -> _playerProfile.value = PlayerProfileUiState.Unavailable(
                    nickname = seat.name,
                    message = if (result.statusCode == 404) {
                        "该玩家还没有可展示的历史战绩"
                    } else {
                        result.message
                    }
                )
            }
        }
    }

    fun dismissPlayerProfile() {
        _playerProfile.value = PlayerProfileUiState.Hidden
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (_updateState.value is UpdateUiState.Downloading) return
        if (!manual && updateChecked) return
        updateChecked = true
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            when (val result = updateApi.latest(httpBaseUrl(lastServerUrl))) {
                is ApiResult.Success -> {
                    _updateState.value = if (result.data.versionCode > BuildConfig.VERSION_CODE) {
                        UpdateUiState.Available(result.data)
                    } else {
                        UpdateUiState.Idle
                    }
                    if (manual && result.data.versionCode <= BuildConfig.VERSION_CODE) {
                        _toast.value = "当前已是最新版本 ${BuildConfig.VERSION_NAME}"
                    }
                }
                is ApiResult.Failure -> {
                    _updateState.value = UpdateUiState.Idle
                    if (manual) _toast.value = "检查更新失败：${result.message}"
                }
            }
        }
    }

    fun downloadUpdate(info: AppUpdateInfo) {
        if (_updateState.value is UpdateUiState.Downloading) return
        _updateState.value = UpdateUiState.Downloading(info, 0)
        viewModelScope.launch {
            val targetDir = File(getApplication<Application>().cacheDir, "updates")
            when (
                val result = updateApi.download(info, targetDir) { progress ->
                    _updateState.value = UpdateUiState.Downloading(info, progress)
                }
            ) {
                is ApiResult.Success -> {
                    _updateState.value = UpdateUiState.Ready(info, result.data)
                }
                is ApiResult.Failure -> {
                    _updateState.value = UpdateUiState.Available(info)
                    _toast.value = result.message
                }
            }
        }
    }

    fun dismissUpdate() {
        val current = _updateState.value
        val forced = when (current) {
            is UpdateUiState.Available -> current.info.forceUpdate
            is UpdateUiState.Downloading -> current.info.forceUpdate
            is UpdateUiState.Ready -> current.info.forceUpdate
            else -> false
        }
        if (!forced && current !is UpdateUiState.Downloading) {
            _updateState.value = UpdateUiState.Idle
        }
    }

    fun backHome() {
        _uiState.value = homeState()
    }

    fun createRoom(serverUrl: String, aiCount: Int) {
        val session = authSession ?: return showAuth()
        config = GameConfig(
            humanCount = 3 - aiCount.coerceIn(0, 2),
            difficulty = config.difficulty,
            mode = config.mode
        ).normalized()
        connect(session.nickname, serverUrl, host = true, room = null)
    }

    fun joinRoom(room: String, serverUrl: String) {
        val session = authSession ?: return showAuth()
        val normalizedRoom = room.filter(Char::isDigit)
        if (normalizedRoom.length != 6) {
            _toast.value = "请输入六位数字房号"
            return
        }
        pendingInviteRoom = null
        connect(
            nickname = session.nickname,
            serverUrl = serverUrl,
            host = false,
            room = normalizedRoom,
            resumeCredential = null
        )
    }

    fun startHostGame() {
        onlineClient?.startGame()
    }

    fun restart() {
        if (isHost) onlineClient?.restartGame()
    }

    fun setReady(ready: Boolean) {
        onlineClient?.setReady(ready)
    }

    fun retryConnection() {
        val current = _uiState.value
        if (current is GameUiState.Connecting || current is GameUiState.Lobby || current is GameUiState.Playing) {
            val savedToken = resumeToken ?: loadResumeToken(roomCode, lastServerUrl)
            val reconnectRoom = if (!savedToken.isNullOrBlank() && roomCode.isNotBlank()) roomCode else null
            connect(
                nickname = authSession?.nickname.orEmpty(),
                serverUrl = lastServerUrl,
                host = isHost,
                room = reconnectRoom ?: if (lastRoomWasCreate) null else (lastRequestedRoom ?: roomCode),
                resumeCredential = savedToken
            )
        }
    }

    fun exitToHome() {
        leaveCurrentRoom()
        _uiState.value = homeState()
    }

    fun call(want: Boolean) {
        onlineClient?.sendAction(mySeat, RemoteAction.Call(want))
    }

    fun play(cards: List<Card>) {
        onlineClient?.sendAction(mySeat, RemoteAction.Play(cards))
    }

    fun pass() {
        onlineClient?.sendAction(mySeat, RemoteAction.Pass)
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun showMessage(message: String) {
        _toast.value = message
    }

    private fun continueAfterAuthentication() {
        val room = pendingInviteRoom
        if (room != null) {
            joinRoom(room, lastServerUrl)
        } else {
            _uiState.value = homeState()
            checkForUpdates(manual = false)
        }
    }

    private fun showAuth(error: String? = null) {
        _uiState.value = GameUiState.Auth(
            serverUrl = lastServerUrl,
            pendingRoom = pendingInviteRoom,
            error = error
        )
    }

    private fun updateAuthError(message: String) {
        val current = _uiState.value as? GameUiState.Auth
        _uiState.value = (current ?: GameUiState.Auth(lastServerUrl)).copy(
            loading = false,
            error = message
        )
    }

    private fun homeState(): GameUiState.Home {
        val session = authSession
        return if (session == null) {
            GameUiState.Home("", lastServerUrl, connection, voiceEnabled)
        } else {
            GameUiState.Home(session.nickname, lastServerUrl, connection, voiceEnabled)
        }
    }

    private fun connect(
        nickname: String,
        serverUrl: String,
        host: Boolean,
        room: String?,
        resumeCredential: String? = null
    ) {
        val session = authSession ?: return showAuth()
        val cleanName = session.nickname.ifBlank { nickname.trim().ifBlank { "朋友" } }
        val cleanUrl = normalizeWebSocketUrl(serverUrl)
        lastServerUrl = cleanUrl.removeSuffix("/ws")
        preferences.edit().putString("serverUrl", lastServerUrl).apply()
        isHost = host
        lastRoomWasCreate = room == null
        lastRequestedRoom = room
        connection = AppConnectionState.CONNECTING
        _uiState.value = GameUiState.Connecting(
            message = if (room == null) "正在创建房间" else "正在加入房间",
            canRetry = false
        )
        onlineClient?.close()
        onlineClient = OnlineClient(
            serverUrl = cleanUrl,
            name = cleanName,
            playerId = session.accountId.ifBlank { legacyPlayerId },
            authToken = session.authToken,
            onRoom = { code, seat, hostSeat, token, players, aiCount, requiredHumans ->
                if (voiceRoomCode != code) {
                    voiceRoomCode = code
                    voiceEventReducer.reset(
                        roomCode = code,
                        suppressNextSnapshot = !resumeCredential.isNullOrBlank()
                    )
                }
                roomCode = code
                mySeat = seat
                this@DoudizhuViewModel.resumeToken = token
                if (!token.isNullOrBlank()) saveResumeToken(code, cleanUrl, token)
                isHost = seat == hostSeat
                playerNames = players
                config = config.copy(humanCount = requiredHumans.coerceIn(1, 3)).normalized()
                connection = AppConnectionState.ONLINE
                val current = _uiState.value
                _uiState.value = if (current is GameUiState.Playing) {
                    current.copy(
                        config = config,
                        mySeat = seat,
                        roomCode = code,
                        players = players,
                        isHost = isHost,
                        connection = connection
                    )
                } else {
                    GameUiState.Lobby(
                        config = config,
                        roomCode = code,
                        yourSeat = seat,
                        players = players,
                        aiCount = aiCount,
                        requiredHumans = requiredHumans,
                        isHost = isHost
                    )
                }
            },
            onState = { state ->
                connection = AppConnectionState.ONLINE
                if (voiceEnabled) {
                    voiceEventReducer.reduce(roomCode, state, mySeat).forEach(_audioCues::tryEmit)
                } else {
                    voiceEventReducer.reduce(roomCode, state, mySeat)
                }
                if (state.phase.name == "FINISHED" && state.gameNumber != lastRecordedGame) {
                    lastRecordedGame = state.gameNumber
                    saveHistory(state)
                }
                _uiState.value = GameUiState.Playing(
                    config = config,
                    state = state,
                    mySeat = mySeat,
                    roomCode = roomCode,
                    players = playerNames,
                    isHost = isHost,
                    voiceEnabled = voiceEnabled
                )
            },
            onError = { message ->
                _toast.value = message
            },
            onConnection = { state ->
                if (state == ConnectionState.RECONNECTING ||
                    state == ConnectionState.CLOSED ||
                    state == ConnectionState.FAILED
                ) {
                    voiceEventReducer.suppressNextSnapshot()
                }
                connection = when (state) {
                    ConnectionState.CONNECTING -> AppConnectionState.CONNECTING
                    ConnectionState.OPEN -> AppConnectionState.ONLINE
                    ConnectionState.RECONNECTING -> AppConnectionState.RECONNECTING
                    ConnectionState.CLOSED, ConnectionState.FAILED -> AppConnectionState.RECONNECTING
                }
                val current = _uiState.value
                if (current is GameUiState.Lobby) {
                    _uiState.value = current.copy(connection = connection)
                } else if (current is GameUiState.Playing) {
                    _uiState.value = current.copy(connection = connection)
                } else if (current is GameUiState.Connecting && state == ConnectionState.FAILED) {
                    _uiState.value = current.copy(
                        message = "连接失败，请检查服务器地址后重试",
                        canRetry = true
                    )
                }
            }
        ).also { client ->
            client.connect {
                if (!resumeCredential.isNullOrBlank() && room != null) {
                    client.reconnectRoom(room, resumeCredential)
                } else if (room == null) {
                    client.createRoom(config.difficulty, config.aiCount)
                } else {
                    client.joinRoom(room)
                }
            }
        }
    }

    private fun leaveCurrentRoom() {
        val oldRoom = roomCode
        val oldUrl = lastServerUrl
        onlineClient?.leaveRoom()
        onlineClient?.close()
        onlineClient = null
        if (oldRoom.isNotBlank()) {
            preferences.edit().remove(resumeKey(oldRoom, oldUrl)).apply()
        }
        resumeToken = null
        roomCode = ""
        voiceRoomCode = ""
        voiceEventReducer.reset("")
        lastRequestedRoom = null
        connection = AppConnectionState.OFFLINE
    }

    private fun hasActiveRoom(): Boolean {
        return roomCode.isNotBlank() &&
            (_uiState.value is GameUiState.Connecting ||
                _uiState.value is GameUiState.Lobby ||
                _uiState.value is GameUiState.Playing)
    }

    private fun saveAuthSession(session: AuthSession) {
        authSession = session
        preferences.edit()
            .putString("authToken", session.authToken)
            .putString("accountId", session.accountId)
            .putString("nickname", session.nickname)
            .apply()
    }

    private fun loadAuthSession(): AuthSession? {
        val token = preferences.getString("authToken", null).orEmpty()
        val accountId = preferences.getString("accountId", null).orEmpty()
        val nickname = preferences.getString("nickname", null).orEmpty()
        return if (token.isBlank() || accountId.isBlank() || nickname.isBlank()) {
            null
        } else {
            AuthSession(token, accountId, nickname)
        }
    }

    private fun clearAuthSession() {
        authSession = null
        preferences.edit()
            .remove("authToken")
            .remove("accountId")
            .remove("nickname")
            .apply()
    }

    private fun saveHistory(state: EngineState) {
        val entry = JSONObject().apply {
            put("time", System.currentTimeMillis())
            put("roomCode", roomCode)
            put("winner", state.seats.getOrNull(state.winnerIndex ?: -1)?.name ?: "未知")
            put("multiplier", state.multiplier)
            put("scoreDelta", JSONArray().apply { state.scoreDelta.forEach(::put) })
            put("players", JSONArray().apply { playerNames.forEach(::put) })
        }
        val entries = JSONArray(preferences.getString("history", "[]"))
        val updated = JSONArray().apply {
            put(entry)
            for (index in 0 until minOf(entries.length(), 19)) put(entries.getJSONObject(index))
        }
        preferences.edit().putString("history", updated.toString()).apply()
    }

    private fun loadHistory(): List<HistoryEntry> {
        val entries = runCatching {
            JSONArray(preferences.getString("history", "[]"))
        }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                val deltas = item.optJSONArray("scoreDelta")
                val players = item.optJSONArray("players")
                add(
                    HistoryEntry(
                        time = item.optLong("time"),
                        roomCode = item.optString("roomCode"),
                        winner = item.optString("winner"),
                        multiplier = item.optInt("multiplier", 1),
                        scoreDelta = if (deltas == null) emptyList() else {
                            List(deltas.length()) { deltas.optInt(it) }
                        },
                        players = if (players == null) emptyList() else {
                            List(players.length()) { players.optString(it) }
                        }
                    )
                )
            }
        }
    }

    private fun normalizeServerBase(url: String): String {
        val trimmed = url.trim().trimEnd('/').removeSuffix("/ws")
        if (trimmed.isBlank()) return DEFAULT_SERVER_URL
        return when {
            trimmed.startsWith("http://", true) -> "ws://${trimmed.substringAfter("://")}"
            trimmed.startsWith("https://", true) -> "wss://${trimmed.substringAfter("://")}"
            trimmed.startsWith("ws://", true) || trimmed.startsWith("wss://", true) -> trimmed
            else -> "ws://$trimmed"
        }
    }

    private fun normalizeWebSocketUrl(url: String): String {
        val base = normalizeServerBase(url)
        return if (base.endsWith("/ws")) base else "$base/ws"
    }

    private fun httpBaseUrl(url: String): String {
        return normalizeServerBase(url)
            .replaceFirst(Regex("^wss://", RegexOption.IGNORE_CASE), "https://")
            .replaceFirst(Regex("^ws://", RegexOption.IGNORE_CASE), "http://")
            .removeSuffix("/ws")
    }

    private fun resumeKey(room: String, serverUrl: String): String {
        return "resumeToken:${serverUrl.trimEnd('/')}:$room"
    }

    private fun saveResumeToken(room: String, serverUrl: String, token: String) {
        preferences.edit().putString(resumeKey(room, serverUrl), token).apply()
    }

    private fun loadResumeToken(room: String, serverUrl: String): String? {
        if (room.isBlank()) return null
        return preferences.getString(resumeKey(room, serverUrl), null)
    }

    override fun onCleared() {
        onlineClient?.close()
        onlineClient = null
        super.onCleared()
    }
}
