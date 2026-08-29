package com.loves.wangyouge

import com.loves.wangyouge.model.AiDifficulty
import com.loves.wangyouge.network.GameProtocol
import com.loves.wangyouge.network.NetMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameProtocolErrorTest {
    @Test
    fun parsesFlatStructuredServerError() {
        val message = GameProtocol.parseMessage(
            """{"type":"ERROR","code":"AUTH_REQUIRED","message":"登录已失效"}"""
        ) as NetMessage.Error

        assertEquals("AUTH_REQUIRED", message.error.code)
        assertEquals("登录已失效", message.error.message)
    }

    @Test
    fun parsesWrappedStructuredServerError() {
        val message = GameProtocol.parseMessage(
            """
            {
              "type": "ERROR",
              "error": {
                "code": "ROOM_NOT_FOUND",
                "message": "房间不存在"
              }
            }
            """.trimIndent()
        ) as NetMessage.Error

        assertEquals("ROOM_NOT_FOUND", message.code)
        assertEquals("房间不存在", message.message)
    }

    @Test
    fun keepsLegacyErrorMessageWithoutInventingCode() {
        val message = GameProtocol.parseMessage(
            """{"type":"ERROR","message":"操作失败"}"""
        ) as NetMessage.Error

        assertNull(message.code)
        assertEquals("操作失败", message.message)
    }

    @Test
    fun reconnectIncludesCurrentAuthToken() {
        val payload = GameProtocol.encodeReconnect(
            room = "123456",
            token = "resume-token",
            authToken = "auth-token"
        )

        assertTrue(payload.contains("\"room\":\"123456\""))
        assertTrue(payload.contains("\"token\":\"resume-token\""))
        assertTrue(payload.contains("\"authToken\":\"auth-token\""))
    }

    @Test
    fun parsesRoomRulesFromServer() {
        val message = GameProtocol.parseMessage(
            """
            {
              "type": "ROOM",
              "room": "123456",
              "yourSeat": 1,
              "hostSeat": 0,
              "players": ["房主", "玩家", "AI"],
              "aiCount": 1,
              "requiredHumans": 2,
              "difficulty": "HARD",
              "actionTimeoutSeconds": 45
            }
            """.trimIndent()
        ) as NetMessage.Room

        assertEquals(AiDifficulty.HARD, message.difficulty)
        assertEquals(45, message.actionTimeoutSeconds)
    }

    @Test
    fun invalidRoomRulesFallBackToCompatibleDefaults() {
        val message = GameProtocol.parseMessage(
            """
            {
              "type": "ROOM",
              "room": "123456",
              "yourSeat": 0,
              "players": ["房主", "AI 1", "AI 2"],
              "difficulty": "IMPOSSIBLE",
              "actionTimeoutSeconds": 99
            }
            """.trimIndent()
        ) as NetMessage.Room

        assertEquals(AiDifficulty.MEDIUM, message.difficulty)
        assertEquals(20, message.actionTimeoutSeconds)
    }

    @Test
    fun parsesRoomDisbandedAndQuickMessageEvents() {
        val disbanded = GameProtocol.parseMessage(
            """{"type":"ROOM_DISBANDED","reason":"房主已解散房间"}"""
        ) as NetMessage.RoomDisbanded
        val quickMessage = GameProtocol.parseMessage(
            """{"type":"QUICK_MESSAGE","seat":2,"messageId":"BE_RIGHT_BACK","at":12345}"""
        ) as NetMessage.QuickMessage

        assertEquals("房主已解散房间", disbanded.reason)
        assertEquals(2, quickMessage.seat)
        assertEquals("BE_RIGHT_BACK", quickMessage.messageId)
        assertEquals(12345L, quickMessage.at)
    }

    @Test
    fun createPayloadKeepsSupportedRulesAndClampsUnsafeValues() {
        val valid = JSONObject(
            GameProtocol.encodeCreate(
                name = "玩家",
                playerId = "player-1",
                authToken = "token",
                difficulty = AiDifficulty.HARD,
                aiCount = 1,
                actionTimeoutSeconds = 30
            )
        )
        val clamped = JSONObject(
            GameProtocol.encodeCreate(
                name = "玩家",
                playerId = "player-1",
                authToken = null,
                difficulty = AiDifficulty.EASY,
                aiCount = 8,
                actionTimeoutSeconds = 99
            )
        )

        assertEquals("HARD", valid.getString("difficulty"))
        assertEquals(1, valid.getInt("aiCount"))
        assertEquals(30, valid.getInt("actionTimeoutSeconds"))
        assertEquals(2, clamped.getInt("aiCount"))
        assertEquals(20, clamped.getInt("actionTimeoutSeconds"))
    }
}
