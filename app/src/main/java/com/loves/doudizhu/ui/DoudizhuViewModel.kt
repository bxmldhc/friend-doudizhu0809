package com.loves.doudizhu.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.model.Card
import com.loves.doudizhu.model.GameConfig
import com.loves.doudizhu.network.ConnectionState
import com.loves.doudizhu.network.OnlineClient
import com.loves.doudizhu.network.RemoteAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

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

sealed interface GameUiState {
    data object Splash : GameUiState
    data class Home(
        val nickname: String = "",
        val serverUrl: String = "ws://127.0.0.1:18080",
        val connection: AppConnectionState = AppConnectionState.OFFLINE
    ) : GameUiState
    data class CreateRoom(
        val nickname: String,
        val serverUrl: String,
        val aiCount: Int = 2
    ) : GameUiState
    data class JoinRoom(
        val nickname: String,
        val serverUrl: String
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
        val connection: AppConnectionState = AppConnectionState.ONLINE
    ) : GameUiState
    data class History(val entries: List<HistoryEntry>) : GameUiState
}

class DoudizhuViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Splash)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val preferences = application.getSharedPreferences("friend_doudizhu", 0)
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
    private var lastNickname = ""
    private var lastServerUrl = "ws://127.0.0.1:18080"
    private var connection = AppConnectionState.OFFLINE

    fun showHome() {
        _uiState.value = GameUiState.Home(lastNickname, lastServerUrl, connection)
    }

    fun openCreateRoom() {
        val home = _uiState.value as? GameUiState.Home ?: return
        _uiState.value = GameUiState.CreateRoom(home.nickname, home.serverUrl, config.aiCount)
    }

    fun openJoinRoom() {
        val home = _uiState.value as? GameUiState.Home ?: return
        _uiState.value = GameUiState.JoinRoom(home.nickname, home.serverUrl)
    }

    fun openHistory() {
        _uiState.value = GameUiState.History(loadHistory())
    }

    fun backHome() {
        _uiState.value = GameUiState.Home(lastNickname, lastServerUrl, connection)
    }

    fun createRoom(nickname: String, serverUrl: String, aiCount: Int) {
        config = GameConfig(
            humanCount = 3 - aiCount.coerceIn(0, 2),
            difficulty = config.difficulty,
            mode = config.mode
        ).normalized()
        connect(nickname, serverUrl, host = true, room = null)
    }

    fun joinRoom(room: String, nickname: String, serverUrl: String) {
        val normalizedRoom = room.filter(Char::isDigit)
        if (normalizedRoom.length != 6) {
            _toast.value = "请输入六位数字房号"
            return
        }
        val normalizedUrl = normalizeUrl(serverUrl)
        connect(
            nickname = nickname,
            serverUrl = serverUrl,
            host = false,
            room = normalizedRoom,
            resumeCredential = loadResumeToken(normalizedRoom, normalizedUrl)
        )
    }

    fun startHostGame() {
        onlineClient?.startGame()
    }

    fun restart() {
        if (isHost) onlineClient?.restartGame()
    }

    fun retryConnection() {
        val current = _uiState.value
        if (current is GameUiState.Connecting || current is GameUiState.Lobby || current is GameUiState.Playing) {
            val savedToken = resumeToken ?: loadResumeToken(roomCode, lastServerUrl)
            val reconnectRoom = if (!savedToken.isNullOrBlank() && roomCode.isNotBlank()) roomCode else null
            connect(
                nickname = lastNickname,
                serverUrl = lastServerUrl,
                host = isHost,
                room = reconnectRoom ?: if (lastRoomWasCreate) null else (lastRequestedRoom ?: roomCode),
                resumeCredential = savedToken
            )
        }
    }

    fun exitToHome() {
        onlineClient?.close()
        onlineClient = null
        connection = AppConnectionState.OFFLINE
        _uiState.value = GameUiState.Home(lastNickname, lastServerUrl, connection)
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

    fun historyEntries(): List<HistoryEntry> = loadHistory()

    private fun connect(
        nickname: String,
        serverUrl: String,
        host: Boolean,
        room: String?,
        resumeCredential: String? = null
    ) {
        val cleanName = nickname.trim().ifBlank { "朋友" }
        val cleanUrl = normalizeUrl(serverUrl)
        lastNickname = cleanName
        lastServerUrl = cleanUrl.removeSuffix("/ws")
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
            onRoom = { code, seat, hostSeat, token, players, aiCount, requiredHumans ->
                roomCode = code
                mySeat = seat
                this@DoudizhuViewModel.resumeToken = token
                if (!token.isNullOrBlank()) saveResumeToken(code, cleanUrl, token)
                isHost = seat == hostSeat
                playerNames = players
                config = config.copy(
                    humanCount = requiredHumans.coerceIn(1, 3)
                ).normalized()
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
                    isHost = isHost
                )
            },
            onError = { message ->
                _toast.value = message
            },
            onConnection = { state ->
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
        val entries = JSONArray(preferences.getString("history", "[]"))
        return buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val deltas = item.optJSONArray("scoreDelta")
                val players = item.optJSONArray("players")
                add(
                    HistoryEntry(
                        time = item.optLong("time"),
                        roomCode = item.optString("roomCode"),
                        winner = item.optString("winner"),
                        multiplier = item.optInt("multiplier", 1),
                        scoreDelta = if (deltas == null) emptyList() else List(deltas.length()) { deltas.getInt(it) },
                        players = if (players == null) emptyList() else List(players.length()) { players.getString(it) }
                    )
                )
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
            .replaceFirst(Regex("^https://", RegexOption.IGNORE_CASE), "wss://")
            .replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "ws://")
        return if (trimmed.endsWith("/ws")) trimmed else "$trimmed/ws"
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
}
