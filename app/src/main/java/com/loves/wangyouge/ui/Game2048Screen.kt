package com.loves.wangyouge.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loves.wangyouge.logic.Move2048

private val GameBg = Color(0xFF091F27)
private val Board = Color(0xFF345259)
private val Empty = Color(0xFF28454C)
private val Accent = Color(0xFFE97D58)
private val Gold = Color(0xFFF2D675)
private val Muted = Color(0xFF9DB8BC)

@Composable
fun Game2048Screen(
    state: GameUiState.Game2048,
    onBack: () -> Unit,
    onMove: (Move2048) -> Unit,
    onRestart: () -> Unit,
    onLeaderboard: () -> Unit
) {
    var showRanks by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(GameBg).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回大厅", tint = Color.White) }
            Text("2048", color = Color.White, fontSize = 23.sp)
            Spacer(Modifier.weight(1f))
            Text("单人游戏", color = Muted, fontSize = 12.sp)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                val boardSize = minOf(maxWidth, maxHeight)
                Box(
                    Modifier.size(boardSize).padding(4.dp)
                        .background(Board, RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        var totalDragX = 0f
                        var totalDragY = 0f
                        detectDragGestures(
                            onDragStart = { totalDragX = 0f; totalDragY = 0f },
                            onDragEnd = {
                                if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) && kotlin.math.abs(totalDragX) > 24) {
                                    onMove(if (totalDragX > 0) Move2048.RIGHT else Move2048.LEFT)
                                } else if (kotlin.math.abs(totalDragY) > 24) {
                                    onMove(if (totalDragY > 0) Move2048.DOWN else Move2048.UP)
                                }
                            },
                            onDragCancel = { totalDragX = 0f; totalDragY = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                            }
                        )
                    },
                    contentAlignment = Alignment.Center
                ) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (0 until 4).forEach { column ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            (0 until 4).forEach { row ->
                                val index = row * 4 + column
                                Tile(
                                    value = state.state.cells[index],
                                    isNew = state.state.newTileIndex == index,
                                    isMerged = index in state.state.mergedTileIndices,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                if (state.state.ended || state.state.won) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xE8193035))) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (state.state.won) "恭喜合成 2048" else "这局结束了", color = Gold, fontSize = 20.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onRestart) { Text("再来一局") }
                        }
                    }
                }
            }
            }
            Column(
                Modifier.width(230.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(Modifier.height(8.dp))
                ScoreBox("当前分数", state.state.score)
                Spacer(Modifier.height(8.dp))
                ScoreBox("最高分", state.bestScore)
                Spacer(Modifier.height(14.dp))
                Text("移动 ${state.state.moves} 次", color = Muted, fontSize = 13.sp)
                Text("最高方块 ${state.state.maxTile}", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(5.dp)); Text("重新开始")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showRanks = true; onLeaderboard() }, modifier = Modifier.fillMaxWidth()) { Text("排行榜") }
            }
        }
    }
    if (showRanks) LeaderboardDialog(state, { showRanks = false })
}

@Composable
private fun Tile(value: Int, isNew: Boolean, isMerged: Boolean, modifier: Modifier) {
    var settled by remember(value, isNew, isMerged) { mutableStateOf(!(isNew || isMerged)) }
    LaunchedEffect(value, isNew, isMerged) {
        if (isNew || isMerged) {
            settled = false
            delay(16)
            settled = true
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (settled) 1f else if (isMerged) 1.15f else .68f,
        animationSpec = tween(180),
        label = "2048-tile-scale"
    )
    val color = tileColor(value)
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .background(color, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (value != 0) Text(
            value.toString(),
            color = if (value <= 4) Color(0xFF282722) else Color.White,
            fontSize = when { value >= 1024 -> 20.sp; value >= 128 -> 24.sp; else -> 30.sp }
        )
    }
}

private fun tileColor(value: Int): Color = when (value) {
    0 -> Empty
    2 -> Color(0xFFF1E6D4)
    4 -> Color(0xFFEAD7BD)
    8 -> Color(0xFFD99D70)
    16 -> Color(0xFFCB8153)
    32 -> Color(0xFFBD6B3C)
    64 -> Color(0xFFA95038)
    128 -> Color(0xFFD1AD5C)
    256 -> Color(0xFFBD9440)
    512 -> Color(0xFF9A7939)
    1024 -> Color(0xFF6A855F)
    else -> Color(0xFF547C61)
}

@Composable
private fun ScoreBox(label: String, value: Int) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF12333B))) {
        Column(Modifier.padding(14.dp)) { Text(label, color = Muted, fontSize = 12.sp); Text(value.toString(), color = Gold, fontSize = 28.sp) }
    }
}

@Composable
private fun LeaderboardDialog(state: GameUiState.Game2048, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("2048 排行榜") },
        text = {
            when {
                state.leaderboardLoading -> Text("正在加载排行榜…")
                state.leaderboardError != null -> Text("排行榜暂时无法加载：${state.leaderboardError}")
                state.leaderboard.isEmpty() -> Text("还没有排行榜记录")
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    itemsIndexed(state.leaderboard) { index, entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", Modifier.width(28.dp), color = Muted)
                            Text(entry.name, Modifier.weight(1f))
                            Text(entry.score.toString(), color = if (entry.score >= state.bestScore) Accent else Color.Unspecified)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } }
    )
}
