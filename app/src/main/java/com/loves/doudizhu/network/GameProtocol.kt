package com.loves.doudizhu.network

import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.model.AiDifficulty
import com.loves.doudizhu.model.Card
import org.json.JSONObject

sealed interface RemoteAction {
    data class Call(val want: Boolean) : RemoteAction
    data class Play(val cards: List<Card>) : RemoteAction
    data object Pass : RemoteAction
}

data class ServerError(
    val code: String? = null,
    val message: String
)

sealed interface NetMessage {
    data class Room(
        val roomCode: String,
        val yourSeat: Int,
        val hostSeat: Int,
        val resumeToken: String?,
        val players: List<String>,
        val aiCount: Int,
        val requiredHumans: Int,
        val difficulty: AiDifficulty,
        val actionTimeoutSeconds: Int
    ) : NetMessage
    data class State(val state: EngineState) : NetMessage
    data class RoomDisbanded(val reason: String) : NetMessage
    data class QuickMessage(
        val seat: Int,
        val messageId: String,
        val at: Long
    ) : NetMessage
    data class Error(val error: ServerError) : NetMessage {
        val code: String?
            get() = error.code
        val message: String
            get() = error.message
    }
    data object Pong : NetMessage
}

object GameProtocol {

    fun encodeCreate(
        name: String,
        playerId: String,
        authToken: String?,
        difficulty: AiDifficulty,
        aiCount: Int,
        actionTimeoutSeconds: Int
    ): String {
        val obj = JSONObject()
        obj.put("type", "CREATE")
        obj.put("name", name)
        obj.put("playerId", playerId)
        if (!authToken.isNullOrBlank()) obj.put("authToken", authToken)
        obj.put("difficulty", difficulty.name)
        obj.put("aiCount", aiCount.coerceIn(0, 2))
        obj.put(
            "actionTimeoutSeconds",
            actionTimeoutSeconds.takeIf { it in setOf(15, 20, 30, 45) } ?: 20
        )
        return obj.toString()
    }

    fun encodeJoin(room: String, name: String, playerId: String, authToken: String?): String {
        val obj = JSONObject()
        obj.put("type", "JOIN")
        obj.put("room", room)
        obj.put("name", name)
        obj.put("playerId", playerId)
        if (!authToken.isNullOrBlank()) obj.put("authToken", authToken)
        return obj.toString()
    }

    fun encodeReconnect(room: String, token: String, authToken: String?): String {
        val obj = JSONObject()
        obj.put("type", "RECONNECT")
        obj.put("room", room)
        obj.put("token", token)
        if (!authToken.isNullOrBlank()) obj.put("authToken", authToken)
        return obj.toString()
    }

    fun encodeStart(): String = """{"type":"START"}"""

    fun encodeRestart(): String = """{"type":"RESTART"}"""

    fun encodeReady(ready: Boolean): String {
        val obj = JSONObject()
        obj.put("type", "READY")
        obj.put("ready", ready)
        return obj.toString()
    }

    fun encodeLeave(): String = """{"type":"LEAVE"}"""

    fun encodeDisband(): String = """{"type":"DISBAND"}"""

    fun encodeQuickMessage(messageId: String): String {
        return JSONObject()
            .put("type", "QUICK_MESSAGE")
            .put("messageId", messageId)
            .toString()
    }

    fun encodeAction(seat: Int, action: RemoteAction): String {
        val actionObj = JSONObject()
        when (action) {
            is RemoteAction.Call -> {
                actionObj.put("kind", "CALL")
                actionObj.put("want", action.want)
            }
            is RemoteAction.Play -> {
                actionObj.put("kind", "PLAY")
                actionObj.put("cards", GameCodec.encodeCards(action.cards))
            }
            RemoteAction.Pass -> actionObj.put("kind", "PASS")
        }
        val obj = JSONObject()
        obj.put("type", "ACTION")
        obj.put("seat", seat)
        obj.put("action", actionObj)
        return obj.toString()
    }

    fun parseMessage(raw: String): NetMessage {
        val obj = JSONObject(raw)
        return when (obj.getString("type")) {
            "ROOM" -> NetMessage.Room(
                roomCode = obj.getString("room"),
                yourSeat = obj.getInt("yourSeat"),
                hostSeat = obj.optInt("hostSeat", 0),
                resumeToken = obj.optString("resumeToken").ifBlank { null },
                players = obj.getJSONArray("players").let { arr ->
                    List(arr.length()) { arr.getString(it) }
                },
                aiCount = obj.optInt("aiCount", 2).coerceIn(0, 2),
                requiredHumans = obj.optInt("requiredHumans", 1).coerceIn(1, 3),
                difficulty = runCatching {
                    AiDifficulty.valueOf(obj.optString("difficulty", AiDifficulty.MEDIUM.name))
                }.getOrDefault(AiDifficulty.MEDIUM),
                actionTimeoutSeconds = obj.optInt("actionTimeoutSeconds", 20)
                    .takeIf { it in setOf(15, 20, 30, 45) }
                    ?: 20
            )
            "STATE" -> NetMessage.State(
                GameCodec.decodeState(obj.getJSONObject("state").toString())
            )
            "ERROR" -> {
                val details = obj.optJSONObject("error")
                val message = details?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: obj.optString("message", "未知错误")
                val code = details?.optString("code")
                    ?.takeIf { it.isNotBlank() }
                    ?: obj.optString("code").ifBlank { null }
                NetMessage.Error(ServerError(code = code, message = message))
            }
            "ROOM_DISBANDED" -> NetMessage.RoomDisbanded(
                reason = obj.optString("reason", "房主已解散房间")
            )
            "QUICK_MESSAGE" -> NetMessage.QuickMessage(
                seat = obj.getInt("seat"),
                messageId = obj.getString("messageId"),
                at = obj.optLong("at", System.currentTimeMillis())
            )
            "PONG" -> NetMessage.Pong
            else -> throw IllegalArgumentException("无法识别的服务器消息")
        }
    }
}
