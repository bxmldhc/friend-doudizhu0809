package com.loves.doudizhu.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loves.doudizhu.network.AccountStats

private val HistoryBg = Color(0xFF091F27)
private val HistoryPanel = Color(0xFF12333B)
private val Muted = Color(0xFF9DB8BC)
private val Wax = Color(0xFFF2D675)

@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    stats: AccountStats?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(HistoryBg).padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回首页", tint = Color.White)
            }
            Column {
                Text("最近战绩", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("只显示已经由服务器确认结束的牌局", color = Muted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0E3B42),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                when {
                    loading -> Text("正在同步累计战绩…", color = Muted)
                    error != null -> Text("累计战绩同步失败：$error", color = Color(0xFFF28B78))
                    stats != null -> {
                        StatItem("累计得分", stats.totalScore.toString())
                        StatItem("完成牌局", stats.games.toString())
                        StatItem("胜局", stats.wins.toString())
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = HistoryPanel,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("还没有已完成的牌局", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("和朋友开一局，结束后这里会自动记录。", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                entries.forEach { entry ->
                    HistoryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Wax, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry) {
    val time = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(entry.time))
    val winnerText = if (entry.winner.isBlank()) "牌局结束" else "${entry.winner} 获胜"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HistoryPanel,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(winnerText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "$time  ·  房号 ${entry.roomCode.ifBlank { "未知" }}",
                    color = Muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.players.filter { it.isNotBlank() && it != "等待加入" }.joinToString("、"),
                    color = Muted,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("×${entry.multiplier}", color = Wax, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.scoreDelta.joinToString(" / ") { if (it >= 0) "+$it" else it.toString() },
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}
