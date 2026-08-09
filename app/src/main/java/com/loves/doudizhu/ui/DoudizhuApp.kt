package com.loves.doudizhu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loves.doudizhu.ui.theme.DoudizhuTheme
import kotlinx.coroutines.delay

@Composable
fun DoudizhuApp(
    viewModel: DoudizhuViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        delay(900)
        viewModel.showHome()
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
                is GameUiState.Home -> HomeScreen(
                    state = state,
                    onCreate = viewModel::openCreateRoom,
                    onJoin = viewModel::openJoinRoom,
                    onHistory = viewModel::openHistory
                )
                is GameUiState.CreateRoom -> CreateRoomScreen(
                    initialNickname = state.nickname,
                    initialServerUrl = state.serverUrl,
                    initialAiCount = state.aiCount,
                    onBack = viewModel::backHome,
                    onCreate = viewModel::createRoom
                )
                is GameUiState.JoinRoom -> JoinRoomScreen(
                    initialNickname = state.nickname,
                    initialServerUrl = state.serverUrl,
                    onBack = viewModel::backHome,
                    onJoin = viewModel::joinRoom
                )
                is GameUiState.Connecting -> ConnectingScreen(
                    message = state.message,
                    canRetry = state.canRetry,
                    onRetry = viewModel::retryConnection,
                    onExit = viewModel::exitToHome
                )
                is GameUiState.Lobby -> LobbyScreen(
                    state = state,
                    onStart = viewModel::startHostGame,
                    onRetry = viewModel::retryConnection,
                    onExit = viewModel::exitToHome
                )
                is GameUiState.Playing -> GameScreen(
                    state = state,
                    onCall = viewModel::call,
                    onPlay = viewModel::play,
                    onPass = viewModel::pass,
                    onRestart = viewModel::restart,
                    onRetry = viewModel::retryConnection,
                    onExit = viewModel::exitToHome
                )
                is GameUiState.History -> HistoryScreen(
                    entries = state.entries,
                    onBack = viewModel::backHome
                )
            }
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
