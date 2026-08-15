package com.loves.doudizhu

import com.loves.doudizhu.network.GameProtocol
import com.loves.doudizhu.network.NetMessage
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
}
