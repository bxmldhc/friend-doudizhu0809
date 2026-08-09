package com.loves.doudizhu.network

import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.model.AiDifficulty
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

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
    private val onRoom: (
        roomCode: String,
        yourSeat: Int,
        hostSeat: Int,
        resumeToken: String?,
        players: List<String>,
        aiCount: Int,
        requiredHumans: Int
    ) -> Unit,
    private val onState: (EngineState) -> Unit,
    private val onError: (String) -> Unit,
    private val onConnection: (ConnectionState) -> Unit
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

    fun connect(onOpen: () -> Unit = {}) {
        closedByUser = false
        onConnection(ConnectionState.CONNECTING)
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    hasOpened = true
                    onConnection(ConnectionState.OPEN)
                    onOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        when (val msg = GameProtocol.parseMessage(text)) {
                            is NetMessage.Room -> onRoom(
                                msg.roomCode,
                                msg.yourSeat,
                                msg.hostSeat,
                                msg.resumeToken,
                                msg.players,
                                msg.aiCount,
                                msg.requiredHumans
                            )
                            is NetMessage.State -> onState(msg.state)
                            is NetMessage.Error -> onError(msg.message)
                            NetMessage.Pong -> Unit
                        }
                    } catch (error: Exception) {
                        onError(error.message ?: "服务器消息解析失败")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closedByUser) onConnection(ConnectionState.RECONNECTING)
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closedByUser) onConnection(ConnectionState.CLOSED)
                }

                override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                    if (!closedByUser) {
                        onConnection(if (hasOpened) ConnectionState.RECONNECTING else ConnectionState.FAILED)
                        onError(error.message ?: "连接服务器失败")
                    }
                }
            }
        )
    }

    fun createRoom(difficulty: AiDifficulty, aiCount: Int) {
        webSocket?.send(GameProtocol.encodeCreate(name, difficulty, aiCount))
    }

    fun joinRoom(room: String) {
        webSocket?.send(GameProtocol.encodeJoin(room, name))
    }

    fun reconnectRoom(room: String, token: String) {
        webSocket?.send(GameProtocol.encodeReconnect(room, token))
    }

    fun startGame() {
        webSocket?.send(GameProtocol.encodeStart())
    }

    fun restartGame() {
        webSocket?.send(GameProtocol.encodeRestart())
    }

    fun sendAction(seat: Int, action: RemoteAction) {
        webSocket?.send(GameProtocol.encodeAction(seat, action))
    }

    fun close() {
        closedByUser = true
        webSocket?.close(1000, "bye")
        webSocket = null
    }
}
