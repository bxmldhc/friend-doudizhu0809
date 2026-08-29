package com.loves.wangyouge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.loves.wangyouge.network.AdminAccount
import com.loves.wangyouge.network.AdminAuditRecord
import com.loves.wangyouge.network.AdminOnlineAccount
import com.loves.wangyouge.network.AdminRoom
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
    onOnlineAccounts: List<AdminOnlineAccount>,
    onSearch: (String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onSetAccountDisabled: (String, Boolean) -> Unit,
    onSetRegistrationEnabled: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember(state.query) { mutableStateOf(state.query) }
    var resetTarget by remember { mutableStateOf<AdminAccount?>(null) }
    var showCreateAccount by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminPageBg),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header") {
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
        }

        state.overview?.let { overview ->
            item(key = "overview") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryTile("账号", overview.accountCount.toString(), Modifier.weight(1f))
                    SummaryTile("房间", overview.roomCount.toString(), Modifier.weight(1f))
                    SummaryTile("在线真人", overview.connectedHumanCount.toString(), Modifier.weight(1f))
                    SummaryTile("进行中", overview.activeGameCount.toString(), Modifier.weight(1f))
                }
            }
        }

        item(key = "tabs") {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AdminPanel,
                contentColor = AdminWax
            ) {
                Tab(selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("账号管理") })
                Tab(selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("在线账号") })
                Tab(selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("当前房间") })
                Tab(selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("操作记录") })
            }
        }

        when {
            state.loading && state.overview == null -> {
                item(key = "loading") {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AdminCoral)
                    }
                }
            }
            state.error != null && state.overview == null -> {
                item(key = "error") {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error, color = Color(0xFFF28B78))
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onRetry) { Text("重试") }
                        }
                    }
                }
            }
            selectedTab == 0 -> {
                item(key = "registration") {
                    Surface(
                        color = AdminPanel,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("允许注册新账号", color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    if (state.registrationEnabled) "当前开放注册" else "当前仅允许已有账号登录",
                                    color = AdminMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (state.updatingRegistration) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Switch(
                                    checked = state.registrationEnabled,
                                    onCheckedChange = onSetRegistrationEnabled
                                )
                            }
                        }
                    }
                }
                item(key = "account-actions") {
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
                        Button(
                            onClick = { showCreateAccount = true },
                            enabled = !state.creatingAccount,
                            modifier = Modifier.height(50.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = AdminWax,
                                contentColor = AdminPageBg
                            )
                        ) {
                            if (state.creatingAccount) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("创建账号")
                            }
                        }
                    }
                }
                items(state.accounts, key = { it.accountId }) { account ->
                    AccountRow(
                        account = account,
                        resetting = state.resettingAccountId == account.accountId,
                        updating = state.updatingAccountId == account.accountId,
                        onReset = { resetTarget = account },
                        onSetDisabled = { disabled ->
                            onSetAccountDisabled(account.accountId, disabled)
                        }
                    )
                }
                if (!state.loading && state.accounts.isEmpty()) {
                    item(key = "accounts-empty") {
                        AdminEmptyState("没有找到账号")
                    }
                }
            }
            selectedTab == 1 -> {
                items(onOnlineAccounts, key = { "${it.nickname}-${it.roomCode}" }) { account ->
                    OnlineAccountRow(account)
                }
                if (!state.loading && onOnlineAccounts.isEmpty()) {
                    item(key = "online-empty") {
                        AdminEmptyState("当前没有已登录的在线账号")
                    }
                }
            }
            selectedTab == 2 -> {
                items(state.rooms, key = { it.roomCode }) { room ->
                    RoomRow(room)
                }
                if (!state.loading && state.rooms.isEmpty()) {
                    item(key = "rooms-empty") {
                        AdminEmptyState("当前没有房间")
                    }
                }
            }
            selectedTab == 3 -> {
                items(state.auditRecords, key = { it.auditId }) { record ->
                    AuditRow(record)
                }
                if (!state.loading && state.auditRecords.isEmpty()) {
                    item(key = "audit-empty") {
                        AdminEmptyState("还没有管理操作记录")
                    }
                }
            }
            else -> Unit
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
    if (showCreateAccount) {
        CreateAccountDialog(
            loading = state.creatingAccount,
            onDismiss = {
                if (!state.creatingAccount) showCreateAccount = false
            },
            onConfirm = { nickname, password ->
                showCreateAccount = false
                onCreateAccount(nickname, password)
            }
        )
    }
}

@Composable
private fun OnlineAccountRow(account: AdminOnlineAccount) {
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
                Text(account.nickname, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "${if (account.gameType == "GOMOKU") "五子棋" else "斗地主"} · 房间 ${account.roomCode}",
                    color = AdminMuted,
                    fontSize = 11.sp
                )
            }
            Text(
                "在线",
                color = Color(0xFF7ED9A3),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AdminEmptyState(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(112.dp),
        color = AdminPanel,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(message, color = AdminMuted, fontSize = 13.sp)
        }
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
private fun AccountRow(
    account: AdminAccount,
    resetting: Boolean,
    updating: Boolean,
    onReset: () -> Unit,
    onSetDisabled: (Boolean) -> Unit
) {
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
                        if (account.disabled) append(" · 已禁用")
                        if (account.mustChangePassword) append(" · 待改密")
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
                enabled = !resetting && !updating,
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
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onSetDisabled(!account.disabled) },
                enabled = !resetting && !updating && !account.isAdmin,
                modifier = Modifier.height(48.dp)
            ) {
                if (updating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (account.disabled) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (account.disabled) "恢复" else "禁用")
                }
            }
        }
    }
}

@Composable
private fun CreateAccountDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val cleanNickname = nickname.trim()
    val error = when {
        nickname.isNotEmpty() && cleanNickname.length !in 2..12 -> "账号名需要 2 到 12 个字符"
        password.isNotEmpty() && password.length !in 6..64 -> "临时密码需要 6 到 64 个字符"
        confirm.isNotEmpty() && password != confirm -> "两次输入的临时密码不一致"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建临时密码账号") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(.76f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "该操作不受公开注册开关影响。用户首次登录后必须先修改密码，才能创建、加入或恢复房间。",
                    color = AdminMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(12) },
                    label = { Text("账号名 / 昵称") },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text("临时密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.take(64) },
                    label = { Text("再次输入临时密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !loading,
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
                onClick = { onConfirm(cleanNickname, password) },
                enabled = !loading &&
                    cleanNickname.length in 2..12 &&
                    password.length in 6..64 &&
                    password == confirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = AdminCoral
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("创建账号")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AuditRow(record: AdminAuditRecord) {
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
                Text(auditActionText(record.action), color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(record.actorNickname)
                        record.targetNickname?.let { append(" → $it") }
                    },
                    color = AdminMuted,
                    fontSize = 11.sp
                )
            }
            Text(formatTime(record.createdAt), color = AdminMuted, fontSize = 11.sp)
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

private fun auditActionText(action: String): String = when (action.uppercase()) {
    "ACCOUNT_DISABLED" -> "禁用账号"
    "ACCOUNT_ENABLED" -> "恢复账号"
    "PASSWORD_RESET" -> "重置密码"
    "ACCOUNT_CREATED" -> "创建账号"
    "REGISTRATION_ENABLED" -> "开放注册"
    "REGISTRATION_DISABLED" -> "关闭注册"
    else -> action
}
