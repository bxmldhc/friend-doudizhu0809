package com.loves.wangyouge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.loves.wangyouge.R
import com.loves.wangyouge.network.InviteLink

private val LobbyBg = Color(0xFF091F27)
private val Panel = Color(0xFF12333B)
private val Coral = Color(0xFFE97D58)
private val Wax = Color(0xFFF2D675)
private val Muted = Color(0xFF9DB8BC)

@Composable
fun LobbyScreen(
    state: GameUiState.Lobby,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onDisband: () -> Unit
) {
    val context = LocalContext.current
    var showDisbandConfirm by remember { mutableStateOf(false) }
    val humanPlayers = state.players
        .take(3)
        .filter { it.isNotBlank() && it != "等待加入" }
    val lobbySeats = buildList {
        state.players.take(3).forEach { name ->
            if (name.isNotBlank() && name != "等待加入") add(LobbySeatData(name, true))
        }
        repeat(state.aiCount) { add(LobbySeatData("服务器 AI", false)) }
        while (size < 3) add(LobbySeatData("等待真人", null))
    }.take(3)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LobbyBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.height(42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出房间", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("等待区", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (state.isHost) "你是房主，可以请求开始游戏" else "等待房主开始游戏",
                    color = Muted,
                    fontSize = 14.sp
                )
            }
            ConnectionPill(state.connection, onRetry)
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.height(64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("房号", color = Muted, fontSize = 13.sp)
                    Text(
                        state.roomCode.chunked(3).joinToString(" "),
                        color = Wax,
                        fontSize = 25.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                }
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("房号", state.roomCode))
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, "复制房号", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "来忘忧阁玩斗地主，房号：${state.roomCode}\n" +
                                            "点击直接加入：${InviteLink.createShareUrl(state.roomCode)}"
                                    )
                                },
                                "分享房间"
                            )
                        )
                    }
                ) {
                    Icon(Icons.Default.IosShare, "分享房号", tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            lobbySeats.forEachIndexed { index, seat ->
                LobbySeat(
                    index = index,
                    name = seat.name,
                    isHuman = seat.isHuman,
                    isSelf = index == state.yourSeat,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            color = Color(0xFF0E3B42),
            shape = RoundedCornerShape(14.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "三人好友局",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "本局配置：${state.aiCount} 个 AI · ${difficultyLabel(state.config.difficulty)}" +
                            " · ${state.config.actionTimeoutSeconds} 秒 · 真人 " +
                            "${humanPlayers.size}/${state.requiredHumans}",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (humanPlayers.size == state.requiredHumans) {
                            "人数已满足，可以请求开始"
                        } else {
                            "还需要 ${state.requiredHumans - humanPlayers.size} 名真人玩家"
                        },
                        color = if (humanPlayers.size == state.requiredHumans) Wax else Muted,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (state.isHost) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDisbandConfirm = true },
                    modifier = Modifier.weight(0.42f).height(48.dp)
                ) {
                    Text("解散房间")
                }
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(0.58f).height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Coral
                    )
                ) {
                    Text("开始游戏", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        } else {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("等待房主开始")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (showDisbandConfirm) {
        AlertDialog(
            onDismissRequest = { showDisbandConfirm = false },
            title = { Text("解散这个房间？") },
            text = { Text("房间和场内分数将被清除，其他玩家会返回首页。此操作无法撤销。") },
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
                TextButton(onClick = { showDisbandConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun difficultyLabel(difficulty: com.loves.wangyouge.model.AiDifficulty): String = when (difficulty) {
    com.loves.wangyouge.model.AiDifficulty.EASY -> "简单 AI"
    com.loves.wangyouge.model.AiDifficulty.MEDIUM -> "普通 AI"
    com.loves.wangyouge.model.AiDifficulty.HARD -> "困难 AI"
}

private data class LobbySeatData(val name: String, val isHuman: Boolean?)

@Composable
private fun LobbySeat(index: Int, name: String, isHuman: Boolean?, isSelf: Boolean, modifier: Modifier) {
    val waiting = isHuman == null
    val ai = isHuman == false
    Surface(
        modifier = modifier,
        color = if (isSelf) Color(0xFF314B40) else Panel,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(
                    if (waiting) Color(0xFF28434A) else if (ai) Color(0xFF51666A) else if (index == 0) Coral else Color(0xFF2E8D7C),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (waiting) "+" else if (ai) "AI" else name.take(1),
                    color = if (waiting) Muted else Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = when {
                    waiting -> "等待真人"
                    ai -> "服务器托管"
                    isSelf -> "你 · 已连接"
                    else -> "真人玩家"
                },
                color = if (waiting) Muted else Wax,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ConnectingScreen(
    title: String,
    message: String,
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxSize().background(LobbyBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.system_status_reconnecting),
            contentDescription = null,
            modifier = Modifier.size(76.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (primaryLabel != null && onPrimary != null) {
                Button(
                    onClick = onPrimary,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Coral
                    )
                ) {
                    if (primaryLabel == "重新连接") {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(primaryLabel)
                }
            }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}

@Composable
private fun ConnectionPill(state: AppConnectionState, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when (state) {
                AppConnectionState.ONLINE -> "已同步"
                AppConnectionState.CONNECTING -> "连接中"
                AppConnectionState.RECONNECTING -> "重连中"
                AppConnectionState.OFFLINE -> "离线"
            },
            color = if (state == AppConnectionState.ONLINE) Color(0xFF78D6A1) else Wax,
            fontSize = 12.sp
        )
        if (state == AppConnectionState.RECONNECTING) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, "重新连接", tint = Color.White)
            }
        }
    }
}
