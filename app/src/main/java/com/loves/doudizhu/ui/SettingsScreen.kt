package com.loves.doudizhu.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loves.doudizhu.R

private val PageBg = Color(0xFF091F27)
private val SurfaceBg = Color(0xFF12333B)
private val Muted = Color(0xFF9DB8BC)
private val Coral = Color(0xFFE97D58)
private val Wax = Color(0xFFF2D675)

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(PageBg),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.brand_splash_landscape),
            contentDescription = "好友斗地主",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Surface(
            color = Color(0xCC091F27),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                text = "连接服务器中…",
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun HomeScreen(
    state: GameUiState.Home,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onHistory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().background(PageBg).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.weight(0.9f).fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text("好友斗地主", color = Wax, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "三个人，马上开一局。",
                color = Color.White,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "创建六位房号，选择 AI 数量，再发给朋友加入。",
                color = Muted,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier.height(52.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Coral
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("创建房间", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onJoin, modifier = Modifier.height(52.dp)) {
                    Text("加入房间", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionDot(state.connection)
                Spacer(Modifier.width(8.dp))
                Text(connectionText(state.connection), color = Muted, fontSize = 13.sp)
            }
        }
        Surface(
            modifier = Modifier.weight(1.1f).fillMaxSize(),
            color = Color(0xFF1B4A4C),
            shape = RoundedCornerShape(28.dp)
        ) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(R.drawable.table_table_felt),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(18.dp),
                    color = Color(0xDD102F37),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "熟人局 · 服务器裁判",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp
                    )
                }
                Image(
                    painter = painterResource(R.drawable.brand_landlord_badge),
                    contentDescription = "地主标记",
                    modifier = Modifier.align(Alignment.Center).size(76.dp)
                )
            }
        }
        Column(
            modifier = Modifier.width(52.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            IconButton(onClick = onHistory, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.History, contentDescription = "最近战绩", tint = Color.White)
            }
            Text("战绩", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun CreateRoomScreen(
    initialNickname: String,
    initialServerUrl: String,
    initialAiCount: Int,
    onBack: () -> Unit,
    onCreate: (nickname: String, serverUrl: String, aiCount: Int) -> Unit
) {
    RoomForm(
        title = "创建房间",
        subtitle = "发起一桌只属于朋友的牌局。",
        initialNickname = initialNickname,
        initialServerUrl = initialServerUrl,
        initialAiCount = initialAiCount,
        roomCode = null,
        onBack = onBack,
        onSubmit = { nickname, url, _, aiCount -> onCreate(nickname, url, aiCount) }
    )
}

@Composable
fun JoinRoomScreen(
    initialNickname: String,
    initialServerUrl: String,
    onBack: () -> Unit,
    onJoin: (room: String, nickname: String, serverUrl: String) -> Unit
) {
    RoomForm(
        title = "加入房间",
        subtitle = "输入朋友发来的六位房号。",
        initialNickname = initialNickname,
        initialServerUrl = initialServerUrl,
        initialAiCount = 2,
        roomCode = "",
        onBack = onBack,
        onSubmit = { nickname, url, room, _ -> onJoin(room, nickname, url) }
    )
}

@Composable
private fun RoomForm(
    title: String,
    subtitle: String,
    initialNickname: String,
    initialServerUrl: String,
    initialAiCount: Int,
    roomCode: String?,
    onBack: () -> Unit,
    onSubmit: (nickname: String, url: String, room: String, aiCount: Int) -> Unit
) {
    var nickname by remember { mutableStateOf(initialNickname) }
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var room by remember { mutableStateOf(roomCode.orEmpty()) }
    var aiCount by remember { mutableStateOf(initialAiCount.coerceIn(0, 2)) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(PageBg).padding(24.dp),
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
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it.take(12) },
                        label = { Text("昵称") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            "AI 数量",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 1, 2).forEach { option ->
                                val selected = aiCount == option
                                OutlinedButton(
                                    onClick = {
                                        aiCount = option
                                        error = null
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) Coral else Color.Transparent,
                                        contentColor = if (selected) Color.White else Muted
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("$option 个", fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "需要 ${3 - aiCount} 名真人玩家才能开始",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                    if (error != null) {
                        Text(error.orEmpty(), color = Color(0xFFF28B78), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val cleanName = nickname.trim()
                            if (cleanName.isBlank()) {
                                error = "请先填写昵称"
                            } else if (roomCode != null && room.length != 6) {
                                error = "请输入六位数字房号"
                            } else {
                                error = null
                                onSubmit(cleanName, serverUrl, room, aiCount)
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
    AppConnectionState.OFFLINE -> "尚未连接"
}
