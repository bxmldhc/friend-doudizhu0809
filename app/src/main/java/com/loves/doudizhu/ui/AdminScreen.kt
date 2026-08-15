package com.loves.doudizhu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loves.doudizhu.network.AdminAccount
import com.loves.doudizhu.network.AdminRoom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AdminPageBg = Color(0xFF091F27)
private val AdminPanel = Color(0xFF12333B)
private val AdminMuted = Color(0xFF9DB8BC)
private val AdminCoral = Color(0xFFE97D58)
private val AdminWax = Color(0xFFF2D675)

@Composable
fun AdminScreen(
    state: GameUiState.Admin,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
    onResetPassword: (String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember(state.query) { mutableStateOf(state.query) }
    var resetTarget by remember { mutableStateOf<AdminAccount?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminPageBg)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回首页", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("超级管理入口", color = AdminWax, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("账号与房间概览", color = AdminMuted, fontSize = 12.sp)
            }
            IconButton(onClick = onRetry, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Refresh, "刷新", tint = Color.White)
            }
        }

        state.overview?.let { overview ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryTile("账号", overview.accountCount.toString(), Modifier.weight(1f))
                SummaryTile("房间", overview.roomCount.toString(), Modifier.weight(1f))
                SummaryTile("在线真人", overview.connectedHumanCount.toString(), Modifier.weight(1f))
                SummaryTile("进行中", overview.activeGameCount.toString(), Modifier.weight(1f))
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = AdminPanel,
            contentColor = AdminWax
        ) {
            Tab(selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("账号管理") })
            Tab(selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("当前房间") })
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.loading && state.overview == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AdminCoral)
                }
            }
            state.error != null && state.overview == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = Color(0xFFF28B78))
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onRetry) { Text("重试") }
                    }
                }
            }
            selectedTab == 0 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(24) },
                        label = { Text("搜索账号名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSearch(query) },
                        modifier = Modifier.height(50.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = AdminCoral
                        )
                    ) {
                        Text("搜索")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.accounts, key = { it.accountId }) { account ->
                        AccountRow(
                            account = account,
                            resetting = state.resettingAccountId == account.accountId,
                            onReset = { resetTarget = account }
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.rooms, key = { it.roomCode }) { room ->
                        RoomRow(room)
                    }
                }
            }
        }
    }

    resetTarget?.let { account ->
        ResetPasswordDialog(
            account = account,
            onDismiss = { resetTarget = null },
            onConfirm = { password ->
                resetTarget = null
                onResetPassword(account.accountId, password)
            }
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier.height(56.dp),
        color = AdminPanel,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, color = AdminWax, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(label, color = AdminMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AccountRow(account: AdminAccount, resetting: Boolean, onReset: () -> Unit) {
    Surface(
        color = AdminPanel,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    buildString {
                        append(account.nickname)
                        if (account.isAdmin) append(" · 管理员")
                    },
                    color = if (account.isAdmin) AdminWax else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "总分 ${signed(account.totalScore)} · ${account.games} 局 · 胜率 ${winRate(account.wins, account.games)}",
                    color = AdminMuted,
                    fontSize = 11.sp
                )
                Text(
                    "注册 ${formatTime(account.createdAt)}",
                    color = AdminMuted,
                    fontSize = 10.sp
                )
            }
            OutlinedButton(
                onClick = onReset,
                enabled = !resetting,
                modifier = Modifier.height(48.dp)
            ) {
                if (resetting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重置密码")
                }
            }
        }
    }
}

@Composable
private fun RoomRow(room: AdminRoom) {
    Surface(
        color = AdminPanel,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "房间 ${room.roomCode}",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${phaseText(room.phase)} · 第 ${room.gameNumber} 局 · 房主 ${room.hostName}",
                    color = AdminMuted,
                    fontSize = 11.sp
                )
            }
            Text(
                "真人 ${room.connectedHumanCount}/${room.humanCount} · AI ${room.aiCount}",
                color = AdminWax,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ResetPasswordDialog(
    account: AdminAccount,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val error = when {
        password.isNotEmpty() && password.length < 6 -> "密码至少 6 位"
        confirm.isNotEmpty() && password != confirm -> "两次输入的密码不一致"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重置 ${account.nickname} 的密码") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(.72f)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text("新密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.take(64) },
                    label = { Text("再次输入新密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xFFF28B78), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.length >= 6 && password == confirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = AdminCoral
                )
            ) {
                Text("确认重置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()

private fun winRate(wins: Int, games: Int): String {
    if (games <= 0) return "0%"
    return "${(wins * 100f / games).toInt()}%"
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知"
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))
}

private fun phaseText(phase: String): String = when (phase.uppercase()) {
    "WAITING" -> "等待中"
    "BIDDING" -> "叫地主"
    "PLAYING" -> "牌局中"
    "FINISHED" -> "本局结束"
    else -> phase.ifBlank { "未知状态" }
}
