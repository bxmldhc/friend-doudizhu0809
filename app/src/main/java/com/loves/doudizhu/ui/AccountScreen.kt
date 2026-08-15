package com.loves.doudizhu.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AccountPageBg = Color(0xFF091F27)
private val AccountPanel = Color(0xFF12333B)
private val AccountMuted = Color(0xFF9DB8BC)
private val AccountCoral = Color(0xFFE97D58)
private val AccountWax = Color(0xFFF2D675)

@Composable
fun AccountScreen(
    state: GameUiState.Account,
    onBack: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onLogoutOtherSessions: () -> Unit,
    onLogout: () -> Unit
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showOtherSessionsDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AccountPageBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(.9f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回首页",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("账号与安全", color = AccountWax, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("管理密码、登录设备和当前账号", color = AccountMuted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Surface(
                color = AccountPanel,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = AccountWax, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(state.nickname, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (state.isAdmin) "超级管理员" else "普通账号",
                                color = if (state.isAdmin) AccountWax else AccountMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("账号 ID", color = AccountMuted, fontSize = 11.sp)
                    Text(
                        state.accountId,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1.1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccountAction(
                icon = { Icon(Icons.Default.Key, null) },
                title = "修改密码",
                subtitle = "验证当前密码后设置新密码",
                enabled = state.loadingAction == null,
                onClick = { showPasswordDialog = true }
            )
            AccountAction(
                icon = { Icon(Icons.Default.Devices, null) },
                title = "退出其他设备",
                subtitle = "保留本机登录，撤销其他设备的登录状态",
                enabled = state.loadingAction == null,
                loading = state.loadingAction == "sessions",
                onClick = { showOtherSessionsDialog = true }
            )
            AccountAction(
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                title = "退出当前账号",
                subtitle = "返回登录页，不会删除服务器战绩",
                enabled = state.loadingAction == null,
                danger = true,
                onClick = { showLogoutDialog = true }
            )
            state.error?.let {
                Text(it, color = Color(0xFFF28B78), fontSize = 13.sp)
            }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            loading = state.loadingAction == "password",
            onDismiss = { if (state.loadingAction == null) showPasswordDialog = false },
            onConfirm = { current, next ->
                onChangePassword(current, next)
                showPasswordDialog = false
            }
        )
    }
    if (showOtherSessionsDialog) {
        ConfirmationDialog(
            title = "退出其他设备？",
            message = "其他设备需要重新输入账号和密码，本机不会退出。",
            confirmText = "确认退出",
            onDismiss = { showOtherSessionsDialog = false },
            onConfirm = {
                showOtherSessionsDialog = false
                onLogoutOtherSessions()
            }
        )
    }
    if (showLogoutDialog) {
        ConfirmationDialog(
            title = "退出当前账号？",
            message = "退出后需要重新输入账号和密码。正在房间中时不会允许直接退出。",
            confirmText = "退出登录",
            danger = true,
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            }
        )
    }
}

@Composable
private fun AccountAction(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean,
    loading: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(68.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(
                title,
                color = if (danger) Color(0xFFF28B78) else Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(subtitle, color = AccountMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val error = when {
        next.isNotEmpty() && next.length < 6 -> "新密码至少需要 6 个字符"
        confirm.isNotEmpty() && next != confirm -> "两次输入的新密码不一致"
        current.isNotEmpty() && current == next -> "新密码不能与当前密码相同"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                PasswordField("当前密码", current) { current = it.take(64) }
                Spacer(Modifier.height(8.dp))
                PasswordField("新密码", next) { next = it.take(64) }
                Spacer(Modifier.height(8.dp))
                PasswordField("再次输入新密码", confirm) { confirm = it.take(64) }
                error?.let {
                    Spacer(Modifier.height(7.dp))
                    Text(it, color = Color(0xFFF28B78), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(current, next) },
                enabled = !loading && current.length >= 6 && next.length >= 6 &&
                    next == confirm && current != next
            ) {
                Text("确认修改")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (danger) Color(0xFFB64E43) else AccountCoral
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
