package com.loves.wangyouge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loves.wangyouge.model.LoveLetterCard

private val LoveBg = Color(0xFF091F27)
private val LoveSurface = Color(0xFF12343B)
private val LoveAccent = Color(0xFFE97D58)
private val LoveWax = Color(0xFFF2D675)
private val LoveMuted = Color(0xFFB4CED0)

@Composable
fun LoveLetterScreen(
    state: GameUiState.LoveLetterPlaying,
    onPlay: (String, Int?, Int?) -> Unit,
    onExit: () -> Unit,
    onRetry: () -> Unit,
    onReady: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onStart: () -> Unit,
    onInvite: () -> Unit
) {
    val me = state.state.players.getOrNull(state.mySeat)
    var selectedCard by remember { mutableStateOf<LoveLetterCard?>(null) }
    var selectedTarget by remember { mutableStateOf<Int?>(null) }
    var guessValue by remember { mutableStateOf<Int?>(null) }
    var showRules by remember { mutableStateOf(false) }
    val canPlay = state.connection == AppConnectionState.ONLINE &&
        state.state.phase == "PLAYING" && state.state.turnIndex == state.mySeat
    Column(
        Modifier.fillMaxSize().background(LoveBg).padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回大厅", tint = Color.White) }
            Text("情书 · 房间 ${state.roomCode}", color = Color.White, fontSize = 18.sp)
            Text("  ${state.state.deckCount} 张牌", color = LoveMuted, fontSize = 12.sp)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (state.connection != AppConnectionState.ONLINE) {
                IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, "重新连接", tint = LoveWax) }
            }
            IconButton(onClick = { showRules = true }) {
                Icon(Icons.Default.Info, "查看情书规则", tint = LoveWax)
            }
        }
        Surface(color = LoveSurface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.state.phase == "FINISHED") "本轮结束" else state.state.message, color = LoveWax)
                Text(
                    when {
                        state.state.phase == "FINISHED" -> state.state.winnerIndex?.let { "${state.players.getOrElse(it) { "玩家${it + 1}" }} 获胜" } ?: "本轮结束"
                        canPlay -> "请选择一张手牌打出"
                        else -> "等待其他玩家行动"
                    },
                    color = LoveMuted, fontSize = 13.sp
                )
                state.state.lastAction?.let { action ->
                    Text("${state.players.getOrElse(action.seat) { "玩家${action.seat + 1}" }}：${cardLabel(action.card)} · ${action.result}", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.state.players.forEach { player ->
                Surface(color = if (player.seat == state.state.turnIndex) LoveAccent else LoveSurface, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.players.getOrElse(player.seat) { "玩家${player.seat + 1}" }, color = Color.White, fontSize = 12.sp)
                        Text(if (player.alive) "信物 ${player.tokens}" else "已淘汰", color = LoveMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        if (state.state.phase == "WAITING") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onInvite) { Text("邀请好友") }
                if (state.isHost) Button(onClick = onStart) { Text("开始游戏") }
            }
        }
        if (state.state.phase == "FINISHED") {
            val ready = state.state.readySeats.getOrNull(state.mySeat) == true
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onReady(!ready) }) { Text(if (ready) "取消准备" else "准备下一局") }
                if (state.isHost) Button(onClick = onRestart) { Text("开始下一局") }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        Text("你的手牌", color = LoveWax, fontSize = 14.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(me?.hand.orEmpty(), key = { it.id }) { card -> LoveLetterCardButton(card, canPlay) { selectedCard = card; selectedTarget = null; guessValue = null } }
        }
    }
    selectedCard?.let { card ->
        val needsTarget = card.name in setOf("GUARD", "PRIEST", "BARON", "KING")
        val needsGuess = card.name == "GUARD"
        AlertDialog(
            onDismissRequest = { selectedCard = null },
            title = { Text("打出 ${cardLabel(card.name)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (needsTarget) {
                        Text("选择目标", color = LoveWax)
                        state.state.players.filter { it.seat != state.mySeat && it.alive && !it.protected }.forEach { player ->
                            TextButton(onClick = { selectedTarget = player.seat }) {
                                Text("${state.players.getOrElse(player.seat) { "玩家${player.seat + 1}" }}${if (selectedTarget == player.seat) " · 已选择" else ""}")
                            }
                        }
                    }
                    if (needsGuess) {
                        Text("猜测目标手牌点数", color = LoveWax)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (2..8).forEach { value ->
                                TextButton(onClick = { guessValue = value }) { Text(if (guessValue == value) "[$value]" else value.toString()) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = (!needsTarget || selectedTarget != null) && (!needsGuess || guessValue != null),
                    onClick = { onPlay(card.id, selectedTarget, guessValue); selectedCard = null }
                ) { Text("确认打出") }
            },
            dismissButton = { TextButton(onClick = { selectedCard = null }) { Text("取消") } }
        )
    }
    if (showRules) {
        GameRulesDialog(game = RuleGame.LOVE_LETTER, onDismiss = { showRules = false })
    }
}

private fun cardLabel(name: String): String = when (name) {
    "GUARD" -> "守卫"; "PRIEST" -> "祭司"; "BARON" -> "男爵"; "HANDMAID" -> "侍女"
    "PRINCE" -> "王子"; "KING" -> "国王"; "COUNTESS" -> "伯爵夫人"; "PRINCESS" -> "公主"
    else -> name
}

@Composable
private fun LoveLetterCardButton(card: LoveLetterCard, enabled: Boolean, onClick: () -> Unit) {
    val tint = when (card.name) {
        "GUARD" -> Color(0xFFB6D7E4); "PRIEST" -> Color(0xFFB7D7A8); "BARON" -> Color(0xFFD8B1DB)
        "HANDMAID" -> Color(0xFFE7C1D0); "PRINCE" -> Color(0xFFE7B57D); "KING" -> Color(0xFFE7D47D)
        "COUNTESS" -> Color(0xFFC7B4E2); else -> Color(0xFFF2A6A0)
    }
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.size(150.dp, 210.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = tint)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(card.value.toString(), color = LoveBg, fontSize = 30.sp)
            Text(cardLabel(card.name), color = LoveBg, fontSize = 19.sp)
        }
    }
}
