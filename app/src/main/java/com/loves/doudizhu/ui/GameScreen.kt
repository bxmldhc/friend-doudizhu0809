package com.loves.doudizhu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loves.doudizhu.R
import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.logic.Phase
import com.loves.doudizhu.logic.SeatAction
import com.loves.doudizhu.logic.SeatState
import com.loves.doudizhu.logic.RoomLeaderboardEntry
import com.loves.doudizhu.model.Card
import com.loves.doudizhu.model.Rank
import com.loves.doudizhu.model.Suit
import kotlinx.coroutines.delay

private val PageBg = Color(0xFF091F27)
private val Felt = Color(0xFF1A6259)
private val Coral = Color(0xFFE97D58)
private val Wax = Color(0xFFF2D675)
private val Muted = Color(0xFFB4CED0)
private val Panel = Color(0xCC12343B)

@Composable
fun GameScreen(
    state: GameUiState.Playing,
    onCall: (Boolean) -> Unit,
    onPlay: (List<Card>) -> Unit,
    onPass: () -> Unit,
    onReady: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onDisband: () -> Unit,
    onQuickMessage: (String) -> Unit,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onPlayerProfile: (Int) -> Unit,
    onDismissPlayerProfile: () -> Unit,
    playerProfile: PlayerProfileUiState,
    quickMessage: QuickMessageUi?
) {
    val game = state.state
    val mySeat = game.seats.getOrNull(state.mySeat)
    val isMyTurn = game.phase != Phase.FINISHED && game.turnIndex == state.mySeat
    val canBid = isMyTurn && game.phase == Phase.BIDDING
    val canPlay = isMyTurn && game.phase == Phase.PLAYING
    val requiredPlayersPresent = state.players
        .take(state.config.humanCount)
        .none { it == "等待加入" }
        && game.seats.take(state.config.humanCount).none { it.isAway }
    var selected by remember(game.gameNumber, game.turnIndex, game.phase) { mutableStateOf(emptySet<Card>()) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showScoreboard by remember { mutableStateOf(false) }
    var showRoundScore by remember(game.gameNumber, game.phase) {
        mutableStateOf(game.phase == Phase.FINISHED)
    }
    var showQuickMessages by remember { mutableStateOf(false) }
    var showDisbandConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(game.gameNumber, game.turnIndex, game.phase) {
        if (game.phase != Phase.FINISHED) selected = emptySet()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(PageBg)) {
        val compactHeight = maxHeight < 500.dp
        val pageHorizontalPadding = if (compactHeight) 10.dp else 16.dp
        val pageVerticalPadding = if (compactHeight) 4.dp else 10.dp
        val topBarHeight = if (compactHeight) 46.dp else 54.dp
        val handAreaHeight = when {
            game.phase == Phase.FINISHED && compactHeight -> 104.dp
            game.phase == Phase.FINISHED -> 122.dp
            compactHeight -> 96.dp
            else -> 112.dp
        }
        val selfPanelWidth = if (compactHeight) 112.dp else 132.dp

        Image(
            painter = painterResource(R.drawable.table_table_felt),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = pageHorizontalPadding, vertical = pageVerticalPadding)
        ) {
            GameTopBar(
                state = state,
                game = game,
                onRetry = onRetry,
                onScoreboard = { showScoreboard = !showScoreboard },
                onQuickMessages = { showQuickMessages = !showQuickMessages },
                canDisband = state.isHost && game.phase == Phase.FINISHED,
                onDisband = { showDisbandConfirm = true },
                onVoiceEnabledChange = onVoiceEnabledChange,
                onExit = { showExitConfirm = true },
                height = topBarHeight
            )
            TriangleTable(
                game = game,
                mySeat = state.mySeat,
                myTurn = isMyTurn,
                canBid = canBid,
                onCall = onCall,
                onPlayerProfile = onPlayerProfile,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            if (mySeat != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(handAreaHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OpponentPanel(
                        seat = mySeat,
                        active = isMyTurn,
                        onClick = { onPlayerProfile(mySeat.index) },
                        modifier = Modifier.width(selfPanelWidth)
                    )
                    if (game.phase == Phase.FINISHED) {
                        FinishedHandRows(
                            cards = mySeat.hand,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        HandRow(
                            cards = mySeat.hand,
                            selected = selected,
                            enabled = canPlay,
                            modifier = Modifier.weight(1f),
                            onToggle = { card ->
                                selected = if (card in selected) selected - card else selected + card
                            }
                        )
                    }
                }
            }
            Controls(
                game = game,
                state = state,
                canBid = canBid,
                canPlay = canPlay,
                selected = selected,
                onCall = onCall,
                onPlay = { onPlay(selected.sorted()) },
                onPass = onPass,
                isHost = state.isHost,
                canRestart = state.isHost && requiredPlayersPresent && game.readySeats.all { it },
                isReady = game.readySeats.getOrNull(state.mySeat) == true,
                onReady = onReady,
                onRestart = onRestart,
                onDisband = { showDisbandConfirm = true }
            )
        }
        if (state.connection == AppConnectionState.RECONNECTING) {
            ReconnectingOverlay(onRetry)
        }
        if (showRoundScore && game.phase == Phase.FINISHED) {
            RoundScorePanel(
                game = game,
                onDismiss = { showRoundScore = false }
            )
        }
        if (showScoreboard) {
            ScoreboardPanel(
                entries = game.leaderboard.ifEmpty {
                    game.seats.map { seat ->
                        RoomLeaderboardEntry(
                            playerId = "",
                            name = seat.name,
                            score = seat.score,
                            isAi = !seat.isHuman,
                            seatIndex = seat.index,
                            isAway = seat.isAway,
                            isActive = true
                        )
                    }.sortedByDescending { it.score }
                },
                onDismiss = { showScoreboard = false }
            )
        }
        if (showQuickMessages) {
            QuickMessageMenu(
                onSelect = {
                    showQuickMessages = false
                    onQuickMessage(it)
                },
                onDismiss = { showQuickMessages = false }
            )
        }
        quickMessage?.let {
            QuickMessageBubble(
                message = quickMessageLabel(it.messageId),
                seat = it.seat,
                mySeat = state.mySeat
            )
        }
        if (showDisbandConfirm) {
            AlertDialog(
                onDismissRequest = { showDisbandConfirm = false },
                title = { Text("解散这个房间？") },
                text = { Text("房间、场内分数和本场记录将被清除，其他玩家会返回首页。") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDisbandConfirm = false
                            onDisband()
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Coral
                        )
                    ) {
                        Text("确认解散")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDisbandConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }
        if (showExitConfirm) {
            AlertDialog(
                onDismissRequest = { showExitConfirm = false },
                title = { Text("退出牌局？") },
                text = {
                    Text(
                        if (game.phase == Phase.FINISHED) {
                            "退出后将离开当前房间并释放座位，之后可以重新输入房号加入。"
                        } else {
                            "退出后当前座位会由服务器 AI 托管。"
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitConfirm = false
                            onExit()
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral)
                    ) {
                        Text("确认退出")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showExitConfirm = false }) {
                        Text("继续游戏")
                    }
                }
            )
        }
        if (playerProfile !is PlayerProfileUiState.Hidden) {
            PlayerProfileDialog(
                state = playerProfile,
                onDismiss = onDismissPlayerProfile
            )
        }
    }
}

@Composable
private fun GameTopBar(
    state: GameUiState.Playing,
    game: EngineState,
    onRetry: () -> Unit,
    onScoreboard: () -> Unit,
    onQuickMessages: () -> Unit,
    canDisband: Boolean,
    onDisband: () -> Unit,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onExit: () -> Unit,
    height: Dp
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(height)) {
        val compact = maxWidth < 820.dp
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出牌局", tint = Color.White)
            }
            Column {
                if (!compact) {
                    Text("好友斗地主", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    text = if (compact) {
                        "${state.roomCode} · ${game.gameNumber}局"
                    } else {
                        "房号 ${state.roomCode} · 第 ${game.gameNumber} 局"
                    },
                    color = Muted,
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (game.phase == Phase.PLAYING && game.bottomCards.isNotEmpty()) {
            BottomCards(
                cards = game.bottomCards,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (compact) "×${game.multiplier}" else "倍数 ×${game.multiplier}",
                color = Wax,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 12.sp else 13.sp
            )
            IconButton(
                onClick = onQuickMessages,
                modifier = Modifier.size(if (compact) 40.dp else 44.dp)
            ) {
                Icon(Icons.Default.ChatBubble, "快捷消息", tint = Color.White)
            }
            IconButton(
                onClick = { onVoiceEnabledChange(!state.voiceEnabled) },
                modifier = Modifier.size(if (compact) 40.dp else 44.dp)
            ) {
                Icon(
                    if (state.voiceEnabled) Icons.AutoMirrored.Filled.VolumeUp
                    else Icons.AutoMirrored.Filled.VolumeOff,
                    if (state.voiceEnabled) "关闭语音" else "开启语音",
                    tint = if (state.voiceEnabled) Wax else Muted
                )
            }
            IconButton(
                onClick = onScoreboard,
                modifier = Modifier.size(if (compact) 40.dp else 44.dp)
            ) {
                Icon(Icons.Default.Leaderboard, "查看场内排行榜", tint = Color.White)
            }
            if (canDisband && !compact) {
                IconButton(onClick = onDisband, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.DeleteForever, "解散房间", tint = Color(0xFFF28B78))
                }
            }
            if (!compact || state.connection != AppConnectionState.ONLINE) {
                Text(
                    text = when (state.connection) {
                        AppConnectionState.ONLINE -> "已同步"
                        AppConnectionState.CONNECTING -> "连接中"
                        AppConnectionState.RECONNECTING -> "重连中"
                        AppConnectionState.OFFLINE -> "离线"
                    },
                    color = if (state.connection == AppConnectionState.ONLINE) Color(0xFF7ED9A3) else Wax,
                    fontSize = 11.sp
                )
            }
            if (state.connection == AppConnectionState.RECONNECTING) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(if (compact) 40.dp else 44.dp)
                ) {
                    Icon(Icons.Default.Refresh, "重新连接", tint = Color.White)
                }
            }
        }
    }
}

private val quickMessages = listOf(
    "WAIT_A_MOMENT" to "稍等一下",
    "READY_TO_START" to "可以开始了",
    "PLAY_AGAIN" to "再来一局",
    "NETWORK_SLOW" to "网络有点卡",
    "BE_RIGHT_BACK" to "我马上回来"
)

private fun quickMessageLabel(id: String): String {
    return quickMessages.firstOrNull { it.first == id }?.second ?: "快捷消息"
}

@Composable
private fun QuickMessageMenu(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopEnd
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 58.dp, end = 92.dp)
                .width(180.dp)
                .clickable(onClick = {}),
            color = Color(0xF212333B),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                quickMessages.forEach { (id, label) ->
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickMessageBubble(message: String, seat: Int, mySeat: Int) {
    val alignment = when (seat) {
        mySeat -> Alignment.BottomStart
        nextSeat(mySeat) -> Alignment.TopEnd
        else -> Alignment.TopStart
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (alignment == Alignment.TopStart) 150.dp else 16.dp,
                end = if (alignment == Alignment.TopEnd) 150.dp else 16.dp,
                top = 72.dp,
                bottom = 142.dp
            ),
        color = Color.Transparent
    ) {
        Box(contentAlignment = alignment) {
            Surface(
                color = Color(0xF2F2D675),
                contentColor = PageBg,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScoreboardPanel(
    entries: List<RoomLeaderboardEntry>,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopEnd
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 58.dp, end = 18.dp)
                .width(250.dp)
                .clickable(onClick = {}),
            color = Color(0xF212333B),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("场内排行榜", color = Wax, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Text("等待服务器下发分数", color = Muted, fontSize = 12.sp)
                } else {
                    entries.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                color = if (index == 0) Wax else Muted,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    buildString {
                                        append(entry.name)
                                        if (entry.isAway) append("（暂离）")
                                        if (entry.isAi) append(" · AI")
                                    },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                Text(
                                    if (entry.isActive) "场内累计" else "已离开，记录保留",
                                    color = Muted,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                "${if (entry.score >= 0) "+" else ""}${entry.score}",
                                color = if (entry.score >= 0) Color(0xFF7ED9A3) else Color(0xFFF28B78),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpponentPanel(
    seat: SeatState,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Panel,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Wax else Color.White.copy(alpha = .12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                Box(
                    modifier = Modifier.size(30.dp).background(
                    if (seat.isAway) Color(0xFF51666A)
                    else if (seat.isHuman) Color(0xFF2E8D7C)
                    else Color(0xFF51666A),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(seat.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${seat.name}${if (seat.isAway) "（暂离）" else ""}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = when {
                        seat.isAway -> "暂离 · AI托管 · ${seat.handCount} 张"
                        seat.isHuman -> "真人 · ${seat.handCount} 张"
                        else -> "服务器 AI · ${seat.handCount} 张"
                    },
                    color = Muted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            if (seat.isLandlord) {
                Image(
                    painter = painterResource(R.drawable.brand_landlord_badge),
                    contentDescription = "地主",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun TriangleTable(
    game: EngineState,
    mySeat: Int,
    myTurn: Boolean,
    canBid: Boolean,
    onCall: (Boolean) -> Unit,
    onPlayerProfile: (Int) -> Unit,
    modifier: Modifier
) {
    val rightOpponent = game.seats.getOrNull(nextSeat(mySeat))
    val leftOpponent = game.seats.getOrNull(nextSeat(nextSeat(mySeat)))
    val selfSeat = game.seats.getOrNull(mySeat)

    BoxWithConstraints(modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        val playWidth = (maxWidth * 0.25f).coerceIn(154.dp, 220.dp)
        val seatWidth = (maxWidth * 0.22f).coerceIn(128.dp, 184.dp)
        val compactHeight = maxHeight < 220.dp
        val playHeight = if (compactHeight) 70.dp else 88.dp
        Box(Modifier.fillMaxSize()) {
            if (leftOpponent != null) {
                TablePlayer(
                    seat = leftOpponent,
                    play = game.lastPlaysBySeat.getOrNull(leftOpponent.index).orEmpty(),
                    action = game.lastActionsBySeat.getOrNull(leftOpponent.index),
                    active = game.turnIndex == leftOpponent.index && game.phase != Phase.FINISHED,
                    width = seatWidth,
                    playWidth = playWidth,
                    playHeight = playHeight,
                    onPlayerProfile = onPlayerProfile,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            if (rightOpponent != null) {
                TablePlayer(
                    seat = rightOpponent,
                    play = game.lastPlaysBySeat.getOrNull(rightOpponent.index).orEmpty(),
                    action = game.lastActionsBySeat.getOrNull(rightOpponent.index),
                    active = game.turnIndex == rightOpponent.index && game.phase != Phase.FINISHED,
                    width = seatWidth,
                    playWidth = playWidth,
                    playHeight = playHeight,
                    onPlayerProfile = onPlayerProfile,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
            if (selfSeat != null) {
                PlayPool(
                    seat = selfSeat,
                    play = game.lastPlaysBySeat.getOrNull(selfSeat.index).orEmpty(),
                    action = game.lastActionsBySeat.getOrNull(selfSeat.index),
                    active = game.turnIndex == selfSeat.index && game.phase != Phase.FINISHED,
                    width = playWidth,
                    height = playHeight,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-2).dp)
                )
            }
            TableCenterStatus(
                game = game,
                myTurn = myTurn,
                canBid = canBid,
                onCall = onCall,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = if (compactHeight) (-8).dp else (-18).dp)
            )
        }
    }
}

@Composable
private fun TablePlayer(
    seat: SeatState,
    play: List<Card>,
    action: SeatAction?,
    active: Boolean,
    width: Dp,
    playWidth: Dp,
    playHeight: Dp,
    onPlayerProfile: (Int) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OpponentPanel(
            seat = seat,
            active = active,
            onClick = { onPlayerProfile(seat.index) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        PlayPool(
            seat = seat,
            play = play,
            action = action,
            active = active,
            width = playWidth,
            height = playHeight
        )
    }
}

@Composable
private fun PlayPool(
    seat: SeatState,
    play: List<Card>,
    action: SeatAction?,
    active: Boolean,
    width: Dp,
    height: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(width).height(height),
        color = if (action == null) Color(0x55204E4A) else Color(0xAA123E42),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (active) 2.dp else 1.dp,
            color = if (active) Wax else Color.White.copy(alpha = .16f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${seat.name} · 出牌区",
                    color = if (active) Wax else Color.White.copy(alpha = .86f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(3.dp))
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (action) {
                    SeatAction.PLAY -> {
                        if (play.isEmpty()) {
                            Text("已出牌", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        } else {
                            val cardWidth = minOf(36.dp, maxWidth)
                            val step = if (play.size == 1) {
                                0.dp
                            } else {
                                ((maxWidth - cardWidth) / (play.size - 1))
                                    .coerceAtLeast(4.dp)
                                    .coerceAtMost(cardWidth * .62f)
                            }
                            CardRow(
                                cards = play,
                                width = cardWidth,
                                height = minOf(52.dp, maxHeight - 2.dp),
                                step = step
                            )
                        }
                    }
                    SeatAction.PASS -> SeatActionLabel("不出")
                    SeatAction.CALL -> SeatActionLabel("叫地主", Wax)
                    SeatAction.NOT_CALL -> SeatActionLabel("不叫")
                    SeatAction.ROB -> SeatActionLabel("抢地主", Wax)
                    SeatAction.NOT_ROB -> SeatActionLabel("不抢")
                    null -> Text(
                        "本轮未操作",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SeatActionLabel(text: String, color: Color = Color.White) {
    Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun TableCenterStatus(
    game: EngineState,
    myTurn: Boolean,
    canBid: Boolean,
    onCall: (Boolean) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (game.phase == Phase.BIDDING) {
            CardBackPile()
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (myTurn) {
                    if (game.bidCandidateIndex == null) "轮到你叫地主" else "轮到你抢地主"
                } else {
                    "${game.currentSeat.name} 正在操作"
                },
                color = if (myTurn) Wax else Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            ActionCountdown(deadlineAt = game.actionDeadlineAt, visible = true)
            Text(
                text = if (myTurn) "操作由服务器判定" else "等待服务器同步下一步",
                color = Muted,
                fontSize = 11.sp
            )
        } else if (game.phase == Phase.PLAYING) {
            Text(
                text = if (myTurn) "当前轮到你" else "等待 ${game.currentSeat.name} 操作",
                color = if (myTurn) Wax else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(game.message, color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
            ActionCountdown(deadlineAt = game.actionDeadlineAt, visible = true)
        }
    }
}

@Composable
private fun ActionCountdown(deadlineAt: Long?, visible: Boolean) {
    if (!visible || deadlineAt == null) return
    var now by remember(deadlineAt) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadlineAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= deadlineAt) break
            delay(250)
        }
    }
    val seconds = ((deadlineAt - now + 999L) / 1000L).coerceAtLeast(0L)
    Text(
        text = "操作剩余 ${seconds} 秒",
        color = if (seconds <= 5) Coral else Wax,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun BottomCards(cards: List<Card>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0x883F5050),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("底牌", color = Wax, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
            CardRow(
                cards = cards,
                width = 28.dp,
                height = 39.dp,
                step = 17.dp
            )
        }
    }
}

@Composable
private fun PlayerProfileDialog(
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
                is PlayerProfileUiState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("正在读取历史战绩")
                }
                is PlayerProfileUiState.Unavailable -> Text(state.message)
                is PlayerProfileUiState.Ready -> {
                    val games = state.stats.games
                    val winRate = if (games == 0) 0 else state.stats.wins * 100 / games
                    val landlordRate = profileWinRate(
                        state.stats.landlordWins,
                        state.stats.landlordGames
                    )
                    val farmerRate = profileWinRate(
                        state.stats.farmerWins,
                        state.stats.farmerGames
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileStatRow("历史总得分", state.stats.totalScore.toString())
                        ProfileStatRow("完成小局", "${state.stats.games}")
                        ProfileStatRow("总胜率", "$winRate%")
                        ProfileStatRow("地主胜率", "$landlordRate%")
                        ProfileStatRow("农民胜率", "$farmerRate%")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private fun profileWinRate(wins: Int, games: Int): Int {
    return if (games <= 0) 0 else wins * 100 / games
}

@Composable
private fun ProfileStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Controls(
    game: EngineState,
    state: GameUiState.Playing,
    canBid: Boolean,
    canPlay: Boolean,
    selected: Set<Card>,
    onCall: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onPass: () -> Unit,
    isHost: Boolean,
    canRestart: Boolean,
    isReady: Boolean,
    onReady: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onDisband: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        val compact = maxWidth < 820.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                game.phase == Phase.FINISHED -> {
                    Button(
                        onClick = { onReady(!isReady) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = game.seats.getOrNull(state.mySeat)?.isHuman == true,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral)
                    ) {
                        Text(if (isReady) "取消准备" else "准备", fontWeight = FontWeight.Bold)
                    }
                    if (isHost) {
                        Button(
                            onClick = onRestart,
                            modifier = Modifier.weight(1f).height(52.dp),
                            enabled = canRestart,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral)
                        ) {
                            Text(if (canRestart) "开始下一局" else "等待全员准备", fontWeight = FontWeight.Bold)
                        }
                        if (compact) {
                            IconButton(onClick = onDisband, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Default.DeleteForever, "解散房间", tint = Color(0xFFF28B78))
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.weight(1f).height(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isReady) "已准备，等待房主开始" else "准备后等待房主开始",
                                color = Muted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                canBid -> {
                    ActionButton(
                        resource = if (game.bidCandidateIndex == null) R.drawable.actions_action_call else R.drawable.actions_action_rob,
                        label = if (game.bidCandidateIndex == null) "叫地主" else "抢地主",
                        onClick = { onCall(true) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        resource = R.drawable.actions_action_pass,
                        label = if (game.bidCandidateIndex == null) "不叫" else "不抢",
                        onClick = { onCall(false) },
                        modifier = Modifier.weight(1f),
                        outlined = true
                    )
                }
                canPlay -> {
                    ActionButton(
                        resource = R.drawable.actions_action_play,
                        label = "出牌",
                        onClick = onPlay,
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        resource = R.drawable.actions_action_pass,
                        label = "不出",
                        onClick = onPass,
                        enabled = game.canPass(),
                        modifier = Modifier.weight(1f),
                        outlined = true
                    )
                }
                game.phase == Phase.PLAYING -> {
                    Text(
                        text = if (state.connection == AppConnectionState.RECONNECTING) "连接恢复后继续" else "${game.currentSeat.name} 正在操作",
                        color = Muted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    resource: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false
) {
    val content = @Composable {
        Image(
            painter = painterResource(resource),
            contentDescription = label,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
    if (outlined) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp)) {
            content()
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(52.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral)
        ) {
            content()
        }
    }
}

@Composable
private fun CardBackPile() {
    Image(
        painter = painterResource(R.drawable.table_card_pile),
        contentDescription = "底牌",
        modifier = Modifier.size(width = 78.dp, height = 58.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun HandRow(
    cards: List<Card>,
    selected: Set<Card>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Card) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(108.dp)) {
        val cardWidth = ((maxWidth - 18.dp) / 6.5f).coerceIn(42.dp, 76.dp)
        CardRow(
            cards = cards.sortedDescending(),
            width = cardWidth,
            height = cardWidth * 1.4f,
            step = ((maxWidth - cardWidth) / (cards.size - 1).coerceAtLeast(1)).coerceIn(18.dp, cardWidth * .55f),
            selected = selected,
            enabled = enabled,
            onToggle = onToggle
        )
    }
}

@Composable
private fun FinishedHandRows(
    cards: List<Card>,
    modifier: Modifier = Modifier
) {
    val sortedCards = cards.sortedDescending()
    if (sortedCards.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(112.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("已出完手牌", color = Wax, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    val rows = if (sortedCards.size > 10) {
        val firstRowSize = (sortedCards.size + 1) / 2
        listOf(sortedCards.take(firstRowSize), sortedCards.drop(firstRowSize))
    } else {
        listOf(sortedCards)
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(116.dp)) {
        val availableWidth = maxWidth
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            rows.forEach { rowCards ->
                val cardWidth = ((availableWidth - 12.dp) / 8f).coerceIn(34.dp, 42.dp)
                val step = if (rowCards.size == 1) {
                    0.dp
                } else {
                    ((availableWidth - cardWidth) / (rowCards.size - 1))
                        .coerceAtLeast(15.dp)
                        .coerceAtMost(cardWidth * .62f)
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(57.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CardRow(
                        cards = rowCards,
                        width = cardWidth,
                        height = cardWidth * 1.35f,
                        step = step
                    )
                }
            }
        }
    }
}

@Composable
private fun CardRow(
    cards: List<Card>,
    width: Dp,
    height: Dp,
    step: Dp,
    selected: Set<Card> = emptySet(),
    enabled: Boolean = false,
    onToggle: (Card) -> Unit = {}
) {
    val totalWidth = width + step * (cards.size - 1).coerceAtLeast(0)
    Box(modifier = Modifier.width(totalWidth).height(height + 10.dp)) {
        cards.forEachIndexed { index, card ->
            PlayingCard(
                card = card,
                width = width,
                height = height,
                selected = card in selected,
                enabled = enabled,
                modifier = Modifier.offset(x = step * index),
                onToggle = { onToggle(card) }
            )
        }
    }
}

@Composable
private fun PlayingCard(
    card: Card,
    width: Dp,
    height: Dp,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit
) {
    val clickableModifier = if (enabled) Modifier.clickable(onClick = onToggle) else Modifier
    Image(
        painter = painterResource(cardResource(card)),
        contentDescription = card.displayText,
        modifier = modifier
            .width(width)
            .height(height)
            .offset(y = if (selected) (-8).dp else 0.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = Wax,
                shape = RoundedCornerShape(6.dp)
            )
            .then(clickableModifier),
        contentScale = ContentScale.FillBounds
    )
}

@Composable
private fun ReconnectingOverlay(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xB3091F27)),
        contentAlignment = Alignment.Center
    ) {
        Surface(color = Color(0xF212333B), shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.system_status_reconnecting),
                    contentDescription = "正在重连",
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text("正在重新连接", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("牌局状态仍由服务器保存", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onRetry) {
                    Text("重新连接")
                }
            }
        }
    }
}

@Composable
private fun RoundScorePanel(
    game: EngineState,
    onDismiss: () -> Unit
) {
    val winner = game.seats.getOrNull(game.winnerIndex ?: 0)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        Surface(
            modifier = Modifier.padding(top = 58.dp, start = 18.dp).width(300.dp),
            color = Color(0xF212333B),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .14f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("本局结算", color = Wax, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            winner?.let { "${it.name} 获胜" } ?: "牌局结束",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "关闭本局结算", tint = Muted)
                    }
                }
                Text(
                    "服务器倍数 ×${game.multiplier}",
                    color = Muted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                game.seats.forEach { seat ->
                    val delta = game.scoreDelta.getOrNull(seat.index) ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${seat.name}${if (seat.isLandlord) " · 地主" else " · 农民"}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            "${if (delta >= 0) "+" else ""}$delta",
                            color = if (delta >= 0) Color(0xFF7ED9A3) else Color(0xFFF28B78),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun cardResource(card: Card): Int {
    if (card.rank == Rank.SMALL_JOKER) return R.drawable.cards_card_joker_small
    if (card.rank == Rank.BIG_JOKER) return R.drawable.cards_card_joker_big
    val suit = when (card.suit) {
        Suit.SPADE -> "spade"
        Suit.HEART -> "heart"
        Suit.CLUB -> "club"
        Suit.DIAMOND -> "diamond"
        Suit.JOKER -> "joker"
    }
    val rank = when (card.rank) {
        Rank.R3 -> "3"
        Rank.R4 -> "4"
        Rank.R5 -> "5"
        Rank.R6 -> "6"
        Rank.R7 -> "7"
        Rank.R8 -> "8"
        Rank.R9 -> "9"
        Rank.R10 -> "10"
        Rank.RJ -> "j"
        Rank.RQ -> "q"
        Rank.RK -> "k"
        Rank.RA -> "a"
        Rank.R2 -> "2"
        Rank.SMALL_JOKER, Rank.BIG_JOKER -> ""
    }
    return when (suit) {
        "joker" -> R.drawable.cards_card_back
        "spade" -> cardDrawable(rank, CardSuit.SPADE)
        "heart" -> cardDrawable(rank, CardSuit.HEART)
        "club" -> cardDrawable(rank, CardSuit.CLUB)
        else -> cardDrawable(rank, CardSuit.DIAMOND)
    }
}

private enum class CardSuit { SPADE, HEART, CLUB, DIAMOND }

private fun nextSeat(index: Int): Int = (index + 2) % 3

private fun cardDrawable(rank: String, suit: CardSuit): Int = when (suit) {
    CardSuit.SPADE -> when (rank) {
        "a" -> R.drawable.cards_card_spade_a
        "2" -> R.drawable.cards_card_spade_2
        "3" -> R.drawable.cards_card_spade_3
        "4" -> R.drawable.cards_card_spade_4
        "5" -> R.drawable.cards_card_spade_5
        "6" -> R.drawable.cards_card_spade_6
        "7" -> R.drawable.cards_card_spade_7
        "8" -> R.drawable.cards_card_spade_8
        "9" -> R.drawable.cards_card_spade_9
        "10" -> R.drawable.cards_card_spade_10
        "j" -> R.drawable.cards_card_spade_j
        "q" -> R.drawable.cards_card_spade_q
        else -> R.drawable.cards_card_spade_k
    }
    CardSuit.HEART -> when (rank) {
        "a" -> R.drawable.cards_card_heart_a
        "2" -> R.drawable.cards_card_heart_2
        "3" -> R.drawable.cards_card_heart_3
        "4" -> R.drawable.cards_card_heart_4
        "5" -> R.drawable.cards_card_heart_5
        "6" -> R.drawable.cards_card_heart_6
        "7" -> R.drawable.cards_card_heart_7
        "8" -> R.drawable.cards_card_heart_8
        "9" -> R.drawable.cards_card_heart_9
        "10" -> R.drawable.cards_card_heart_10
        "j" -> R.drawable.cards_card_heart_j
        "q" -> R.drawable.cards_card_heart_q
        else -> R.drawable.cards_card_heart_k
    }
    CardSuit.CLUB -> when (rank) {
        "a" -> R.drawable.cards_card_club_a
        "2" -> R.drawable.cards_card_club_2
        "3" -> R.drawable.cards_card_club_3
        "4" -> R.drawable.cards_card_club_4
        "5" -> R.drawable.cards_card_club_5
        "6" -> R.drawable.cards_card_club_6
        "7" -> R.drawable.cards_card_club_7
        "8" -> R.drawable.cards_card_club_8
        "9" -> R.drawable.cards_card_club_9
        "10" -> R.drawable.cards_card_club_10
        "j" -> R.drawable.cards_card_club_j
        "q" -> R.drawable.cards_card_club_q
        else -> R.drawable.cards_card_club_k
    }
    CardSuit.DIAMOND -> when (rank) {
        "a" -> R.drawable.cards_card_diamond_a
        "2" -> R.drawable.cards_card_diamond_2
        "3" -> R.drawable.cards_card_diamond_3
        "4" -> R.drawable.cards_card_diamond_4
        "5" -> R.drawable.cards_card_diamond_5
        "6" -> R.drawable.cards_card_diamond_6
        "7" -> R.drawable.cards_card_diamond_7
        "8" -> R.drawable.cards_card_diamond_8
        "9" -> R.drawable.cards_card_diamond_9
        "10" -> R.drawable.cards_card_diamond_10
        "j" -> R.drawable.cards_card_diamond_j
        "q" -> R.drawable.cards_card_diamond_q
        else -> R.drawable.cards_card_diamond_k
    }
}
