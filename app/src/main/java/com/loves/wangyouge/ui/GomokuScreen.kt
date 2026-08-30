package com.loves.wangyouge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import com.loves.wangyouge.model.GOMOKU_BOARD_SIZE
import com.loves.wangyouge.model.GomokuPhase
import com.loves.wangyouge.model.GomokuSeat
import com.loves.wangyouge.model.GomokuState
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val GomokuPage = Color(0xFF091F27)
private val GomokuSurface = Color(0xFF12343B)
private val GomokuCoral = Color(0xFFE97D58)
private val GomokuWax = Color(0xFFF2D675)
private val GomokuMuted = Color(0xFFB4CED0)
private val GomokuBoard = Color(0xFFD6AC68)
private val GomokuGrid = Color(0xFF714D2A)
private val gomokuQuickMessages = listOf(
    "WAIT_A_MOMENT" to "稍等一下",
    "READY_TO_START" to "可以开始了",
    "PLAY_AGAIN" to "再来一局",
    "GOOD_GAME" to "下得漂亮"
)

@Composable
fun GomokuEntryScreen(
    state: GameUiState.GomokuEntry,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onCreateWithAi: (String) -> Unit,
    onJoin: (String, String) -> Unit
) {
    var serverUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var roomCode by remember(state.initialRoom) { mutableStateOf(state.initialRoom) }
    var withAi by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(GomokuPage).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.78f).widthIn(max = 760.dp),
            color = GomokuSurface,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                    Column {
                        Text("五子棋", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("双人联机对弈", color = GomokuMuted, fontSize = 13.sp)
                    }
                }
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务器地址") },
                    singleLine = true
                )
                Surface(
                    color = Color(0xFF102E36),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("加入 AI 对手", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (withAi) "创建后由 AI 自动占用白方" else "创建后等待朋友加入",
                                color = GomokuMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(checked = withAi, onCheckedChange = { withAi = it })
                    }
                }
                Button(
                    onClick = {
                        if (withAi) onCreateWithAi(serverUrl) else onCreate(serverUrl)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GomokuCoral)
                ) {
                    Text("创建房间", fontWeight = FontWeight.Bold)
                }
                Text("或加入朋友的房间", color = GomokuWax, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { roomCode = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.weight(1f),
                        label = { Text("六位房号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = { onJoin(roomCode, serverUrl) },
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GomokuCoral)
                    ) {
                        Text("加入")
                    }
                }
            }
        }
    }
}

@Composable
fun GomokuLobbyScreen(
    state: GameUiState.GomokuLobby,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onDisband: () -> Unit
) {
    var showExitConfirm by remember { mutableStateOf(false) }
    var showDisbandConfirm by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val online = state.connection == AppConnectionState.ONLINE
    val players = List(2) { state.players.getOrElse(it) { "等待加入" } }
    val hasAiOpponent = players.getOrNull(1) == "棋灵"

    BoxWithConstraints(Modifier.fillMaxSize().background(GomokuPage).padding(18.dp)) {
        val compact = maxHeight < 440.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)
        ) {
            GomokuTopBar(
                roomCode = state.roomCode,
                gameNumber = null,
                connection = state.connection,
                onExit = { showExitConfirm = true }
            )
            Surface(color = GomokuSurface, shape = RoundedCornerShape(8.dp)) {
                Column(
                    modifier = Modifier.padding(if (compact) 14.dp else 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (hasAiOpponent) "AI 练习" else "等待对手",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (hasAiOpponent) {
                            "黑方先行。AI 已准备，房主可直接开始对局。"
                        } else {
                            "黑方先行。两位玩家进入后，房主即可开始对局。"
                        },
                        color = GomokuMuted,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GomokuLobbySeat(players[0], "黑方", 0, Modifier.weight(1f))
                        GomokuLobbySeat(players[1], "白方", 1, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(state.roomCode))
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(6.dp))
                            Text("复制房号")
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString("忘忧阁五子棋房间：${state.roomCode}"))
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("分享")
                        }
                    }
                    if (state.isHost) {
                        Button(
                            onClick = onStart,
                            enabled = online && (hasAiOpponent || players.all { it != "等待加入" }),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GomokuCoral)
                        ) {
                            Text("开始对局", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("等待房主开始对局", color = GomokuWax, fontWeight = FontWeight.SemiBold)
                    }
                    if (!online) {
                        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("重新连接")
                        }
                    }
                    if (state.isHost) {
                        TextButton(onClick = { showDisbandConfirm = true }) {
                            Icon(Icons.Default.DeleteForever, null)
                            Spacer(Modifier.width(4.dp))
                            Text("解散房间")
                        }
                    }
                }
            }
        }
    }
    if (showExitConfirm) {
        GomokuConfirmDialog(
            title = "返回首页？",
            message = "返回后可在首页继续恢复这个房间。",
            confirm = onExit,
            onDismiss = { showExitConfirm = false }
        )
    }
    if (showDisbandConfirm) {
        GomokuConfirmDialog(
            title = "解散房间？",
            message = "房间会立即关闭，其他玩家也将返回首页。",
            confirm = onDisband,
            onDismiss = { showDisbandConfirm = false }
        )
    }
}

@Composable
fun GomokuGameScreen(
    state: GameUiState.GomokuPlaying,
    onMove: (Int, Int) -> Unit,
    onReady: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onDisband: () -> Unit,
    onQuickMessage: (String) -> Unit,
    quickMessage: QuickMessageUi?,
    playerProfile: PlayerProfileUiState,
    onPlayerProfile: (Int) -> Unit,
    onDismissPlayerProfile: () -> Unit
) {
    var showExitConfirm by remember { mutableStateOf(false) }
    var showDisbandConfirm by remember { mutableStateOf(false) }
    var showQuickMessages by remember { mutableStateOf(false) }
    val game = state.state
    val canMove = state.connection == AppConnectionState.ONLINE &&
        game.phase == GomokuPhase.PLAYING &&
        game.turnIndex == state.mySeat

    BoxWithConstraints(Modifier.fillMaxSize().background(GomokuPage).padding(8.dp)) {
        val compact = maxHeight < 480.dp || maxWidth < 820.dp
        val sidePanelWidth = if (compact) 84.dp else 96.dp
        val boardSize = minOf(
            maxHeight,
            maxWidth
        ).coerceAtLeast(0.dp)

        // 棋盘优先占满短边，玩家信息悬浮在两侧而不再挤占落子区域。
        GomokuBoardCanvas(
            board = game.board,
            winningLine = game.winningLine.map { it.row to it.col }.toSet(),
            enabled = canMove,
            onMove = onMove,
            modifier = Modifier
                .size(boardSize)
                .align(Alignment.Center)
        )
        GomokuPlayerPanel(
            seat = game.seats.getOrNull(0),
            fallback = state.players.getOrElse(0) { "黑方" },
            isCurrent = game.phase == GomokuPhase.PLAYING && game.turnIndex == 0,
            isReady = game.readySeats.getOrElse(0) { false },
            onClick = { onPlayerProfile(0) },
            modifier = Modifier
                .width(sidePanelWidth)
                .heightIn(max = if (compact) 150.dp else 180.dp)
                .padding(start = 2.dp)
                .align(Alignment.CenterStart)
        )
        GomokuPlayerPanel(
            seat = game.seats.getOrNull(1),
            fallback = state.players.getOrElse(1) { "白方" },
            isCurrent = game.phase == GomokuPhase.PLAYING && game.turnIndex == 1,
            isReady = game.readySeats.getOrElse(1) { false },
            onClick = { onPlayerProfile(1) },
            modifier = Modifier
                .width(sidePanelWidth)
                .heightIn(max = if (compact) 150.dp else 180.dp)
                .padding(end = 2.dp)
                .align(Alignment.CenterEnd)
        )
        GomokuTopBar(
            roomCode = state.roomCode,
            gameNumber = game.gameNumber,
            connection = state.connection,
            onExit = { showExitConfirm = true },
            onQuickMessage = { showQuickMessages = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        if (game.phase == GomokuPhase.PLAYING) {
            Surface(
                color = GomokuSurface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (canMove) "轮到你落子" else "等待对方落子",
                        color = if (canMove) GomokuWax else GomokuMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.connection != AppConnectionState.ONLINE) {
                        Spacer(Modifier.width(10.dp))
                        TextButton(onClick = onRetry) { Text("重新连接") }
                    }
                }
            }
        } else {
            GomokuFinishedBar(
                state = state,
                onReady = onReady,
                onRestart = onRestart,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        if (state.isHost) {
            TextButton(
                onClick = { showDisbandConfirm = true },
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text("解散房间", color = GomokuMuted, fontSize = 12.sp)
            }
        }
        quickMessage?.let {
            GomokuQuickMessageBubble(
                message = gomokuQuickMessageLabel(it.messageId),
                seat = it.seat,
                mySeat = state.mySeat
            )
        }
        if (showQuickMessages) {
            GomokuQuickMessageMenu(
                onSelect = {
                    showQuickMessages = false
                    onQuickMessage(it)
                },
                onDismiss = { showQuickMessages = false }
            )
        }
    }
    if (showExitConfirm) {
        GomokuConfirmDialog(
            title = "返回首页？",
            message = "返回后可从首页继续恢复这盘对局。",
            confirm = onExit,
            onDismiss = { showExitConfirm = false }
        )
    }
    if (showDisbandConfirm) {
        GomokuConfirmDialog(
            title = "解散房间？",
            message = "房间会立即关闭，其他玩家也将返回首页。",
            confirm = onDisband,
            onDismiss = { showDisbandConfirm = false }
        )
    }
    GomokuPlayerProfileDialog(playerProfile, onDismissPlayerProfile)
}

@Composable
private fun GomokuTopBar(
    roomCode: String,
    gameNumber: Int?,
    connection: AppConnectionState,
    onExit: () -> Unit,
    onQuickMessage: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回首页", tint = Color.White)
        }
        Text("房间 $roomCode", color = Color.White, fontWeight = FontWeight.Bold)
        gameNumber?.let {
            Spacer(Modifier.width(8.dp))
            Text("第 $it 局", color = GomokuMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        if (onQuickMessage != null) {
            IconButton(onClick = onQuickMessage) {
                Icon(Icons.Default.ChatBubbleOutline, "快捷消息", tint = GomokuMuted)
            }
        }
        Text(
            when (connection) {
                AppConnectionState.ONLINE -> "已同步"
                AppConnectionState.CONNECTING -> "连接中"
                AppConnectionState.RECONNECTING -> "重连中"
                AppConnectionState.OFFLINE -> "已断开"
            },
            color = if (connection == AppConnectionState.ONLINE) Color(0xFF7ED9A3) else GomokuWax,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun GomokuLobbySeat(name: String, label: String, index: Int, modifier: Modifier) {
    Surface(modifier = modifier, color = Color(0xFF102E36), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (index == 0) Color(0xFF1C1C1C) else Color(0xFFF4F0E8))
                    .border(1.dp, if (index == 0) Color(0xFF555555) else Color(0xFFB5AFA5), androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.height(8.dp))
            Text(label, color = GomokuWax, fontSize = 12.sp)
            Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun GomokuPlayerPanel(
    seat: GomokuSeat?,
    fallback: String,
    isCurrent: Boolean,
    isReady: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val isBlack = seat?.stone != "WHITE"
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (isCurrent) Color(0xFF16434A) else GomokuSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isBlack) Color(0xFF181818) else Color(0xFFF4F0E8))
                    .border(1.dp, if (isBlack) Color(0xFF606060) else Color(0xFFC5BEB3), androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.height(8.dp))
            Text(if (isBlack) "黑方" else "白方", color = GomokuWax, fontSize = 12.sp)
            Text(seat?.name?.ifBlank { fallback } ?: fallback, color = Color.White, textAlign = TextAlign.Center)
            if (seat?.isAi == true) {
                Spacer(Modifier.height(4.dp))
                Text("AI 对手", color = GomokuMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("场分 ${seat?.score ?: 0}", color = GomokuMuted, fontSize = 12.sp)
            if (!isCurrent && isReady) {
                Spacer(Modifier.height(5.dp))
                Text("已准备", color = Color(0xFF7ED9A3), fontSize = 11.sp)
            }
            if (seat?.isAway == true) {
                Spacer(Modifier.height(6.dp))
                Text("暂离", color = GomokuCoral, fontSize = 12.sp)
            }
            if (isCurrent) {
                Spacer(Modifier.height(8.dp))
                Text("正在落子", color = Color(0xFF7ED9A3), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GomokuBoardCanvas(
    board: List<List<Int>>,
    winningLine: Set<Pair<Int, Int>>,
    enabled: Boolean,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(GomokuBoard)
            .border(2.dp, GomokuGrid, RoundedCornerShape(6.dp))
            .padding(4.dp)
            .fillMaxSize()
            .pointerInput(enabled, board) {
                detectTapGestures { tap ->
                    if (!enabled) return@detectTapGestures
                    val spacing = minOf(size.width, size.height) / (GOMOKU_BOARD_SIZE - 1)
                    val side = spacing * (GOMOKU_BOARD_SIZE - 1)
                    val startX = (size.width - side) / 2f
                    val startY = (size.height - side) / 2f
                    val col = ((tap.x - startX) / spacing).roundToInt()
                    val row = ((tap.y - startY) / spacing).roundToInt()
                    val targetX = startX + col * spacing
                    val targetY = startY + row * spacing
                    val distance = sqrt((tap.x - targetX) * (tap.x - targetX) +
                        (tap.y - targetY) * (tap.y - targetY))
                    if (
                        row in 0 until GOMOKU_BOARD_SIZE &&
                        col in 0 until GOMOKU_BOARD_SIZE &&
                        distance <= spacing * 0.48f
                    ) {
                        onMove(row, col)
                    }
                }
            }
    ) {
        val spacing = minOf(size.width, size.height) / (GOMOKU_BOARD_SIZE - 1)
        val side = spacing * (GOMOKU_BOARD_SIZE - 1)
        val startX = (size.width - side) / 2f
        val startY = (size.height - side) / 2f
        repeat(GOMOKU_BOARD_SIZE) { index ->
            val offset = index * spacing
            drawLine(
                GomokuGrid,
                Offset(startX, startY + offset),
                Offset(startX + side, startY + offset),
                strokeWidth = 1.3f
            )
            drawLine(
                GomokuGrid,
                Offset(startX + offset, startY),
                Offset(startX + offset, startY + side),
                strokeWidth = 1.3f
            )
        }
        listOf(3 to 3, 3 to 11, 7 to 7, 11 to 3, 11 to 11).forEach { (row, col) ->
            drawCircle(GomokuGrid, radius = spacing * 0.12f, center = Offset(startX + col * spacing, startY + row * spacing))
        }
        board.forEachIndexed { row, line ->
            line.forEachIndexed { col, value ->
                if (value == 0) return@forEachIndexed
                val center = Offset(startX + col * spacing, startY + row * spacing)
                val color = if (value == 1) Color(0xFF171717) else Color(0xFFF8F4E9)
                drawCircle(color, radius = spacing * 0.41f, center = center)
                drawCircle(
                    if (value == 1) Color(0xFF5A5A5A) else Color(0xFFB7B0A5),
                    radius = spacing * 0.41f,
                    center = center,
                    style = Stroke(width = 1.2f)
                )
                if ((row to col) in winningLine) {
                    drawCircle(
                        GomokuCoral,
                        radius = spacing * 0.49f,
                        center = center,
                        style = Stroke(width = 2.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GomokuFinishedBar(
    state: GameUiState.GomokuPlaying,
    onReady: (Boolean) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val game = state.state
    val result = when (game.winnerIndex) {
        null -> "本局和棋"
        state.mySeat -> "本局获胜"
        else -> "本局失利"
    }
    val ready = game.readySeats.getOrElse(state.mySeat) { false }
    Surface(color = GomokuSurface, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(result, color = if (game.winnerIndex == state.mySeat) Color(0xFF7ED9A3) else GomokuWax, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(
                "${game.seats.getOrNull(0)?.name ?: "黑方"} ${game.seats.getOrNull(0)?.score ?: 0} : " +
                    "${game.seats.getOrNull(1)?.score ?: 0} ${game.seats.getOrNull(1)?.name ?: "白方"}",
                color = GomokuMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            if (game.seats.getOrNull(state.mySeat)?.isAi == true) {
                Text("AI 已准备", color = Color(0xFF7ED9A3), fontSize = 12.sp)
            } else {
                Button(
                    onClick = { onReady(!ready) },
                    colors = ButtonDefaults.buttonColors(containerColor = GomokuCoral)
                ) {
                    Text(if (ready) "取消准备" else "准备下一局")
                }
            }
            if (state.isHost) {
                Button(
                    onClick = onRestart,
                    enabled = game.readySeats.all { it },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GomokuCoral,
                        disabledContainerColor = GomokuSurface
                    )
                ) {
                    Text(if (game.readySeats.all { it }) "开始下一局" else "等待双方准备")
                }
            } else if (ready) {
                Text("已准备，等待对方", color = GomokuMuted, fontSize = 12.sp)
            } else {
                Text("准备后等待房主开始", color = GomokuMuted, fontSize = 12.sp)
            }
        }
    }
}

private fun gomokuQuickMessageLabel(id: String): String {
    return gomokuQuickMessages.firstOrNull { it.first == id }?.second ?: "快捷消息"
}

@Composable
private fun GomokuQuickMessageMenu(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopEnd
    ) {
        Surface(
            modifier = Modifier.padding(top = 48.dp, end = 10.dp),
            color = Color(0xFF12343B),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                gomokuQuickMessages.forEach { (id, label) ->
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { onSelect(id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GomokuQuickMessageBubble(message: String, seat: Int, mySeat: Int) {
    Box(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        contentAlignment = if (seat == mySeat) Alignment.BottomStart else Alignment.BottomEnd
    ) {
        Surface(color = GomokuSurface, shape = RoundedCornerShape(8.dp)) {
            Text(message, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun GomokuPlayerProfileDialog(
    state: PlayerProfileUiState,
    onDismiss: () -> Unit
) {
    val title = when (state) {
        PlayerProfileUiState.Hidden -> return
        is PlayerProfileUiState.Loading -> state.nickname
        is PlayerProfileUiState.Ready -> state.stats.nickname
        is PlayerProfileUiState.Unavailable -> state.nickname
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            when (state) {
                PlayerProfileUiState.Hidden -> Unit
                is PlayerProfileUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在读取历史战绩")
                }
                is PlayerProfileUiState.Unavailable -> Text(state.message)
                is PlayerProfileUiState.Ready -> {
                    val stats = state.stats.gameStats["GOMOKU"]
                    val games = stats?.games ?: 0
                    val wins = stats?.wins ?: 0
                    val winRate = if (games == 0) 0 else wins * 100 / games
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GomokuProfileStat("五子棋场分", (stats?.totalScore ?: 0).toString())
                        GomokuProfileStat("完成对局", games.toString())
                        GomokuProfileStat("获胜局数", wins.toString())
                        GomokuProfileStat("胜率", "$winRate%")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun GomokuProfileStat(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = GomokuMuted, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GomokuConfirmDialog(
    title: String,
    message: String,
    confirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = confirm, colors = ButtonDefaults.buttonColors(containerColor = GomokuCoral)) {
                Text("确认")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
