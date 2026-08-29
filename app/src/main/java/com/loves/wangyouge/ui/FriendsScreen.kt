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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
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
    onRemove: (String) -> Unit
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
            Tab(selectedTab == 0, { selectedTab = 0 }, text = { Text("我的好友") })
            Tab(selectedTab == 1, { selectedTab = 1 }, text = { Text("好友申请") })
            Tab(selectedTab == 2, { selectedTab = 2 }, text = { Text("添加好友") })
        }
        state.error?.let {
            Text(it, color = Color(0xFFF28B78), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp))
        }
        when {
            state.loading && state.friends.isEmpty() && state.requests.isEmpty() && state.searchResults.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FriendsCoral)
                }
            }
            selectedTab == 0 -> {
                FriendList(
                    friends = state.friends,
                    emptyText = "还没有好友，去添加一位朋友吧",
                    onRemove = { removeTarget = it }
                )
            }
            selectedTab == 1 -> {
                RequestList(state.requests, state.actionInFlight, onAccept, onReject)
            }
            else -> {
                AddFriendPanel(
                    query = query,
                    results = state.searchResults,
                    loading = state.loading,
                    actionInFlight = state.actionInFlight,
                    onQueryChange = { query = it.take(24) },
                    onSearch = { onSearch(query.trim()) },
                    onSendRequest = onSendRequest
                )
            }
        }
    }

    removeTarget?.let { target ->
        androidx.compose.material3.AlertDialog(
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
    emptyText: String,
    onRemove: (FriendProfile) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(friends, key = { it.accountId }) { friend ->
            Surface(color = FriendsPanel, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).background(
                            if (friend.online) Color(0xFF7ED9A3) else Color(0xFF60777B),
                            androidx.compose.foundation.shape.CircleShape
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(friend.nickname, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (friend.online) "在线，可邀请加入房间" else "离线", color = FriendsMuted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onRemove(friend) }) {
                        Icon(Icons.Default.DeleteOutline, "删除好友", tint = FriendsMuted)
                    }
                }
            }
        }
        if (friends.isEmpty()) {
            item { Text(emptyText, color = FriendsMuted, modifier = Modifier.padding(20.dp)) }
        }
    }
}

@Composable
private fun RequestList(
    requests: List<FriendRequest>,
    actionInFlight: Boolean,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
        if (requests.isEmpty()) {
            item { Text("暂时没有好友申请", color = FriendsMuted, modifier = Modifier.padding(20.dp)) }
        }
    }
}

@Composable
private fun AddFriendPanel(
    query: String,
    results: List<FriendProfile>,
    loading: Boolean,
    actionInFlight: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSendRequest: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                Text("搜索")
            }
        }
        Text("至少输入 2 个字符，只会显示可添加的账号。", color = FriendsMuted, fontSize = 12.sp)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.accountId }) { account ->
                Surface(color = FriendsPanel, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(account.nickname, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("账号名匹配", color = FriendsMuted, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onSendRequest(account.accountId) },
                            enabled = !actionInFlight
                        ) { Text("加好友") }
                    }
                }
            }
        }
    }
}
