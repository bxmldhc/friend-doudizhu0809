package com.loves.wangyouge.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loves.wangyouge.network.AccountStats
import com.loves.wangyouge.network.MatchRound
import com.loves.wangyouge.network.RecentMatch

private val HistoryBg = Color(0xFF091F27)
private val HistoryPanel = Color(0xFF12333B)
private val Muted = Color(0xFF9DB8BC)
private val Wax = Color(0xFFF2D675)
private val Positive = Color(0xFF7ED9A3)
private val Negative = Color(0xFFF28B78)

@Composable
fun HistoryScreen(
    stats: AccountStats?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val matches = stats?.recentMatches.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HistoryBg),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回首页", tint = Color.White)
                }
                Column {
                    Text("最近战绩", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("按账号保存最近十场，点击场次查看每一小局", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item(key = "stats") {
            StatsPanel(stats = stats, loading = loading, error = error, onRetry = onRetry)
        }
        if (!loading && matches.isEmpty()) {
            item(key = "empty") {
                EmptyHistory(modifier = Modifier.fillMaxWidth().height(150.dp))
            }
        } else {
            items(matches, key = { it.matchId }) { match ->
                MatchCard(match, stats?.playerId.orEmpty())
            }
        }
    }
}

@Composable
private fun StatsPanel(
    stats: AccountStats?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0E3B42),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            when {
                loading -> Text("正在同步战绩…", color = Muted)
                error != null -> {
                    Text(
                        "战绩同步失败：$error",
                        color = Negative,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("重试")
                    }
                }
                stats != null -> {
                    val winRate = if (stats.games == 0) 0 else stats.wins * 100 / stats.games
                    val landlordRate = winRate(stats.landlordWins, stats.landlordGames)
                    val farmerRate = winRate(stats.farmerWins, stats.farmerGames)
                    StatItem("累计得分", stats.totalScore.toString())
                    StatItem("完成小局", stats.games.toString())
                    StatItem("总胜率", "$winRate%")
                    StatItem("地主胜率", "$landlordRate%")
                    StatItem("农民胜率", "$farmerRate%")
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = HistoryPanel, shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("还没有已完成的场次", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("完成一小局后，这里会自动同步。", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Wax, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

private fun winRate(wins: Int, games: Int): Int {
    return if (games <= 0) 0 else wins * 100 / games
}

@Composable
private fun MatchCard(match: RecentMatch, viewerPlayerId: String) {
    var expanded by remember(match.matchId) { mutableStateOf(false) }
    val time = formatTime(match.updatedAt)
    val totalRounds = match.rounds.size
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        color = HistoryPanel,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "房间 ${match.roomCode.ifBlank { "未知" }}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text("$time · $totalRounds 小局", color = Muted, fontSize = 12.sp)
                }
                Text(
                    matchScore(match, viewerPlayerId),
                    color = scoreColor(matchScoreValue(match, viewerPlayerId)),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "收起详情" else "展开详情",
                    tint = Muted
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    match.rounds.forEach { round ->
                        RoundRow(round)
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundRow(round: MatchRound) {
    Surface(color = Color(0xFF0B2930), shape = RoundedCornerShape(6.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("第 ${round.gameNumber} 局", color = Wax, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("倍数 ×${round.multiplier}", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(5.dp))
            round.players.forEach { player ->
                val roles = buildList {
                    if (player.isAi) add("AI")
                    if (player.playerId == round.landlordPlayerId) add("地主")
                    if (player.playerId == round.winnerPlayerId) add("获胜")
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        buildString {
                            append(player.name)
                            if (roles.isNotEmpty()) append(" · ${roles.joinToString(" · ")}")
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        signedScore(player.delta),
                        color = scoreColor(player.delta),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun matchScoreValue(match: RecentMatch, viewerPlayerId: String): Int {
    return match.rounds.sumOf { round ->
        round.players.firstOrNull { it.playerId == viewerPlayerId }?.delta ?: 0
    }
}

private fun matchScore(match: RecentMatch, viewerPlayerId: String): String {
    return signedScore(matchScoreValue(match, viewerPlayerId))
}

private fun signedScore(value: Int): String = if (value >= 0) "+$value" else value.toString()

private fun scoreColor(value: Int): Color = when {
    value > 0 -> Positive
    value < 0 -> Negative
    else -> Muted
}

private fun formatTime(value: Long): String {
    return SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(value))
}
