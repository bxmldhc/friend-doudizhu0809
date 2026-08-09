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

sealed interface NetMessage {
    data class Room(
        val roomCode: String,
        val yourSeat: Int,
        val hostSeat: Int,
        val resumeToken: String?,
        val players: List<String>,
        val aiCount: Int,
        val requiredHumans: Int
    ) : NetMessage
    data class State(val state: EngineState) : NetMessage
    data class Error(val message: String) : NetMessage
    data object Pong : NetMessage
}

object GameProtocol {

    fun encodeCreate(name: String, difficulty: AiDifficulty, aiCount: Int): String {
        val obj = JSONObject()
        obj.put("type", "CREATE")
        obj.put("name", name)
        obj.put("difficulty", difficulty.name)
        obj.put("aiCount", aiCount.coerceIn(0, 2))
        return obj.toString()
    }

    fun encodeJoin(room: String, name: String): String {
        val obj = JSONObject()
        obj.put("type", "JOIN")
        obj.put("room", room)
        obj.put("name", name)
        return obj.toString()
    }

    fun encodeReconnect(room: String, token: String): String {
        val obj = JSONObject()
        obj.put("type", "RECONNECT")
        obj.put("room", room)
        obj.put("token", token)
        return obj.toString()
    }

    fun encodeStart(): String = """{"type":"START"}"""

    fun encodeRestart(): String = """{"type":"RESTART"}"""

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
                requiredHumans = obj.optInt("requiredHumans", 1).coerceIn(1, 3)
            )
            "STATE" -> NetMessage.State(
                GameCodec.decodeState(obj.getJSONObject("state").toString())
            )
            "ERROR" -> NetMessage.Error(obj.optString("message", "未知错误"))
            "PONG" -> NetMessage.Pong
            else -> throw IllegalArgumentException("无法识别的服务器消息")
        }
    }
}
