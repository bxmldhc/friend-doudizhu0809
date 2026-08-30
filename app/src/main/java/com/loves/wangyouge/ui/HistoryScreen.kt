package com.loves.wangyouge.ui

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
import androidx.compose.foundation.lazy.LazyRow
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
import com.loves.wangyouge.network.GameStats
import com.loves.wangyouge.network.MatchRound
import com.loves.wangyouge.network.RecentMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryBg = Color(0xFF091F27)
private val HistoryPanel = Color(0xFF12333B)
private val Muted = Color(0xFF9DB8BC)
private val Wax = Color(0xFFF2D675)
private val Positive = Color(0xFF7ED9A3)
private val Negative = Color(0xFFF28B78)

private enum class HistoryGame(val wire: String, val label: String) {
    DOUDIZHU("DOUDIZHU", "斗地主"),
    GOMOKU("GOMOKU", "五子棋")
}

@Composable
fun HistoryScreen(
    stats: AccountStats?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    var selectedGame by remember { mutableStateOf(HistoryGame.DOUDIZHU) }
    val matches = stats?.recentMatches.orEmpty().filter {
        normalizedGameType(it.gameType) == selectedGame.wire
    }
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
                    Text("个人战绩", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("选择游戏查看完整胜率与最近十场", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item(key = "games") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(HistoryGame.entries.toList(), key = { it.wire }) { game ->
                    HistoryGameCard(
                        game = game,
                        stats = stats?.let { selectedStats(it, game) },
                        selected = selectedGame == game,
                        enabled = !loading,
                        onClick = { selectedGame = game }
                    )
                }
            }
        }
        item(key = "stats") {
            StatsPanel(stats, selectedGame, loading, error, onRetry)
        }
        if (!loading && matches.isEmpty()) {
            item(key = "empty") {
                EmptyHistory(selectedGame.label, Modifier.fillMaxWidth().height(150.dp))
            }
        } else {
            items(matches, key = { "${it.gameType}-${it.matchId}" }) { match ->
                MatchCard(match, stats?.playerId.orEmpty(), selectedGame)
            }
        }
    }
}

@Composable
private fun HistoryGameCard(
    game: HistoryGame,
    stats: GameStats?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val games = stats?.games ?: 0
    val wins = stats?.wins ?: 0
    Surface(
        color = if (selected) Color(0xFF0E5960) else HistoryPanel,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .width(166.dp)
            .height(104.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                game.label,
                color = if (selected) Wax else Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (games > 0) "胜率 ${rate(wins, games)}%" else "暂无正式战绩",
                color = Muted,
                fontSize = 12.sp
            )
            Text(
                if (game == HistoryGame.GOMOKU) "${games} 场对局" else "${games} 小局",
                color = if (selected) Color.White else Muted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatsPanel(
    stats: AccountStats?,
    game: HistoryGame,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF0E3B42), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            when {
                loading -> Text("正在同步战绩...", color = Muted)
                error != null -> {
                    Text("战绩同步失败：$error", color = Negative, modifier = Modifier.weight(1f))
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("重试")
                    }
                }
                stats != null -> {
                    val selected = selectedStats(stats, game)
                    StatItem("场分", selected.totalScore.toString())
                    StatItem(if (game == HistoryGame.GOMOKU) "完成对局" else "完成小局", selected.games.toString())
                    StatItem("胜局", selected.wins.toString())
                    StatItem("胜率", "${rate(selected.wins, selected.games)}%")
                    if (game == HistoryGame.DOUDIZHU) {
                        StatItem("地主胜率", "${rate(selected.landlordWins, selected.landlordGames)}%")
                        StatItem("农民胜率", "${rate(selected.farmerWins, selected.farmerGames)}%")
                    }
                }
            }
        }
    }
}

private fun selectedStats(stats: AccountStats, game: HistoryGame): GameStats {
    return stats.gameStats[game.wire] ?: GameStats()
}

@Composable
private fun EmptyHistory(label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = HistoryPanel, shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("还没有已完成的${label}场次", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("完成一局后，这里会自动同步。", color = Muted, fontSize = 13.sp)
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

@Composable
private fun MatchCard(match: RecentMatch, viewerPlayerId: String, game: HistoryGame) {
    var expanded by remember(match.matchId) { mutableStateOf(false) }
    val score = matchScoreValue(match, viewerPlayerId)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        color = HistoryPanel,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${game.label} · 房间 ${match.roomCode.ifBlank { "未知" }}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text("${formatTime(match.updatedAt)} · ${match.rounds.size} 局", color = Muted, fontSize = 12.sp)
                }
                Text(signedScore(score), color = scoreColor(score), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Muted)
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    match.rounds.forEach { round ->
                        RoundRow(round, game)
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundRow(round: MatchRound, game: HistoryGame) {
    Surface(color = Color(0xFF0B2930), shape = RoundedCornerShape(6.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("第 ${round.gameNumber} 局", color = Wax, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                if (game == HistoryGame.DOUDIZHU) {
                    Text("倍数 x${round.multiplier}", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(5.dp))
            round.players.forEach { player ->
                val labels = buildList {
                    if (player.isAi) add("AI")
                    if (game == HistoryGame.DOUDIZHU && player.playerId == round.landlordPlayerId) add("地主")
                    if (player.playerId == round.winnerPlayerId) add("获胜")
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        buildString {
                            append(player.name)
                            if (labels.isNotEmpty()) append(" · ${labels.joinToString(" · ")}")
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(signedScore(player.delta), color = scoreColor(player.delta), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun normalizedGameType(value: String): String = value.ifBlank { "DOUDIZHU" }.uppercase(Locale.ROOT)

private fun matchScoreValue(match: RecentMatch, viewerPlayerId: String): Int =
    match.rounds.sumOf { round -> round.players.firstOrNull { it.playerId == viewerPlayerId }?.delta ?: 0 }

private fun signedScore(value: Int): String = if (value >= 0) "+$value" else value.toString()

private fun scoreColor(value: Int): Color = when {
    value > 0 -> Positive
    value < 0 -> Negative
    else -> Muted
}

private fun rate(wins: Int, games: Int): Int = if (games <= 0) 0 else wins * 100 / games

private fun formatTime(value: Long): String =
    SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(value))
