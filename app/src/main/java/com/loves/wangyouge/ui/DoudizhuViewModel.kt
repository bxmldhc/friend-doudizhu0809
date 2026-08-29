package com.loves.wangyouge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loves.wangyouge.BuildConfig
import com.loves.wangyouge.audio.AudioCue
import com.loves.wangyouge.audio.VoiceEventReducer
import com.loves.wangyouge.logic.EngineState
import com.loves.wangyouge.model.AiDifficulty
import com.loves.wangyouge.model.Card
import com.loves.wangyouge.model.GameConfig
import com.loves.wangyouge.model.GomokuState
import com.loves.wangyouge.model.RemoteGameType
import com.loves.wangyouge.network.AccountStats
import com.loves.wangyouge.network.AdminAccount
import com.loves.wangyouge.network.AdminAuditRecord
import com.loves.wangyouge.network.AdminOverview
import com.loves.wangyouge.network.AdminRoom
import com.loves.wangyouge.network.ApiResult
import com.loves.wangyouge.network.AuthApi
import com.loves.wangyouge.network.AuthSession
import com.loves.wangyouge.network.ConnectionState
import com.loves.wangyouge.network.InviteLink
import com.loves.wangyouge.network.OnlineClient
import com.loves.wangyouge.network.PublicPlayerStats
import com.loves.wangyouge.network.RemoteAction
import com.loves.wangyouge.network.ServerError
import com.loves.wangyouge.network.AppUpdateInfo
import com.loves.wangyouge.network.UpdateApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.io.File

enum class AppConnectionState {
    OFFLINE,
    CONNECTING,
    ONLINE,
    RECONNECTING
}

data class QuickMessageUi(
    val seat: Int,
    val messageId: String,
    val at: Long
)

enum class ConnectingAction {
    RETRY,
    RETURN_TO_JOIN,
    RETURN_HOME
}

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
        val voiceEnabled: Boolean = true,
        val resumeRoomCode: String? = null,
        val resumeSavedAt: Long? = null,
        val isAdmin: Boolean = false
    ) : GameUiState

    data class CreateRoom(
        val nickname: String,
        val serverUrl: String,
        val aiCount: Int = 2,
        val difficulty: AiDifficulty = AiDifficulty.MEDIUM,
        val actionTimeoutSeconds: Int = 20
    ) : GameUiState

    data class JoinRoom(
        val nickname: String,
        val serverUrl: String,
        val initialRoom: String = ""
    ) : GameUiState

    data class Connecting(
        val title: String,
        val message: String,
        val errorCode: String? = null,
        val primaryLabel: String? = null,
        val primaryAction: ConnectingAction? = null,
        val secondaryLabel: String? = null,
        val secondaryAction: ConnectingAction? = null
    ) : GameUiState

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

    data class GomokuEntry(
        val nickname: String,
        val serverUrl: String,
        val initialRoom: String = ""
    ) : GameUiState

    data class GomokuLobby(
        val roomCode: String,
        val yourSeat: Int,
        val players: List<String>,
        val isHost: Boolean,
        val connection: AppConnectionState = AppConnectionState.ONLINE
    ) : GameUiState

    data class GomokuPlaying(
        val state: GomokuState,
        val mySeat: Int,
        val roomCode: String,
        val players: List<String>,
        val isHost: Boolean,
        val connection: AppConnectionState = AppConnectionState.ONLINE
    ) : GameUiState

    data class History(
        val stats: AccountStats? = null,
        val loading: Boolean = true,
        val error: String? = null
    ) : GameUiState

    data class Account(
        val accountId: String,
        val nickname: String,
        val isAdmin: Boolean,
        val mustChangePassword: Boolean = false,
        val loadingAction: String? = null,
        val error: String? = null
    ) : GameUiState

    data class Admin(
        val overview: AdminOverview? = null,
        val accounts: List<AdminAccount> = emptyList(),
        val rooms: List<AdminRoom> = emptyList(),
        val auditRecords: List<AdminAuditRecord> = emptyList(),
        val registrationEnabled: Boolean = true,
        val loading: Boolean = true,
        val error: String? = null,
        val query: String = "",
        val creatingAccount: Boolean = false,
        val resettingAccountId: String? = null,
        val updatingAccountId: String? = null,
        val updatingRegistration: Boolean = false
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

    private val _quickMessage = MutableStateFlow<QuickMessageUi?>(null)
    val quickMessage: StateFlow<QuickMessageUi?> = _quickMessage.asStateFlow()

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
    private var lastServerUrl = preferences.getString("serverUrl", DEFAULT_SERVER_URL)
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_SERVER_URL
    private var connection = AppConnectionState.OFFLINE
    private var voiceEnabled = preferences.getBoolean("voiceEnabled", true)
    private var updateChecked = false
    private var reconnectJob: Job? = null
    private var quickMessageJob: Job? = null
    private var reconnectAttempt = 0
    private var allowAutomaticReconnect = true
    private var authVerifiedThisProcess = false
    private val voiceEventReducer = VoiceEventReducer()
    private var voiceRoomCode = ""
    private var activeGameType = RemoteGameType.DOUDIZHU

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
                    authVerifiedThisProcess = true
                    continueAfterAuthentication()
                }
                is ApiResult.Failure -> {
                    if (result.code.equals("ACCOUNT_DISABLED", ignoreCase = true)) {
                        clearAuthSession()
                        showAuth("当前账号已被停用，请联系管理员")
                    } else if (result.isUnauthorized) {
                        clearAuthSession()
                        showAuth("登录已过期，请重新登录")
                    } else {
                        authVerifiedThisProcess = false
                        _toast.value = "暂时无法验证登录状态：${result.message}"
                        _uiState.value = homeState()
                    }
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
                    authVerifiedThisProcess = true
                    continueAfterAuthentication()
                }
                is ApiResult.Failure -> {
                    val message = when (result.code?.uppercase()) {
                        "ACCOUNT_DISABLED" -> "当前账号已被停用，请联系管理员"
                        "REGISTRATION_DISABLED" -> "管理员暂时关闭了新账号注册，请登录已有账号"
                        else -> result.message
                    }
                    if (result.code.equals("ACCOUNT_DISABLED", ignoreCase = true)) {
                        clearAuthSession()
                    }
                    _uiState.value = GameUiState.Auth(
                        serverUrl = cleanUrl,
                        pendingRoom = pendingInviteRoom,
                        error = message
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
        if (!requirePasswordChanged(session)) return
        val home = _uiState.value as? GameUiState.Home ?: return
        _uiState.value = GameUiState.CreateRoom(
            nickname = session.nickname,
            serverUrl = home.serverUrl,
            aiCount = config.aiCount,
            difficulty = config.difficulty,
            actionTimeoutSeconds = config.actionTimeoutSeconds
        )
    }

    fun openJoinRoom() {
        val session = authSession ?: return showAuth()
        if (!requirePasswordChanged(session)) return
        val home = _uiState.value as? GameUiState.Home ?: return
        _uiState.value = GameUiState.JoinRoom(
            nickname = session.nickname,
            serverUrl = home.serverUrl,
            initialRoom = pendingInviteRoom.orEmpty()
        )
    }

    fun openHistory() {
        _uiState.value = GameUiState.History()
        loadHistory()
    }

    fun openAccount() {
        val session = authSession ?: return showAuth()
        _uiState.value = GameUiState.Account(
            accountId = session.accountId,
            nickname = session.nickname,
            isAdmin = session.isAdmin,
            mustChangePassword = session.mustChangePassword,
            error = if (session.mustChangePassword) {
                "当前使用的是临时密码。请先修改密码，再创建、加入或恢复房间。"
            } else {
                null
            }
        )
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        val session = authSession ?: return showAuth()
        val current = _uiState.value as? GameUiState.Account ?: return
        if (currentPassword.length !in 6..64 || newPassword.length !in 6..64) {
            _uiState.value = current.copy(error = "密码需要 6 到 64 个字符")
            return
        }
        _uiState.value = current.copy(loadingAction = "password", error = null)
        viewModelScope.launch {
            when (
                val result = authApi.changePassword(
                    httpBaseUrl(lastServerUrl),
                    session.authToken,
                    currentPassword,
                    newPassword
                )
            ) {
                is ApiResult.Success -> {
                    saveAuthSession(result.data)
                    val state = _uiState.value as? GameUiState.Account ?: return@launch
                    _uiState.value = state.copy(
                        mustChangePassword = result.data.mustChangePassword,
                        loadingAction = null,
                        error = null
                    )
                    _toast.value = if (session.mustChangePassword) {
                        "密码已修改，现在可以进入房间"
                    } else {
                        "密码已修改，其他设备已退出"
                    }
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized && result.code != "CURRENT_PASSWORD_INVALID") {
                        handleAuthenticationExpired()
                    } else {
                        val state = _uiState.value as? GameUiState.Account ?: return@launch
                        _uiState.value = state.copy(loadingAction = null, error = result.message)
                    }
                }
            }
        }
    }

    fun logoutOtherSessions() {
        val session = authSession ?: return showAuth()
        val current = _uiState.value as? GameUiState.Account ?: return
        _uiState.value = current.copy(loadingAction = "sessions", error = null)
        viewModelScope.launch {
            when (
                val result = authApi.logoutOtherSessions(
                    httpBaseUrl(lastServerUrl),
                    session.authToken
                )
            ) {
                is ApiResult.Success -> {
                    saveAuthSession(result.data)
                    val state = _uiState.value as? GameUiState.Account ?: return@launch
                    _uiState.value = state.copy(loadingAction = null)
                    _toast.value = "其他设备已退出"
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized) {
                        handleAuthenticationExpired()
                    } else {
                        val state = _uiState.value as? GameUiState.Account ?: return@launch
                        _uiState.value = state.copy(loadingAction = null, error = result.message)
                    }
                }
            }
        }
    }

    fun openAdmin() {
        if (authSession?.isAdmin != true) {
            _toast.value = "当前账号没有管理权限"
            return
        }
        _uiState.value = GameUiState.Admin()
        loadAdmin()
    }

    fun retryAdmin() {
        val current = _uiState.value as? GameUiState.Admin ?: return
        _uiState.value = current.copy(loading = true, error = null)
        loadAdmin(current.query)
    }

    fun searchAdminAccounts(query: String) {
        val current = _uiState.value as? GameUiState.Admin ?: return
        _uiState.value = current.copy(query = query, loading = true, error = null)
        loadAdmin(query)
    }

    fun createAdminAccount(nickname: String, temporaryPassword: String) {
        val session = authSession ?: return showAuth()
        if (!session.isAdmin) return
        val current = _uiState.value as? GameUiState.Admin ?: return
        val cleanNickname = nickname.trim()
        when {
            cleanNickname.length !in 2..12 -> {
                _uiState.value = current.copy(error = "账号名需要 2 到 12 个字符")
                return
            }
            temporaryPassword.length !in 6..64 -> {
                _uiState.value = current.copy(error = "临时密码需要 6 到 64 个字符")
                return
            }
        }
        _uiState.value = current.copy(creatingAccount = true, error = null)
        viewModelScope.launch {
            when (
                val result = authApi.createAdminAccount(
                    httpBaseUrl(lastServerUrl),
                    session.authToken,
                    cleanNickname,
                    temporaryPassword
                )
            ) {
                is ApiResult.Success -> {
                    _toast.value = "已创建 ${result.data.nickname}，首次登录需要修改密码"
                    loadAdmin((_uiState.value as? GameUiState.Admin)?.query.orEmpty())
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized) {
                        handleAuthenticationExpired()
                    } else {
                        val state = _uiState.value as? GameUiState.Admin ?: return@launch
                        _uiState.value = state.copy(
                            creatingAccount = false,
                            error = when (result.code?.uppercase()) {
                                "NICKNAME_TAKEN" -> "该账号名已经被使用"
                                "INVALID_NICKNAME" -> "账号名需要 2 到 12 个有效字符"
                                "INVALID_PASSWORD" -> "临时密码需要 6 到 64 个字符"
                                else -> result.message
                            }
                        )
                    }
                }
            }
        }
    }

    fun resetAdminPassword(accountId: String, newPassword: String) {
        val session = authSession ?: return showAuth()
        if (!session.isAdmin) return
        val current = _uiState.value as? GameUiState.Admin ?: return
        if (newPassword.length !in 6..64) {
            _toast.value = "新密码需要 6 到 64 个字符"
            return
        }
        _uiState.value = current.copy(resettingAccountId = accountId)
        viewModelScope.launch {
            when (
                val result = authApi.resetAdminPassword(
                    httpBaseUrl(lastServerUrl),
                    session.authToken,
                    accountId,
                    newPassword
                )
            ) {
                is ApiResult.Success -> {
                    _toast.value = "已重置 ${result.data.nickname} 的密码"
                    loadAdmin((_uiState.value as? GameUiState.Admin)?.query.orEmpty())
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized) {
                        handleAuthenticationExpired()
                    } else {
                        val state = _uiState.value as? GameUiState.Admin ?: return@launch
                        _uiState.value = state.copy(
                            resettingAccountId = null,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun setAdminAccountDisabled(accountId: String, disabled: Boolean) {
        val session = authSession ?: return showAuth()
        if (!session.isAdmin) return
        val current = _uiState.value as? GameUiState.Admin ?: return
        _uiState.value = current.copy(
            updatingAccountId = accountId,
            error = null
        )
        viewModelScope.launch {
            when (
                val result = authApi.setAdminAccountDisabled(
                    httpBaseUrl(lastServerUrl),
                    session.authToken,
                    accountId,
                    disabled
                )
            ) {
                is ApiResult.Success -> {
                    _toast.value = if (result.data.disabled) {
                        "已禁用 ${result.data.nickname}"
                    } else {
                        "已恢复 ${result.data.nickname}"
                    }
                    loadAdmin((_uiState.value as? GameUiState.Admin)?.query.orEmpty())
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized) {
                        handleAuthenticationExpired()
                    } else {
                        val state = _uiState.value as? GameUiState.Admin ?: return@launch
                        _uiState.value = state.copy(
                            updatingAccountId = null,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun setRegistrationEnabled(enabled: Boolean) {
        val session = authSession ?: return showAuth()
        if (!session.isAdmin) return
        val current = _uiState.value as? GameUiState.Admin ?: return
        _uiState.value = current.copy(updatingRegistration = true, error = null)
        viewModelScope.launch {
            when (
                val result = authApi.setAdminRegistration(
                    httpBaseUrl(lastServerUrl),
                    session.authToken,
                    enabled
                )
            ) {
                is ApiResult.Success -> {
                    val state = _uiState.value as? GameUiState.Admin ?: return@launch
                    _uiState.value = state.copy(
                        registrationEnabled = result.data.enabled,
                        updatingRegistration = false
                    )
                    _toast.value = if (result.data.enabled) "新账号注册已开放" else "新账号注册已关闭"
                    loadAdmin(state.query)
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized) {
                        handleAuthenticationExpired()
                    } else {
                        val state = _uiState.value as? GameUiState.Admin ?: return@launch
                        _uiState.value = state.copy(
                            updatingRegistration = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    private fun loadAdmin(query: String = "") {
        val session = authSession ?: return showAuth()
        if (!session.isAdmin) return backHome()
        viewModelScope.launch {
            val overview = authApi.adminOverview(httpBaseUrl(lastServerUrl), session.authToken)
            val accounts = authApi.adminAccounts(
                httpBaseUrl(lastServerUrl),
                session.authToken,
                query
            )
            val rooms = authApi.adminRooms(httpBaseUrl(lastServerUrl), session.authToken)
            val registration = authApi.adminRegistration(
                httpBaseUrl(lastServerUrl),
                session.authToken
            )
            val audit = authApi.adminAudit(httpBaseUrl(lastServerUrl), session.authToken)
            val failure = listOf(overview, accounts, rooms, registration, audit)
                .filterIsInstance<ApiResult.Failure>()
                .firstOrNull()
            if (failure != null) {
                if (failure.isUnauthorized) {
                    handleAuthenticationExpired()
                } else {
                    val current = _uiState.value as? GameUiState.Admin ?: return@launch
                    _uiState.value = current.copy(
                        loading = false,
                        creatingAccount = false,
                        resettingAccountId = null,
                        updatingAccountId = null,
                        updatingRegistration = false,
                        error = failure.message
                    )
                }
                return@launch
            }
            val current = _uiState.value as? GameUiState.Admin ?: return@launch
            _uiState.value = current.copy(
                overview = (overview as ApiResult.Success).data,
                accounts = (accounts as ApiResult.Success).data.accounts,
                rooms = (rooms as ApiResult.Success).data.rooms,
                registrationEnabled = (registration as ApiResult.Success).data.enabled,
                auditRecords = (audit as ApiResult.Success).data.records,
                loading = false,
                creatingAccount = false,
                resettingAccountId = null,
                updatingAccountId = null,
                updatingRegistration = false,
                error = null,
                query = query
            )
        }
    }

    fun retryHistory() {
        val current = _uiState.value as? GameUiState.History ?: return
        _uiState.value = current.copy(loading = true, error = null)
        loadHistory()
    }

    private fun loadHistory() {
        val session = authSession ?: return showAuth()
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

    fun createRoom(
        serverUrl: String,
        aiCount: Int,
        difficulty: AiDifficulty,
        actionTimeoutSeconds: Int
    ) {
        val session = authSession ?: return showAuth()
        config = GameConfig(
            humanCount = 3 - aiCount.coerceIn(0, 2),
            difficulty = difficulty,
            mode = config.mode,
            actionTimeoutSeconds = actionTimeoutSeconds
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

    fun openGomoku() {
        val session = authSession ?: return showAuth()
        if (!requirePasswordChanged(session)) return
        _uiState.value = GameUiState.GomokuEntry(
            nickname = session.nickname,
            serverUrl = lastServerUrl
        )
    }

    fun createGomokuRoom(serverUrl: String) {
        val session = authSession ?: return showAuth()
        if (!requirePasswordChanged(session)) return
        config = GameConfig(
            humanCount = 2,
            difficulty = AiDifficulty.MEDIUM,
            actionTimeoutSeconds = 20
        ).normalized()
        connect(
            nickname = session.nickname,
            serverUrl = serverUrl,
            host = true,
            room = null,
            gameType = RemoteGameType.GOMOKU
        )
    }

    fun joinGomokuRoom(room: String, serverUrl: String) {
        val session = authSession ?: return showAuth()
        if (!requirePasswordChanged(session)) return
        val normalizedRoom = room.filter(Char::isDigit)
        if (normalizedRoom.length != 6) {
            _toast.value = "请输入六位数字房号"
            return
        }
        connect(
            nickname = session.nickname,
            serverUrl = serverUrl,
            host = false,
            room = normalizedRoom,
            gameType = RemoteGameType.GOMOKU
        )
    }

    fun playGomoku(row: Int, col: Int) {
        val current = _uiState.value as? GameUiState.GomokuPlaying ?: return
        if (current.connection != AppConnectionState.ONLINE) {
            _toast.value = "正在重连，暂时不能落子"
            return
        }
        if (current.state.phase != com.loves.wangyouge.model.GomokuPhase.PLAYING) return
        if (current.state.turnIndex != current.mySeat) {
            _toast.value = "还没轮到你"
            return
        }
        if (current.state.board.getOrNull(row)?.getOrNull(col) != 0) {
            _toast.value = "这里已经有棋子了"
            return
        }
        onlineClient?.sendGomokuMove(row, col)
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

    fun disbandRoom() {
        if (!isHost) {
            _toast.value = "只有房主可以解散房间"
            return
        }
        onlineClient?.disbandRoom()
    }

    fun sendQuickMessage(messageId: String) {
        onlineClient?.sendQuickMessage(messageId)
    }

    fun retryConnection() {
        reconnectJob?.cancel()
        reconnectAttempt = 0
        allowAutomaticReconnect = true
        val current = _uiState.value
        if (
            current is GameUiState.Connecting ||
            current is GameUiState.Lobby ||
            current is GameUiState.Playing ||
            current is GameUiState.GomokuLobby ||
            current is GameUiState.GomokuPlaying
        ) {
            val savedToken = resumeToken ?: loadResumeToken(roomCode, lastServerUrl)
            val reconnectRoom = if (!savedToken.isNullOrBlank() && roomCode.isNotBlank()) roomCode else null
            connect(
                nickname = authSession?.nickname.orEmpty(),
                serverUrl = lastServerUrl,
                host = isHost,
                room = reconnectRoom ?: if (lastRoomWasCreate) null else (lastRequestedRoom ?: roomCode),
                resumeCredential = savedToken,
                gameType = activeGameType
            )
        }
    }

    fun handleConnectingAction(action: ConnectingAction) {
        when (action) {
            ConnectingAction.RETRY -> retryConnection()
            ConnectingAction.RETURN_TO_JOIN -> returnToJoinRoom()
            ConnectingAction.RETURN_HOME -> exitToHome()
        }
    }

    fun exitToHome() {
        disconnectPreservingResume()
        _uiState.value = homeState()
    }

    fun resumeSavedRoom() {
        val record = loadResumeRecord() ?: run {
            _toast.value = "没有可恢复的牌局"
            _uiState.value = homeState()
            return
        }
        val session = authSession ?: return showAuth()
        if (record.accountId != session.accountId) {
            clearResumeRecord()
            _toast.value = "恢复信息不属于当前账号"
            _uiState.value = homeState()
            return
        }
        roomCode = record.roomCode
        resumeToken = record.resumeToken
        lastServerUrl = record.serverUrl
        connect(
            nickname = session.nickname,
            serverUrl = record.serverUrl,
            host = false,
            room = record.roomCode,
            resumeCredential = record.resumeToken,
            gameType = record.gameType
        )
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
        val session = authSession
        if (session?.mustChangePassword == true) {
            openAccount()
            return
        }
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
        val resumeRecord = session?.let { loadResumeRecordFor(it.accountId) }
        return if (session == null) {
            GameUiState.Home("", lastServerUrl, connection, voiceEnabled)
        } else {
            GameUiState.Home(
                nickname = session.nickname,
                serverUrl = lastServerUrl,
                connection = connection,
                voiceEnabled = voiceEnabled,
                resumeRoomCode = resumeRecord?.roomCode,
                resumeSavedAt = resumeRecord?.savedAt?.takeIf { it > 0L },
                isAdmin = authVerifiedThisProcess && session.isAdmin
            )
        }
    }

    private fun requirePasswordChanged(session: AuthSession): Boolean {
        if (!session.mustChangePassword) return true
        _toast.value = "请先在账号页修改临时密码"
        openAccount()
        return false
    }

    private fun connect(
        nickname: String,
        serverUrl: String,
        host: Boolean,
        room: String?,
        resumeCredential: String? = null,
        gameType: RemoteGameType = RemoteGameType.DOUDIZHU
    ) {
        val session = authSession ?: return showAuth()
        if (!requirePasswordChanged(session)) return
        reconnectJob?.cancel()
        allowAutomaticReconnect = true
        val cleanName = session.nickname.ifBlank { nickname.trim().ifBlank { "朋友" } }
        val cleanUrl = normalizeWebSocketUrl(serverUrl)
        lastServerUrl = cleanUrl.removeSuffix("/ws")
        preferences.edit().putString("serverUrl", lastServerUrl).apply()
        isHost = host
        lastRoomWasCreate = room == null
        lastRequestedRoom = room
        activeGameType = gameType
        connection = AppConnectionState.CONNECTING
        _uiState.value = GameUiState.Connecting(
            title = if (room == null) {
                if (gameType == RemoteGameType.GOMOKU) "正在创建五子棋房间" else "正在创建房间"
            } else {
                if (gameType == RemoteGameType.GOMOKU) "正在加入五子棋房间" else "正在加入房间"
            },
            message = "正在与服务器同步，请稍候"
        )
        onlineClient?.close()
        var nextClient: OnlineClient? = null
        nextClient = OnlineClient(
            serverUrl = cleanUrl,
            name = cleanName,
            playerId = session.accountId.ifBlank { legacyPlayerId },
            authToken = session.authToken,
            onRoom = {
                    code,
                    seat,
                    hostSeat,
                    token,
                    players,
                    aiCount,
                    requiredHumans,
                    difficulty,
                    actionTimeoutSeconds,
                    roomGameType ->
                if (onlineClient === nextClient) {
                    activeGameType = roomGameType
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
                    if (!token.isNullOrBlank()) {
                        saveResumeToken(code, lastServerUrl, token)
                        saveResumeRecord(
                            ResumeRecord(
                                roomCode = code,
                                resumeToken = token,
                                serverUrl = lastServerUrl,
                                accountId = session.accountId,
                                savedAt = System.currentTimeMillis(),
                                gameType = roomGameType
                            )
                        )
                    }
                    isHost = seat == hostSeat
                    playerNames = players
                    config = config.copy(
                        humanCount = requiredHumans.coerceIn(1, 3),
                        difficulty = difficulty,
                        actionTimeoutSeconds = actionTimeoutSeconds
                    ).normalized()
                    connection = AppConnectionState.ONLINE
                    reconnectAttempt = 0
                    val current = _uiState.value
                    _uiState.value = if (roomGameType == RemoteGameType.GOMOKU) {
                        when (current) {
                            is GameUiState.GomokuPlaying -> current.copy(
                                mySeat = seat,
                                roomCode = code,
                                players = players,
                                isHost = isHost,
                                connection = connection
                            )
                            else -> GameUiState.GomokuLobby(
                                roomCode = code,
                                yourSeat = seat,
                                players = players,
                                isHost = isHost,
                                connection = connection
                            )
                        }
                    } else if (current is GameUiState.Playing) {
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
                }
            },
            onState = { state ->
                if (onlineClient === nextClient && activeGameType == RemoteGameType.DOUDIZHU) {
                    connection = AppConnectionState.ONLINE
                    reconnectAttempt = 0
                    if (voiceEnabled) {
                        voiceEventReducer.reduce(roomCode, state, mySeat)
                            .forEach(_audioCues::tryEmit)
                    } else {
                        voiceEventReducer.reduce(roomCode, state, mySeat)
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
                }
            },
            onGomokuState = { state ->
                if (onlineClient === nextClient && activeGameType == RemoteGameType.GOMOKU) {
                    connection = AppConnectionState.ONLINE
                    reconnectAttempt = 0
                    _uiState.value = GameUiState.GomokuPlaying(
                        state = state,
                        mySeat = mySeat,
                        roomCode = roomCode,
                        players = playerNames,
                        isHost = isHost,
                        connection = connection
                    )
                }
            },
            onRoomDisbanded = { reason ->
                if (onlineClient === nextClient) {
                    clearRoomAfterDisband()
                    _toast.value = reason.ifBlank { "房主已解散房间" }
                    _uiState.value = homeState()
                }
            },
            onQuickMessage = { seat, messageId, at ->
                if (onlineClient === nextClient) {
                    _quickMessage.value = QuickMessageUi(seat, messageId, at)
                    quickMessageJob?.cancel()
                    quickMessageJob = viewModelScope.launch {
                        delay(3_000)
                        val currentMessage = _quickMessage.value
                        if (currentMessage?.seat == seat && currentMessage.at == at) {
                            _quickMessage.value = null
                        }
                    }
                }
            },
            onServerError = { error ->
                if (onlineClient === nextClient) handleServerError(error)
            },
            onConnection = { state ->
                if (onlineClient === nextClient) {
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
                        ConnectionState.CLOSED, ConnectionState.FAILED ->
                            AppConnectionState.RECONNECTING
                    }
                    val current = _uiState.value
                    if (current is GameUiState.Lobby) {
                        _uiState.value = current.copy(connection = connection)
                    } else if (current is GameUiState.Playing) {
                        _uiState.value = current.copy(connection = connection)
                    } else if (current is GameUiState.GomokuLobby) {
                        _uiState.value = current.copy(connection = connection)
                    } else if (current is GameUiState.GomokuPlaying) {
                        _uiState.value = current.copy(connection = connection)
                    } else if (current is GameUiState.Connecting &&
                        state == ConnectionState.FAILED
                    ) {
                        _uiState.value = current.copy(
                            title = "暂时无法连接",
                            message = "请检查网络或服务器地址后重新连接",
                            errorCode = "CONNECTION_FAILED",
                            primaryLabel = "重新连接",
                            primaryAction = ConnectingAction.RETRY,
                            secondaryLabel = "返回首页",
                            secondaryAction = ConnectingAction.RETURN_HOME
                        )
                    }
                    if (
                        allowAutomaticReconnect &&
                        (state == ConnectionState.CLOSED ||
                            state == ConnectionState.FAILED ||
                            state == ConnectionState.RECONNECTING)
                    ) {
                        scheduleAutomaticReconnect()
                    }
                }
            }
        )
        onlineClient = nextClient
        nextClient.connect {
            if (onlineClient === nextClient) {
                if (!resumeCredential.isNullOrBlank() && room != null) {
                    nextClient.reconnectRoom(room, resumeCredential)
                } else if (room == null) {
                    nextClient.createRoom(
                        config.difficulty,
                        config.aiCount,
                        config.actionTimeoutSeconds,
                        gameType
                    )
                } else {
                    nextClient.joinRoom(room, gameType)
                }
            }
        }
    }

    private fun leaveCurrentRoom() {
        reconnectJob?.cancel()
        allowAutomaticReconnect = false
        val oldRoom = roomCode
        val oldUrl = lastServerUrl
        onlineClient?.leaveRoom()
        onlineClient?.close()
        onlineClient = null
        if (oldRoom.isNotBlank()) {
            preferences.edit().remove(resumeKey(oldRoom, oldUrl)).apply()
        }
        clearResumeRecord()
        resumeToken = null
        roomCode = ""
        voiceRoomCode = ""
        voiceEventReducer.reset("")
        lastRequestedRoom = null
        activeGameType = RemoteGameType.DOUDIZHU
        connection = AppConnectionState.OFFLINE
    }

    private fun clearRoomAfterDisband() {
        reconnectJob?.cancel()
        quickMessageJob?.cancel()
        allowAutomaticReconnect = false
        val oldRoom = roomCode
        val oldUrl = lastServerUrl
        onlineClient?.close()
        onlineClient = null
        if (oldRoom.isNotBlank()) {
            preferences.edit().remove(resumeKey(oldRoom, oldUrl)).apply()
        }
        clearResumeRecord()
        resumeToken = null
        roomCode = ""
        voiceRoomCode = ""
        voiceEventReducer.reset("")
        lastRequestedRoom = null
        activeGameType = RemoteGameType.DOUDIZHU
        _quickMessage.value = null
        connection = AppConnectionState.OFFLINE
    }

    private fun disconnectPreservingResume() {
        reconnectJob?.cancel()
        allowAutomaticReconnect = false
        onlineClient?.close()
        onlineClient = null
        connection = AppConnectionState.OFFLINE
        voiceEventReducer.suppressNextSnapshot()
    }

    private fun scheduleAutomaticReconnect() {
        if (reconnectJob?.isActive == true || reconnectAttempt >= RECONNECT_DELAYS_MS.size) return
        val token = resumeToken ?: loadResumeRecord()?.resumeToken ?: return
        val targetRoom = roomCode.ifBlank { loadResumeRecord()?.roomCode.orEmpty() }
        if (targetRoom.isBlank()) return
        val delayMs = RECONNECT_DELAYS_MS[reconnectAttempt++]
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (!allowAutomaticReconnect) return@launch
            connect(
                nickname = authSession?.nickname.orEmpty(),
                serverUrl = lastServerUrl,
                host = isHost,
                room = targetRoom,
                resumeCredential = token,
                gameType = activeGameType
            )
        }
    }

    private fun handleServerError(error: ServerError) {
        when (error.code?.uppercase()) {
            "AUTH_REQUIRED", "UNAUTHORIZED", "INVALID_TOKEN" -> handleAuthenticationExpired()
            "PASSWORD_CHANGE_REQUIRED" -> {
                stopConnection()
                authSession?.let { session ->
                    saveAuthSession(session.copy(mustChangePassword = true))
                }
                openAccount()
                _toast.value = error.message.ifBlank { "请先修改临时密码" }
            }
            "ACCOUNT_DISABLED" -> {
                stopConnection()
                clearAuthSession()
                showAuth("当前账号已被停用，请联系管理员")
            }
            "REGISTRATION_DISABLED" -> {
                stopConnection()
                showAuth("管理员暂时关闭了新账号注册，请登录已有账号")
            }
            "ROOM_NOT_FOUND" -> {
                clearResumeRecord()
                resumeToken = null
                roomCode = ""
                stopConnection()
                showConnectingIssue(
                    title = "没有找到这个房间",
                    message = "房间可能已经结束，请确认房号后重新输入",
                    code = "ROOM_NOT_FOUND",
                    primaryLabel = "重新输入房号",
                    primaryAction = ConnectingAction.RETURN_TO_JOIN
                )
            }
            "RESUME_DENIED" -> {
                clearResumeRecord()
                resumeToken = null
                roomCode = ""
                stopConnection()
                showConnectingIssue(
                    title = "无法恢复上次牌局",
                    message = "原座位已经失效，可以返回首页重新加入房间",
                    code = "RESUME_DENIED",
                    primaryLabel = "返回首页",
                    primaryAction = ConnectingAction.RETURN_HOME,
                    includeSecondaryHome = false
                )
            }
            "ROOM_FULL" -> {
                stopConnection()
                showConnectingIssue(
                    title = "房间人数已满",
                    message = "这个房间暂时没有空位，可以检查房号或稍后再试",
                    code = "ROOM_FULL",
                    primaryLabel = "重新输入房号",
                    primaryAction = ConnectingAction.RETURN_TO_JOIN
                )
            }
            "GAME_ALREADY_STARTED" -> {
                stopConnection()
                showConnectingIssue(
                    title = "牌局已经开始",
                    message = "当前不能以新玩家身份加入，可以检查房号或等待下一场",
                    code = "GAME_ALREADY_STARTED",
                    primaryLabel = "重新输入房号",
                    primaryAction = ConnectingAction.RETURN_TO_JOIN
                )
            }
            "CONNECTION_FAILED" -> Unit
            else -> _toast.value = error.message
        }
    }

    private fun showConnectingIssue(
        title: String,
        message: String,
        code: String,
        primaryLabel: String,
        primaryAction: ConnectingAction,
        includeSecondaryHome: Boolean = true
    ) {
        _uiState.value = GameUiState.Connecting(
            title = title,
            message = message,
            errorCode = code,
            primaryLabel = primaryLabel,
            primaryAction = primaryAction,
            secondaryLabel = if (includeSecondaryHome) "返回首页" else null,
            secondaryAction = if (includeSecondaryHome) ConnectingAction.RETURN_HOME else null
        )
    }

    private fun returnToJoinRoom() {
        val session = authSession ?: return showAuth()
        val room = lastRequestedRoom.orEmpty()
        stopConnection()
        roomCode = ""
        resumeToken = null
        _uiState.value = GameUiState.JoinRoom(
            nickname = session.nickname,
            serverUrl = lastServerUrl,
            initialRoom = room
        )
    }

    private fun stopConnection() {
        reconnectJob?.cancel()
        allowAutomaticReconnect = false
        onlineClient?.close()
        onlineClient = null
        connection = AppConnectionState.OFFLINE
    }

    private fun handleAuthenticationExpired() {
        reconnectJob?.cancel()
        allowAutomaticReconnect = false
        onlineClient?.close()
        onlineClient = null
        clearAuthSession()
        showAuth("登录已过期，请重新登录")
    }

    private fun hasActiveRoom(): Boolean {
        return roomCode.isNotBlank() &&
            (_uiState.value is GameUiState.Connecting ||
                _uiState.value is GameUiState.Lobby ||
                _uiState.value is GameUiState.Playing ||
                _uiState.value is GameUiState.GomokuLobby ||
                _uiState.value is GameUiState.GomokuPlaying)
    }

    private fun saveAuthSession(session: AuthSession) {
        authSession = session
        preferences.edit()
            .putString("authToken", session.authToken)
            .putString("accountId", session.accountId)
            .putString("nickname", session.nickname)
            .putString("accountRole", session.role.name)
            .putBoolean("isAdmin", session.isAdmin)
            .putBoolean("mustChangePassword", session.mustChangePassword)
            .apply()
    }

    private fun loadAuthSession(): AuthSession? {
        val token = preferences.getString("authToken", null).orEmpty()
        val accountId = preferences.getString("accountId", null).orEmpty()
        val nickname = preferences.getString("nickname", null).orEmpty()
        return if (token.isBlank() || accountId.isBlank() || nickname.isBlank()) {
            null
        } else {
            AuthSession(
                authToken = token,
                accountId = accountId,
                nickname = nickname,
                role = com.loves.wangyouge.network.AccountRole.from(
                    preferences.getString("accountRole", null)
                ),
                isAdmin = preferences.getBoolean("isAdmin", false),
                mustChangePassword = preferences.getBoolean("mustChangePassword", false)
            )
        }
    }

    private fun clearAuthSession() {
        authVerifiedThisProcess = false
        authSession = null
        preferences.edit()
            .remove("authToken")
            .remove("accountId")
            .remove("nickname")
            .remove("accountRole")
            .remove("isAdmin")
            .remove("mustChangePassword")
            .apply()
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

    private fun saveResumeRecord(record: ResumeRecord) {
        preferences.edit()
            .putString("resumeRoomCode", record.roomCode)
            .putString("resumeCredential", record.resumeToken)
            .putString("resumeServerUrl", record.serverUrl)
            .putString("resumeAccountId", record.accountId)
            .putLong("resumeSavedAt", record.savedAt)
            .putString("resumeGameType", record.gameType.name)
            .apply()
    }

    private fun loadResumeRecord(): ResumeRecord? {
        val room = preferences.getString("resumeRoomCode", null).orEmpty()
        val token = preferences.getString("resumeCredential", null).orEmpty()
        val server = preferences.getString("resumeServerUrl", null).orEmpty()
        val account = preferences.getString("resumeAccountId", null).orEmpty()
        if (room.length != 6 || token.isBlank() || server.isBlank() || account.isBlank()) return null
        return ResumeRecord(
            roomCode = room,
            resumeToken = token,
            serverUrl = server,
            accountId = account,
            savedAt = preferences.getLong("resumeSavedAt", 0L),
            gameType = RemoteGameType.fromWire(
                preferences.getString("resumeGameType", RemoteGameType.DOUDIZHU.name)
            )
        )
    }

    private fun loadResumeRecordFor(accountId: String): ResumeRecord? {
        return loadResumeRecord()?.takeIf { it.accountId == accountId }
    }

    private fun clearResumeRecord() {
        val record = loadResumeRecord()
        val editor = preferences.edit()
            .remove("resumeRoomCode")
            .remove("resumeCredential")
            .remove("resumeServerUrl")
            .remove("resumeAccountId")
            .remove("resumeSavedAt")
            .remove("resumeGameType")
        if (record != null) {
            editor.remove(resumeKey(record.roomCode, record.serverUrl))
            editor.remove(resumeKey(record.roomCode, normalizeWebSocketUrl(record.serverUrl)))
        }
        editor.apply()
    }

    override fun onCleared() {
        reconnectJob?.cancel()
        onlineClient?.close()
        onlineClient = null
        super.onCleared()
    }
}

private data class ResumeRecord(
    val roomCode: String,
    val resumeToken: String,
    val serverUrl: String,
    val accountId: String,
    val savedAt: Long,
    val gameType: RemoteGameType = RemoteGameType.DOUDIZHU
)

private val RECONNECT_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L)
