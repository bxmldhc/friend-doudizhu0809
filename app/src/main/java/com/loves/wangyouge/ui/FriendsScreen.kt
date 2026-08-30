package com.loves.wangyouge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loves.wangyouge.network.FriendProfile
import com.loves.wangyouge.network.FriendRequest
import com.loves.wangyouge.network.RoomInvitation

private val FriendsPage = Color(0xFF091F27)
private val FriendsPanel = Color(0xFF12333B)
private val FriendsMuted = Color(0xFF9DB8BC)
private val FriendsCoral = Color(0xFFE97D58)
private val FriendsWax = Color(0xFFF2D675)

@Composable
fun FriendsScreen(
    state: GameUiState.Friends,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAcceptRoomInvitation: (String) -> Unit,
    onDismissRoomInvitation: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember(state.query) { mutableStateOf(state.query) }
    var removeTarget by remember { mutableStateOf<FriendProfile?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(FriendsPage),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("好友", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("和朋友一起开局", color = FriendsMuted, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, "刷新", tint = Color.White)
            }
        }
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = FriendsPanel,
            contentColor = FriendsWax
        ) {
            Tab(selectedTab == 0, { selectedTab = 0 }, text = { Text("好友") })
            Tab(selectedTab == 1, { selectedTab = 1 }, text = { Text("收到的申请") })
            Tab(selectedTab == 2, { selectedTab = 2 }, text = { Text("发出的申请") })
            Tab(selectedTab == 3, { selectedTab = 3 }, text = { Text("房间邀请") })
        }
        if (state.loading && state.friends.isEmpty() && state.requests.isEmpty() &&
            state.searchResults.isEmpty() && state.roomInvitations.isEmpty()
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FriendsCoral)
            }
        } else {
            when (selectedTab) {
                0 -> FriendList(
                    friends = state.friends,
                    onRemove = { removeTarget = it },
                    query = query,
                    searchResults = state.searchResults,
                    loading = state.loading,
                    actionInFlight = state.actionInFlight,
                    onQueryChange = { query = it.take(24) },
                    onSearch = { onSearch(query.trim()) },
                    onSendRequest = onSendRequest,
                    error = state.error
                )
                1 -> RequestList(
                    requests = state.requests.filter { it.direction == "INCOMING" },
                    emptyText = "暂时没有收到的好友申请",
                    actionInFlight = state.actionInFlight,
                    onAccept = onAccept,
                    onReject = onReject,
                    error = state.error
                )
                2 -> RequestList(
                    requests = state.requests.filter { it.direction == "OUTGOING" },
                    emptyText = "暂时没有发出的好友申请",
                    actionInFlight = state.actionInFlight,
                    onAccept = onAccept,
                    onReject = onReject,
                    error = state.error
                )
                else -> RoomInvitationList(
                    invitations = state.roomInvitations,
                    actionInFlight = state.actionInFlight,
                    onAccept = onAcceptRoomInvitation,
                    onDismiss = onDismissRoomInvitation,
                    error = state.error
                )
            }
        }
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("删除好友？") },
            text = { Text("删除后需要重新发送好友申请。") },
            confirmButton = {
                Button(onClick = {
                    removeTarget = null
                    onRemove(target.accountId)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun FriendList(
    friends: List<FriendProfile>,
    onRemove: (FriendProfile) -> Unit,
    query: String,
    searchResults: List<FriendProfile>,
    loading: Boolean,
    actionInFlight: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSendRequest: (String) -> Unit,
    error: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!error.isNullOrBlank()) item { FriendError(error) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("输入账号名") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSearch, enabled = !loading && query.trim().length >= 2) {
                    Icon(Icons.Default.PersonAdd, null)
                    Spacer(Modifier.width(4.dp))
                    Text("添加")
                }
            }
        }
        if (searchResults.isNotEmpty()) {
            item { Text("搜索结果", color = FriendsWax, fontWeight = FontWeight.SemiBold) }
            items(searchResults, key = { "search-${it.accountId}" }) { account ->
                Surface(color = FriendsPanel, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(account.nickname, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("账号匹配", color = FriendsMuted, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onSendRequest(account.accountId) },
                            enabled = !actionInFlight
                        ) { Text("发送申请") }
                    }
                }
            }
        }
        item {
            Text(
                "我的好友 ${friends.size}",
                color = FriendsWax,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        items(friends, key = { it.accountId }) { friend ->
            Surface(color = FriendsPanel, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).background(
                            if (friend.online) Color(0xFF7ED9A3) else Color(0xFF60777B),
                            CircleShape
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(friend.nickname, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            if (friend.online) "在线，可邀请加入房间" else "离线",
                            color = FriendsMuted,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { onRemove(friend) }) {
                        Icon(Icons.Default.DeleteOutline, "删除好友", tint = FriendsMuted)
                    }
                }
            }
        }
        if (friends.isEmpty()) {
            item {
                Text(
                    "还没有好友，搜索账号名添加一位朋友吧。",
                    color = FriendsMuted,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RequestList(
    requests: List<FriendRequest>,
    emptyText: String,
    actionInFlight: Boolean,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    error: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!error.isNullOrBlank()) item { FriendError(error) }
        items(requests, key = { it.requestId }) { request ->
            Surface(color = FriendsPanel, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(request.account.nickname, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            if (request.direction == "INCOMING") "请求添加你为好友" else "等待对方处理",
                            color = FriendsMuted,
                            fontSize = 12.sp
                        )
                    }
                    if (request.direction == "INCOMING") {
                        OutlinedButton(
                            onClick = { onReject(request.requestId) },
                            enabled = !actionInFlight,
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) { Text("忽略") }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { onAccept(request.requestId) },
                            enabled = !actionInFlight,
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) { Text("同意") }
                    }
                }
            }
        }
        if (requests.isEmpty()) item { Text(emptyText, color = FriendsMuted, modifier = Modifier.padding(20.dp)) }
    }
}

@Composable
private fun RoomInvitationList(
    invitations: List<RoomInvitation>,
    actionInFlight: Boolean,
    onAccept: (String) -> Unit,
    onDismiss: (String) -> Unit,
    error: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!error.isNullOrBlank()) item { FriendError(error) }
        items(invitations, key = { it.invitationId }) { invitation ->
            Surface(color = FriendsPanel, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        "${invitation.inviter.nickname} 邀请你加入${if (invitation.gameType == "GOMOKU") "五子棋" else "斗地主"}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("房间号：${invitation.roomCode}", color = FriendsMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onDismiss(invitation.invitationId) },
                            enabled = !actionInFlight
                        ) { Text("忽略") }
                        Button(
                            onClick = { onAccept(invitation.invitationId) },
                            enabled = !actionInFlight
                        ) { Text("接受并加入") }
                    }
                }
            }
        }
        if (invitations.isEmpty()) {
            item {
                Text("暂时没有房间邀请", color = FriendsMuted, modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
private fun FriendError(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF482B2D),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            message,
            color = Color(0xFFF28B78),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}
