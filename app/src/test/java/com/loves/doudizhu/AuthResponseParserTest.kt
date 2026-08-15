package com.loves.doudizhu

import com.loves.doudizhu.network.AuthResponseParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthResponseParserTest {
    @Test
    fun parsesWrappedSession() {
        val session = AuthResponseParser.parseSession(
            JSONObject(
                """
                {"ok":true,"data":{"authToken":"token-1","account":{"accountId":"account-1","nickname":"小明"}}}
                """.trimIndent()
            )
        )

        assertEquals("token-1", session.authToken)
        assertEquals("account-1", session.accountId)
        assertEquals("小明", session.nickname)
    }

    @Test
    fun parsesFlatStatsAliases() {
        val stats = AuthResponseParser.parseStats(
            JSONObject("""{"ok":true,"data":{"score":18,"gameCount":7,"winCount":4}}""")
        )

        assertEquals(18, stats.totalScore)
        assertEquals(7, stats.games)
        assertEquals(4, stats.wins)
    }
}
