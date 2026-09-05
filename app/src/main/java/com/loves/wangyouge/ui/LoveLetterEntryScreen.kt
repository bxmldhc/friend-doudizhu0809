package com.loves.wangyouge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoveLetterEntryScreen(state: GameUiState.LoveLetterEntry, onBack: () -> Unit, onCreate: (Int, String) -> Unit) {
    var count by remember { mutableIntStateOf(state.playerCount) }
    Column(Modifier.fillMaxSize().background(Color(0xFF091F27)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回大厅", tint = Color.White) }
            Text("创建情书房间", color = Color.White, fontSize = 24.sp)
        }
        Surface(color = Color(0xFF12343B), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("选择本局人数", color = Color(0xFFF2D675), fontSize = 17.sp)
                Text("不足人数时，开始游戏会自动补入 AI。", color = Color(0xFFB4CED0), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(2, 3, 4).forEach { value -> Button(onClick = { count = value }) { Text(if (count == value) "✓ $value 人" else "$value 人") } }
                }
            }
        }
        Button(onClick = { onCreate(count, state.serverUrl) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("创建并进入等待区") }
    }
}
