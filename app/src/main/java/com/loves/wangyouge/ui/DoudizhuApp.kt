package com.loves.wangyouge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loves.wangyouge.audio.GameVoicePlayer
import com.loves.wangyouge.update.UpdateInstaller
import com.loves.wangyouge.ui.theme.DoudizhuTheme
import kotlinx.coroutines.delay

@Composable
fun DoudizhuApp(
    viewModel: DoudizhuViewModel = viewModel(),
    initialInviteUri: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val inviteConfirmation by viewModel.inviteConfirmation.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val playerProfile by viewModel.playerProfile.collectAsState()
    val quickMessage by viewModel.quickMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val voicePlayer = remember { GameVoicePlayer(context) }

    DisposableEffect(voicePlayer) {
        onDispose { voicePlayer.release() }
    }

    LaunchedEffect(viewModel) {
        viewModel.audioCues.collect(voicePlayer::play)
    }

    val voiceEnabled = when (val state = uiState) {
        is GameUiState.Home -> state.voiceEnabled
        is GameUiState.Playing -> state.voiceEnabled
        else -> true
    }
    LaunchedEffect(voiceEnabled) {
        if (!voiceEnabled) {
            voicePlayer.stop()
        }
    }

    LaunchedEffect(initialInviteUri) {
        viewModel.handleInvite(initialInviteUri)
        delay(900)
        viewModel.initialize()
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    DoudizhuTheme {
        Box(Modifier.fillMaxSize()) {
            when (val state = uiState) {
                GameUiState.Splash -> SplashScreen()
                is GameUiState.Auth -> AuthScreen(
                    state = state,
                    onSubmit = viewModel::submitAuth
                )
                is GameUiState.Home -> HomeScreen(
                    state = state,
                    onCreate = viewModel::openCreateRoom,
                    onJoin = viewModel::openJoinRoom,
                    onCreateGomoku = { viewModel.createGomokuRoom(state.serverUrl) },
                    onJoinGomoku = { room -> viewModel.joinGomokuRoom(room, state.serverUrl) },
                    onHistory = viewModel::openHistory,
                    onResume = viewModel::resumeSavedRoom,
                    onAdmin = viewModel::openAdmin,
                    onAccount = viewModel::openAccount,
                    onVoiceEnabledChange = viewModel::setVoiceEnabled,
                    onCheckUpdate = { viewModel.checkForUpdates() }
                )
                is GameUiState.CreateRoom -> CreateRoomScreen(
                    nickname = state.nickname,
                    initialServerUrl = state.serverUrl,
                    initialAiCount = state.aiCount,
                    initialDifficulty = state.difficulty,
                    initialActionTimeoutSeconds = state.actionTimeoutSeconds,
                    onBack = viewModel::backHome,
                    onCreate = viewModel::createRoom
                )
                is GameUiState.JoinRoom -> JoinRoomScreen(
                    nickname = state.nickname,
                    initialServerUrl = state.serverUrl,
                    initialRoom = state.initialRoom,
                    onBack = viewModel::backHome,
                    onJoin = viewModel::joinRoom
                )
                is GameUiState.Connecting -> ConnectingScreen(
                    title = state.title,
                    message = state.message,
                    primaryLabel = state.primaryLabel,
                    onPrimary = state.primaryAction?.let { action ->
                        { viewModel.handleConnectingAction(action) }
                    },
                    secondaryLabel = state.secondaryLabel,
                    onSecondary = state.secondaryAction?.let { action ->
                        { viewModel.handleConnectingAction(action) }
                    }
                )
                is GameUiState.Lobby -> LobbyScreen(
                    state = state,
                    onStart = viewModel::startHostGame,
                    onRetry = viewModel::retryConnection,
                    onExit = viewModel::exitToHome,
                    onDisband = viewModel::disbandRoom
                )
                is GameUiState.Playing -> GameScreen(
                    state = state,
                    playerProfile = playerProfile,
                    onCall = viewModel::call,
                    onPlay = viewModel::play,
                    onPass = viewModel::pass,
                    onReady = viewModel::setReady,
                    onRestart = viewModel::restart,
                    onRetry = viewModel::retryConnection,
                    onExit = viewModel::exitToHome,
                    onDisband = viewModel::disbandRoom,
                    onQuickMessage = viewModel::sendQuickMessage,
                    onVoiceEnabledChange = viewModel::setVoiceEnabled,
                    onPlayerProfile = viewModel::openPlayerProfile,
                    onDismissPlayerProfile = viewModel::dismissPlayerProfile,
                    quickMessage = quickMessage
                )
                is GameUiState.GomokuEntry -> GomokuEntryScreen(
                    state = state,
                    onBack = viewModel::backHome,
                    onCreate = viewModel::createGomokuRoom,
                    onCreateWithAi = { serverUrl ->
                        viewModel.createGomokuRoom(serverUrl, withAi = true)
                    },
                    onJoin = viewModel::joinGomokuRoom
                )
                is GameUiState.GomokuLobby -> GomokuLobbyScreen(
                    state = state,
                    onStart = viewModel::startHostGame,
                    onRetry = viewModel::retryConnection,
                    onExit = viewModel::exitToHome,
                    onDisband = viewModel::disbandRoom
                )
                is GameUiState.GomokuPlaying -> GomokuGameScreen(
                    state = state,
                    onMove = viewModel::playGomoku,
                    onReady = viewModel::setReady,
                    onRestart = viewModel::restart,
                    onRetry = viewModel::retryConnection,
                    onExit = viewModel::exitToHome,
                    onDisband = viewModel::disbandRoom
                )
                is GameUiState.History -> HistoryScreen(
                    stats = state.stats,
                    loading = state.loading,
                    error = state.error,
                    onBack = viewModel::backHome,
                    onRetry = viewModel::retryHistory
                )
                is GameUiState.Account -> AccountScreen(
                    state = state,
                    onBack = viewModel::backHome,
                    onChangePassword = viewModel::changePassword,
                    onLogoutOtherSessions = viewModel::logoutOtherSessions,
                    onLogout = viewModel::logout
                )
                is GameUiState.Admin -> AdminScreen(
                    state = state,
                    onBack = viewModel::backHome,
                    onRetry = viewModel::retryAdmin,
                    onOnlineAccounts = state.onlineAccounts,
                    onSearch = viewModel::searchAdminAccounts,
                    onCreateAccount = viewModel::createAdminAccount,
                    onResetPassword = viewModel::resetAdminPassword,
                    onSetAccountDisabled = viewModel::setAdminAccountDisabled,
                    onSetRegistrationEnabled = viewModel::setRegistrationEnabled
                )
            }
            SnackbarHost(hostState = snackbarHostState)
            inviteConfirmation?.let { room ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissInvite,
                    title = { Text("加入新房间？") },
                    text = { Text("你正在当前房间中。确认退出并加入房间 $room 吗？") },
                    confirmButton = {
                        Button(onClick = viewModel::confirmInvite) {
                            Text("退出并加入")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissInvite) {
                            Text("取消")
                        }
                    }
                )
            }
            if (uiState is GameUiState.Home) {
                UpdateDialog(
                    state = updateState,
                    onDownload = viewModel::downloadUpdate,
                    onInstall = { apk ->
                        when (val result = UpdateInstaller.install(context, apk)) {
                            UpdateInstaller.InstallResult.Started -> Unit
                            UpdateInstaller.InstallResult.PermissionRequired -> {
                                viewModel.showMessage("请允许安装未知应用，然后返回并再次点击安装")
                            }
                            is UpdateInstaller.InstallResult.Failed -> {
                                viewModel.showMessage(result.message)
                            }
                        }
                    },
                    onDismiss = viewModel::dismissUpdate
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    state: UpdateUiState,
    onDownload: (com.loves.wangyouge.network.AppUpdateInfo) -> Unit,
    onInstall: (java.io.File) -> Unit,
    onDismiss: () -> Unit
) {
    val info = when (state) {
        is UpdateUiState.Available -> state.info
        is UpdateUiState.Downloading -> state.info
        is UpdateUiState.Ready -> state.info
        else -> null
    } ?: return
    val downloading = state is UpdateUiState.Downloading

    AlertDialog(
        onDismissRequest = {
            if (!info.forceUpdate && !downloading) onDismiss()
        },
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column {
                if (info.releaseNotes.isEmpty()) {
                    Text("新版本已经可以下载。")
                } else {
                    info.releaseNotes.forEach { note -> Text("• $note") }
                }
                if (state is UpdateUiState.Downloading) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("正在下载 ${state.progress}%")
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUiState.Available -> {
                    Button(onClick = { onDownload(state.info) }) { Text("立即更新") }
                }
                is UpdateUiState.Downloading -> {
                    Button(onClick = {}, enabled = false) { Text("下载中") }
                }
                is UpdateUiState.Ready -> {
                    Button(onClick = { onInstall(state.apk) }) { Text("安装更新") }
                }
                else -> Unit
            }
        },
        dismissButton = if (!info.forceUpdate && !downloading) {
            {
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        } else {
            null
        }
    )
}
