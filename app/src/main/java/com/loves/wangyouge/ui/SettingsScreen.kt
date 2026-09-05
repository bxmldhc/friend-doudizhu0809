package com.loves.wangyouge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loves.wangyouge.R
import com.loves.wangyouge.model.AiDifficulty

private val PageBg = Color(0xFF091F27)
private val SurfaceBg = Color(0xFF12333B)
private val Muted = Color(0xFF9DB8BC)
private val Coral = Color(0xFFE97D58)
private val Wax = Color(0xFFF2D675)

@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize().background(PageBg)) {
        Image(
            painter = painterResource(R.drawable.wangyouge_launch_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.82f),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "忘忧阁",
                color = Wax,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "朋友一起，轻松开局",
                color = Color(0xFFD4E8E4),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Coral,
                strokeWidth = 3.dp
            )
            Text("连接服务器中…", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun HomeScreen(
    state: GameUiState.Home,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onCreateGomoku: () -> Unit,
    onJoinGomoku: (String) -> Unit,
    onOpen2048: () -> Unit,
    onOpenLoveLetter: () -> Unit,
    onHistory: () -> Unit,
    onFriends: () -> Unit,
    onResume: () -> Unit,
    onAdmin: () -> Unit,
    onAccount: () -> Unit,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(PageBg)) {
        Image(
            painter = painterResource(R.drawable.wangyouge_lobby_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.5f),
            contentScale = ContentScale.Crop
        )
        val compact = maxHeight < 500.dp || maxWidth < 1_000.dp
        val pagePadding = if (compact) 14.dp else 24.dp
        val sectionGap = if (compact) 12.dp else 20.dp
        val sidebarWidth = if (compact) 168.dp else 212.dp
        val toolbarWidth = if (compact) 64.dp else 76.dp
        var selectedGame by remember { mutableStateOf(HomeGame.DOUDIZHU) }
        var gomokuRoomCode by remember { mutableStateOf("") }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(pagePadding),
            horizontalArrangement = Arrangement.spacedBy(sectionGap)
        ) {
            Column(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(
                            if (state.isAdmin) R.drawable.wangyouge_avatar_admin
                            else R.drawable.wangyouge_avatar_default
                        ),
                        contentDescription = "当前账号头像",
                        modifier = Modifier.size(if (compact) 42.dp else 52.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.nickname.ifBlank { "未登录" },
                            color = Color.White,
                            fontSize = if (compact) 16.sp else 20.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text("好友小游戏", color = Muted, fontSize = 11.sp)
                    }
                }
                HomeInfoRow(
                    iconRes = when (state.connection) {
                        AppConnectionState.ONLINE -> R.drawable.wangyouge_status_online
                        AppConnectionState.OFFLINE -> R.drawable.wangyouge_status_offline
                        AppConnectionState.CONNECTING -> R.drawable.wangyouge_status_connecting
                        AppConnectionState.RECONNECTING -> R.drawable.wangyouge_status_reconnect
                    },
                    title = connectionText(state.connection),
                    subtitle = "服务器连接状态"
                )
                Spacer(Modifier.height(if (compact) 0.dp else 4.dp))
                HomeCompactAction(
                    icon = { Icon(Icons.Default.History, null, tint = Color.White) },
                    title = "最近战绩",
                    subtitle = "查看最近十场记录",
                    onClick = onHistory
                )
                HomeCompactAction(
                    icon = { Icon(Icons.Default.Person, null, tint = Color.White) },
                    title = "好友",
                    subtitle = "查看好友与申请",
                    onClick = onFriends
                )
                HomeCompactAction(
                    icon = { Icon(Icons.Default.ManageAccounts, null, tint = Color.White) },
                    title = "账号设置",
                    subtitle = "账号与登录信息",
                    onClick = onAccount
                )
                HomeCompactAction(
                    icon = {
                        Icon(
                            if (state.voiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            null,
                            tint = if (state.voiceEnabled) Wax else Muted
                        )
                    },
                    title = if (state.voiceEnabled) "游戏语音已开" else "游戏语音已关",
                    subtitle = "点击切换",
                    onClick = { onVoiceEnabledChange(!state.voiceEnabled) }
                )
                if (state.isAdmin) {
                    HomeCompactAction(
                        icon = { Icon(Icons.Default.AdminPanelSettings, null, tint = Wax) },
                        title = "超级管理入口",
                        subtitle = "账号与房间管理",
                        onClick = onAdmin
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = Color(0xE612343B),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(if (compact) 16.dp else 22.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                ) {
                    HomeGameHero(
                        selectedGame = selectedGame,
                        compact = compact,
                        roomCode = gomokuRoomCode,
                        onRoomCodeChange = { gomokuRoomCode = it.filter(Char::isDigit).take(6) },
                        onCreateDoudizhu = onCreate,
                        onJoinDoudizhu = onJoin,
                        onResumeDoudizhu = onResume,
                        resumeRoomCode = state.resumeRoomCode,
                        resumeSavedAt = state.resumeSavedAt,
                        onCreateGomoku = onCreateGomoku,
                        onJoinGomoku = { onJoinGomoku(gomokuRoomCode) },
                        onOpen2048 = onOpen2048
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (selectedGame != HomeGame.DOUDIZHU) {
                            HomeGameTile(
                                modifier = Modifier.width(if (compact) 112.dp else 138.dp),
                                imageRes = R.drawable.wangyouge_game_doudizhu,
                                title = "斗地主",
                                onClick = { selectedGame = HomeGame.DOUDIZHU }
                            )
                        }
                        if (selectedGame != HomeGame.GOMOKU) {
                            HomeGameTile(
                                modifier = Modifier.width(if (compact) 112.dp else 138.dp),
                                imageRes = R.drawable.wangyouge_game_gomoku,
                                title = "五子棋",
                                onClick = { selectedGame = HomeGame.GOMOKU }
                            )
                        }
                        if (selectedGame != HomeGame.GAME_2048) {
                            HomeGameTile(
                                modifier = Modifier.width(if (compact) 112.dp else 138.dp),
                                imageRes = R.drawable.wangyouge_game_more,
                                title = "2048",
                                onClick = { selectedGame = HomeGame.GAME_2048 }
                            )
                        }
                        HomeGameTile(
                            modifier = Modifier.width(if (compact) 112.dp else 138.dp),
                            imageRes = R.drawable.wangyouge_game_more,
                            title = "情书",
                            onClick = onOpenLoveLetter
                        )
                        HomeGameTile(
                            modifier = Modifier.width(if (compact) 112.dp else 138.dp),
                            imageRes = R.drawable.wangyouge_game_more,
                            title = "更多小游戏",
                            enabled = false,
                            onClick = {}
                        )
                        HomeGameTile(
                            modifier = Modifier.width(if (compact) 112.dp else 138.dp),
                            imageRes = R.drawable.wangyouge_game_more,
                            title = "敬请期待",
                            enabled = false,
                            onClick = {}
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .width(toolbarWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 22.dp)
            ) {
                HomeNavigationItem(
                    label = "战绩",
                    onClick = onHistory
                ) {
                    Icon(Icons.Default.History, contentDescription = "最近战绩", tint = Color.White)
                }
                HomeNavigationItem(
                    label = "检查更新",
                    onClick = onCheckUpdate
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "检查更新", tint = Color.White)
                }
            }
        }
    }
}

private enum class HomeGame {
    DOUDIZHU,
    GOMOKU,
    GAME_2048,
    LOVE_LETTER
}

@Composable
private fun HomeGameHero(
    selectedGame: HomeGame,
    compact: Boolean,
    roomCode: String,
    onRoomCodeChange: (String) -> Unit,
    onCreateDoudizhu: () -> Unit,
    onJoinDoudizhu: () -> Unit,
    onResumeDoudizhu: () -> Unit,
    resumeRoomCode: String?,
    resumeSavedAt: Long?,
    onCreateGomoku: () -> Unit,
    onJoinGomoku: () -> Unit,
    onOpen2048: () -> Unit
) {
    val isDoudizhu = selectedGame == HomeGame.DOUDIZHU
    val isGomoku = selectedGame == HomeGame.GOMOKU
    val title = when (selectedGame) { HomeGame.DOUDIZHU -> "好友斗地主"; HomeGame.GOMOKU -> "五子棋"; HomeGame.GAME_2048 -> "2048"; HomeGame.LOVE_LETTER -> "情书" }
    val detail = when (selectedGame) { HomeGame.DOUDIZHU -> "三人联机，支持 AI 补位"; HomeGame.GOMOKU -> "双人联机，黑方先行"; HomeGame.GAME_2048 -> "单人游戏，挑战最高分"; HomeGame.LOVE_LETTER -> "经典 16 张牌，支持 AI 补位" }
    val imageRes = when (selectedGame) { HomeGame.DOUDIZHU -> R.drawable.wangyouge_game_doudizhu; HomeGame.GOMOKU -> R.drawable.wangyouge_game_gomoku; HomeGame.GAME_2048, HomeGame.LOVE_LETTER -> R.drawable.wangyouge_game_more }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xEA16434A),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 16.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isDoudizhu) "三人牌局" else "双人对弈",
                        color = Color(0xFF7ED9A3),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        title,
                        color = Color.White,
                        fontSize = if (compact) 25.sp else 32.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(detail, color = Color(0xFFD4E8E4), fontSize = 12.sp)
                }
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = title,
                    modifier = Modifier
                        .size(if (compact) 92.dp else 118.dp)
                        .padding(start = 8.dp),
                    contentScale = ContentScale.Crop
                )
            }
            if (isDoudizhu) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onCreateDoudizhu,
                        modifier = Modifier.height(50.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("创建房间", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onJoinDoudizhu, modifier = Modifier.height(50.dp)) {
                        Icon(Icons.Default.Link, null)
                        Spacer(Modifier.width(6.dp))
                        Text("加入房间", fontWeight = FontWeight.Bold)
                    }
                }
                resumeRoomCode?.let { room ->
                    OutlinedButton(
                        onClick = onResumeDoudizhu,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Wax)
                    ) {
                        Icon(Icons.Default.Restore, null)
                        Spacer(Modifier.width(6.dp))
                        Text("继续房间 $room", fontWeight = FontWeight.Bold)
                        resumeSavedAt?.let {
                            Spacer(Modifier.width(6.dp))
                            Text("· ${resumeTimeDescription(it)}", fontSize = 11.sp)
                        }
                    }
                }
            } else if (isGomoku) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onCreateGomoku,
                        modifier = Modifier.height(50.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("创建房间", fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = onRoomCodeChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("六位房号") },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = onJoinGomoku,
                        enabled = roomCode.length == 6,
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("加入")
                    }
                }
            } else {
                Button(
                    onClick = onOpen2048,
                    modifier = Modifier.height(50.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("开始游戏", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HomeInfoRow(
    iconRes: Int,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HomeCompactAction(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF102E36),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
        }
    }
}

@Composable
private fun HomeGameTile(
    modifier: Modifier,
    imageRes: Int,
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 92.dp),
        color = SurfaceBg,
        shape = RoundedCornerShape(8.dp),
        border = null
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                color = if (enabled) Color.White else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun connectionColor(connection: AppConnectionState): Color = when (connection) {
    AppConnectionState.ONLINE -> Color(0xFF7ED9A3)
    AppConnectionState.RECONNECTING, AppConnectionState.CONNECTING -> Wax
    AppConnectionState.OFFLINE -> Color(0xFFE97870)
}

@Composable
private fun HomeNavigationItem(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            icon()
        }
        Text(label, color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

private fun resumeTimeDescription(savedAt: Long): String {
    val elapsed = (System.currentTimeMillis() - savedAt).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    return when {
        elapsed < minute -> "刚刚保存的牌局"
        elapsed < hour -> "${elapsed / minute} 分钟前保存"
        elapsed < day -> "${elapsed / hour} 小时前保存"
        elapsed < 7L * day -> "${elapsed / day} 天前保存"
        else -> "较早保存的牌局"
    }
}

@Composable
fun AuthScreen(
    state: GameUiState.Auth,
    onSubmit: (register: Boolean, nickname: String, password: String, serverUrl: String) -> Unit
) {
    var register by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(0.9f)) {
            Text("忘忧阁", color = Wax, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (register) "创建你的账号" else "欢迎回来",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                state.pendingRoom?.let { "登录后将直接加入房间 $it" }
                    ?: "昵称是唯一账号名，也会显示在牌桌和累计战绩中。",
                color = if (state.pendingRoom == null) Muted else Wax,
                fontSize = 15.sp
            )
        }
        Surface(
            modifier = Modifier.weight(1.1f),
            color = SurfaceBg,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            register = false
                            confirmPassword = ""
                            localError = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = if (!register) Coral else Color.Transparent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("登录")
                    }
                    OutlinedButton(
                        onClick = {
                            register = true
                            localError = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = if (register) Coral else Color.Transparent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("注册")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(12) },
                    label = { Text("昵称 / 账号名") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it.take(64)
                        localError = null
                    },
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                )
                if (register) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it.take(64)
                            localError = null
                        },
                        label = { Text("再次输入密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                )
                (localError ?: state.error)?.let {
                    Spacer(Modifier.height(7.dp))
                    Text(it, color = Color(0xFFF28B78), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (register && password != confirmPassword) {
                            localError = "两次输入的密码不一致"
                        } else {
                            localError = null
                            onSubmit(register, nickname, password, serverUrl)
                        }
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Coral
                    )
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (register) "注册并继续" else "登录并继续", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateRoomScreen(
    nickname: String,
    initialServerUrl: String,
    initialAiCount: Int,
    initialDifficulty: AiDifficulty,
    initialActionTimeoutSeconds: Int,
    onBack: () -> Unit,
    onCreate: (
        serverUrl: String,
        aiCount: Int,
        difficulty: AiDifficulty,
        actionTimeoutSeconds: Int
    ) -> Unit
) {
    RoomForm(
        title = "创建房间",
        subtitle = "发起一桌只属于朋友的牌局。",
        nickname = nickname,
        initialServerUrl = initialServerUrl,
        initialAiCount = initialAiCount,
        initialDifficulty = initialDifficulty,
        initialActionTimeoutSeconds = initialActionTimeoutSeconds,
        roomCode = null,
        onBack = onBack,
        onSubmit = { url, _, aiCount, difficulty, timeout ->
            onCreate(url, aiCount, difficulty, timeout)
        }
    )
}

@Composable
fun JoinRoomScreen(
    nickname: String,
    initialServerUrl: String,
    initialRoom: String,
    onBack: () -> Unit,
    onJoin: (room: String, serverUrl: String) -> Unit
) {
    RoomForm(
        title = "加入房间",
        subtitle = "输入朋友发来的六位房号。",
        nickname = nickname,
        initialServerUrl = initialServerUrl,
        initialAiCount = 2,
        initialDifficulty = AiDifficulty.MEDIUM,
        initialActionTimeoutSeconds = 20,
        roomCode = initialRoom,
        onBack = onBack,
        onSubmit = { url, room, _, _, _ -> onJoin(room, url) }
    )
}

@Composable
private fun RoomForm(
    title: String,
    subtitle: String,
    nickname: String,
    initialServerUrl: String,
    initialAiCount: Int,
    initialDifficulty: AiDifficulty,
    initialActionTimeoutSeconds: Int,
    roomCode: String?,
    onBack: () -> Unit,
    onSubmit: (
        url: String,
        room: String,
        aiCount: Int,
        difficulty: AiDifficulty,
        actionTimeoutSeconds: Int
    ) -> Unit
) {
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var room by remember(roomCode) { mutableStateOf(roomCode.orEmpty()) }
    var aiCount by remember { mutableStateOf(initialAiCount.coerceIn(0, 2)) }
    var difficulty by remember { mutableStateOf(initialDifficulty) }
    var actionTimeoutSeconds by remember {
        mutableStateOf(
            initialActionTimeoutSeconds.takeIf { it in setOf(15, 20, 30, 45) } ?: 20
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
            Column {
                Text(title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceBg,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("玩家账号", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(nickname, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("服务器地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(modifier = Modifier.weight(0.8f)) {
                    if (roomCode != null) {
                        OutlinedTextField(
                            value = room,
                            onValueChange = {
                                room = it.filter(Char::isDigit).take(6)
                                error = null
                            },
                            label = { Text("六位房号") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                letterSpacing = 5.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text("服务器生成六位房号", color = Muted, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "等待区最多可加入 3 名真人，开局时空位自动补入 AI",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "操作限时",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            listOf(15, 20, 30, 45).forEach { option ->
                                val selected = actionTimeoutSeconds == option
                                OutlinedButton(
                                    onClick = { actionTimeoutSeconds = option },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) Coral else Color.Transparent,
                                        contentColor = if (selected) Color.White else Muted
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("$option 秒", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    if (error != null) {
                        Text(error.orEmpty(), color = Color(0xFFF28B78), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (roomCode != null && room.length != 6) {
                                error = "请输入六位数字房号"
                            } else {
                                error = null
                                onSubmit(
                                    serverUrl,
                                    room,
                                    aiCount,
                                    difficulty,
                                    actionTimeoutSeconds
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Coral
                        )
                    ) {
                        Text(if (roomCode == null) "创建并等待朋友" else "进入房间", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionDot(state: AppConnectionState) {
    Box(
        modifier = Modifier.size(9.dp).background(
            color = when (state) {
                AppConnectionState.ONLINE -> Color(0xFF78D6A1)
                AppConnectionState.CONNECTING, AppConnectionState.RECONNECTING -> Wax
                AppConnectionState.OFFLINE -> Color(0xFF84969A)
            },
            shape = RoundedCornerShape(50)
        )
    )
}

private fun connectionText(state: AppConnectionState): String = when (state) {
    AppConnectionState.ONLINE -> "服务器在线"
    AppConnectionState.CONNECTING -> "正在连接服务器"
    AppConnectionState.RECONNECTING -> "正在重新连接"
    AppConnectionState.OFFLINE -> "未进入房间"
}
