package com.loves.wangyouge.network

import com.loves.wangyouge.logic.EngineState
import com.loves.wangyouge.model.AiDifficulty
import com.loves.wangyouge.model.GomokuState
import com.loves.wangyouge.model.RemoteGameType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnectionState {
    CONNECTING,
    OPEN,
    RECONNECTING,
    CLOSED,
    FAILED
}

class OnlineClient(
    serverUrl: String,
    private val name: String,
    private val playerId: String,
    private val authToken: String?,
    private val onRoom: (
        roomCode: String,
        yourSeat: Int,
        hostSeat: Int,
        resumeToken: String?,
        players: List<String>,
        aiCount: Int,
        requiredHumans: Int,
        difficulty: AiDifficulty,
        actionTimeoutSeconds: Int,
        gameType: RemoteGameType
    ) -> Unit,
    private val onState: (EngineState) -> Unit,
    private val onGomokuState: (GomokuState) -> Unit,
    private val onRoomDisbanded: (String) -> Unit,
    private val onQuickMessage: (seat: Int, messageId: String, at: Long) -> Unit,
    private val onItemThrown: (fromSeat: Int, targetSeat: Int, item: String, at: Long) -> Unit,
    private val onConnection: (ConnectionState) -> Unit,
    private val onServerError: (ServerError) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val request = Request.Builder().url(serverUrl).build()
    private var webSocket: WebSocket? = null
    private var closedByUser = false
    private var hasOpened = false
    private var lastConnectionState: ConnectionState? = null
    private val terminalCallbackSent = AtomicBoolean(false)

    fun connect(onOpen: () -> Unit = {}) {
        closedByUser = false
        hasOpened = false
        terminalCallbackSent.set(false)
        emitConnection(ConnectionState.CONNECTING)
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (closedByUser || webSocket !== this@OnlineClient.webSocket) return
                    hasOpened = true
                    emitConnection(ConnectionState.OPEN)
                    onOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (closedByUser || webSocket !== this@OnlineClient.webSocket) return
                    try {
                        when (val msg = GameProtocol.parseMessage(text)) {
                            is NetMessage.Room -> onRoom(
                                msg.roomCode,
                                msg.yourSeat,
                                msg.hostSeat,
                                msg.resumeToken,
                                msg.players,
                                msg.aiCount,
                                msg.requiredHumans,
                                msg.difficulty,
                                msg.actionTimeoutSeconds,
                                msg.gameType
                            )
                            is NetMessage.State -> onState(msg.state)
                            is NetMessage.GomokuState -> onGomokuState(msg.state)
                            is NetMessage.RoomDisbanded -> onRoomDisbanded(msg.reason)
                            is NetMessage.QuickMessage -> onQuickMessage(
                                msg.seat,
                                msg.messageId,
                                msg.at
                            )
                            is NetMessage.ItemThrown -> onItemThrown(
                                msg.fromSeat,
                                msg.targetSeat,
                                msg.item,
                                msg.at
                            )
                            is NetMessage.Error -> onServerError(msg.error)
                            NetMessage.Pong -> Unit
                        }
                    } catch (error: Exception) {
                        onServerError(
                            ServerError(
                                code = "MESSAGE_PARSE_FAILED",
                                message = error.message ?: "服务器消息解析失败"
                            )
                        )
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closedByUser && webSocket === this@OnlineClient.webSocket) {
                        emitConnection(ConnectionState.RECONNECTING)
                    }
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (
                        !closedByUser &&
                        webSocket === this@OnlineClient.webSocket &&
                        terminalCallbackSent.compareAndSet(false, true)
                    ) {
                        emitConnection(ConnectionState.CLOSED)
                    }
                }

                override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                    if (
                        !closedByUser &&
                        webSocket === this@OnlineClient.webSocket &&
                        terminalCallbackSent.compareAndSet(false, true)
                    ) {
                        emitConnection(
                            if (hasOpened) ConnectionState.RECONNECTING else ConnectionState.FAILED
                        )
                        onServerError(
                            ServerError(
                                code = "CONNECTION_FAILED",
                                message = error.message ?: "连接服务器失败"
                            )
                        )
                    }
                }
            }
        )
    }

    fun createRoom(
        difficulty: AiDifficulty,
        aiCount: Int,
        actionTimeoutSeconds: Int,
        gameType: RemoteGameType = RemoteGameType.DOUDIZHU
    ) {
        webSocket?.send(
            GameProtocol.encodeCreate(
                name,
                playerId,
                authToken,
                difficulty,
                aiCount,
                actionTimeoutSeconds,
                gameType
            )
        )
    }

    fun joinRoom(room: String, gameType: RemoteGameType = RemoteGameType.DOUDIZHU) {
        webSocket?.send(GameProtocol.encodeJoin(room, name, playerId, authToken, gameType))
    }

    fun reconnectRoom(room: String, token: String) {
        webSocket?.send(GameProtocol.encodeReconnect(room, token, authToken))
    }

    fun startGame() {
        webSocket?.send(GameProtocol.encodeStart())
    }

    fun restartGame() {
        webSocket?.send(GameProtocol.encodeRestart())
    }

    fun setReady(ready: Boolean) {
        webSocket?.send(GameProtocol.encodeReady(ready))
    }

    fun leaveRoom() {
        webSocket?.send(GameProtocol.encodeLeave())
    }

    fun disbandRoom() {
        webSocket?.send(GameProtocol.encodeDisband())
    }

    fun sendQuickMessage(messageId: String) {
        webSocket?.send(GameProtocol.encodeQuickMessage(messageId))
    }

    fun sendThrowItem(targetSeat: Int, item: String) {
        webSocket?.send(GameProtocol.encodeThrowItem(targetSeat, item))
    }

    fun sendAction(seat: Int, action: RemoteAction) {
        webSocket?.send(GameProtocol.encodeAction(seat, action))
    }

    fun sendGomokuMove(row: Int, col: Int) {
        webSocket?.send(GameProtocol.encodeGomokuMove(row, col))
    }

    fun close() {
        closedByUser = true
        terminalCallbackSent.set(true)
        webSocket?.close(1000, "bye")
        webSocket = null
    }

    private fun emitConnection(state: ConnectionState) {
        if (lastConnectionState == state) return
        lastConnectionState = state
        onConnection(state)
    }
}
