package com.loves.doudizhu

import com.loves.doudizhu.network.AuthResponseParser
import com.loves.doudizhu.network.AccountRole
import com.loves.doudizhu.network.ApiResult
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals(AccountRole.USER, session.role)
        assertFalse(session.isAdmin)
    }

    @Test
    fun parsesAdminRoleFromAccountAndTopLevelFlag() {
        val byRole = AuthResponseParser.parseSession(
            JSONObject(
                """
                {
                  "ok": true,
                  "data": {
                    "token": "token-admin",
                    "account": {
                      "id": "account-admin",
                      "name": "dlc",
                      "role": "ADMIN"
                    }
                  }
                }
                """.trimIndent()
            )
        )
        val byFlag = AuthResponseParser.parseSession(
            JSONObject(
                """
                {
                  "authToken": "token-flag",
                  "accountId": "account-flag",
                  "nickname": "管理员",
                  "isAdmin": true
                }
                """.trimIndent()
            )
        )

        assertEquals(AccountRole.ADMIN, byRole.role)
        assertTrue(byRole.isAdmin)
        assertEquals(AccountRole.ADMIN, byFlag.role)
        assertTrue(byFlag.isAdmin)
    }

    @Test
    fun authRequiredFailureIsUnauthorized() {
        val failure = ApiResult.Failure(
            message = "登录已失效",
            statusCode = 400,
            code = "AUTH_REQUIRED"
        )

        assertTrue(failure.isUnauthorized)
    }

    @Test
    fun adminRequiredDoesNotExpireLogin() {
        val failure = ApiResult.Failure(
            message = "需要管理员权限",
            statusCode = 403,
            code = "ADMIN_REQUIRED"
        )

        assertFalse(failure.isUnauthorized)
        assertTrue(failure.isAdminRequired)
    }

    @Test
    fun parsesFlatStatsAliases() {
        val stats = AuthResponseParser.parseStats(
            JSONObject(
                """
                {
                  "ok": true,
                  "data": {
                    "playerId": "account-1",
                    "score": 18,
                    "gameCount": 7,
                    "winCount": 4,
                    "recentMatches": [{
                      "matchId": "match-1",
                      "roomCode": "123456",
                      "startedAt": 100,
                      "updatedAt": 200,
                      "rounds": [{
                        "gameNumber": 1,
                        "finishedAt": 200,
                        "winnerPlayerId": "account-1",
                        "landlordPlayerId": "account-1",
                        "multiplier": 2,
                        "players": [
                          {"playerId":"account-1","name":"玩家","delta":4,"isAi":false},
                          {"playerId":"ai-1","name":"小智","delta":-2,"isAi":true}
                        ]
                      }]
                    }]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals("account-1", stats.playerId)
        assertEquals(18, stats.totalScore)
        assertEquals(7, stats.games)
        assertEquals(4, stats.wins)
        assertEquals(1, stats.recentMatches.size)
        assertEquals("123456", stats.recentMatches.single().roomCode)
        assertEquals(true, stats.recentMatches.single().rounds.single().players[1].isAi)
    }

    @Test
    fun parsesWrappedAdminOverviewAliases() {
        val overview = AuthResponseParser.parseAdminOverview(
            JSONObject(
                """
                {
                  "ok": true,
                  "data": {
                    "overview": {
                      "totalAccounts": 12,
                      "totalRooms": 3,
                      "connectedHumans": 5,
                      "activeGames": 2
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(12, overview.accountCount)
        assertEquals(3, overview.roomCount)
        assertEquals(5, overview.connectedHumanCount)
        assertEquals(2, overview.activeGameCount)
    }

    @Test
    fun parsesNestedAdminAccountListAndResetResponse() {
        val accounts = AuthResponseParser.parseAdminAccounts(
            JSONObject(
                """
                {
                  "ok": true,
                  "data": {
                    "accounts": {
                      "items": [{
                        "id": "account-1",
                        "name": "dlc",
                        "createdAt": 1234,
                        "role": "ADMIN",
                        "score": 30,
                        "gameCount": 8,
                        "winCount": 5
                      }],
                      "total": 7,
                      "limit": 20,
                      "query": "dl"
                    }
                  }
                }
                """.trimIndent()
            )
        )
        val reset = AuthResponseParser.parseResetPassword(
            JSONObject(
                """
                {
                  "ok": true,
                  "data": {
                    "updatedAccount": {
                      "accountId": "account-2",
                      "nickname": "玩家二",
                      "isAdmin": false
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(7, accounts.total)
        assertEquals(20, accounts.limit)
        assertEquals("dl", accounts.query)
        assertEquals("account-1", accounts.accounts.single().accountId)
        assertTrue(accounts.accounts.single().isAdmin)
        assertEquals(30, accounts.accounts.single().totalScore)
        assertEquals("account-2", reset.accountId)
        assertFalse(reset.isAdmin)
    }

    @Test
    fun parsesFlatAdminRoomListAliases() {
        val rooms = AuthResponseParser.parseAdminRooms(
            JSONObject(
                """
                {
                  "ok": true,
                  "data": {
                    "rooms": [{
                      "code": "123456",
                      "status": "PLAYING",
                      "playerCount": 2,
                      "connectedHumans": 1,
                      "ais": 1,
                      "ownerName": "dlc",
                      "roundNumber": 4,
                      "startedAt": 9988
                    }],
                    "roomCount": 1
                  }
                }
                """.trimIndent()
            )
        )

        val room = rooms.rooms.single()
        assertEquals(1, rooms.total)
        assertEquals("123456", room.roomCode)
        assertEquals("PLAYING", room.phase)
        assertEquals(2, room.humanCount)
        assertEquals(1, room.connectedHumanCount)
        assertEquals(1, room.aiCount)
        assertEquals("dlc", room.hostName)
        assertEquals(4, room.gameNumber)
        assertEquals(9988L, room.createdAt)
    }
}
