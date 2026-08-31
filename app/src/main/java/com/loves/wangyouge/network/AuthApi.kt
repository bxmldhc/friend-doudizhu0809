package com.loves.wangyouge.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class AuthSession(
    val authToken: String,
    val accountId: String,
    val nickname: String,
    val role: AccountRole = AccountRole.USER,
    val isAdmin: Boolean = role == AccountRole.ADMIN,
    val mustChangePassword: Boolean = false
)

enum class AccountRole {
    USER,
    ADMIN;

    companion object {
        fun from(value: String?): AccountRole {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: USER
        }
    }
}

data class AdminOverview(
    val accountCount: Int,
    val roomCount: Int,
    val connectedHumanCount: Int,
    val activeGameCount: Int,
    val appOnlineCount: Int = 0
)

data class AdminAccount(
    val accountId: String,
    val nickname: String,
    val createdAt: Long,
    val role: AccountRole,
    val isAdmin: Boolean,
    val disabled: Boolean = false,
    val mustChangePassword: Boolean = false,
    val totalScore: Int = 0,
    val games: Int = 0,
    val wins: Int = 0
)

data class RegistrationSettings(
    val enabled: Boolean
)

data class AdminAuditRecord(
    val auditId: String,
    val createdAt: Long,
    val actorAccountId: String,
    val actorNickname: String,
    val action: String,
    val targetAccountId: String? = null,
    val targetNickname: String? = null
)

data class AdminAuditList(
    val records: List<AdminAuditRecord>,
    val limit: Int
)

data class AdminAccountList(
    val accounts: List<AdminAccount>,
    val total: Int,
    val limit: Int,
    val query: String
)

data class AdminRoom(
    val roomCode: String,
    val phase: String,
    val humanCount: Int,
    val connectedHumanCount: Int,
    val aiCount: Int,
    val hostName: String,
    val gameNumber: Int,
    val createdAt: Long
)

data class AdminRoomList(
    val rooms: List<AdminRoom>,
    val total: Int
)

data class AdminOnlineAccount(
    val nickname: String,
    val gameType: String,
    val roomCode: String,
    val lastActiveAt: Long,
    val location: String = "ROOM"
)

data class AdminOnlineAccountList(
    val accounts: List<AdminOnlineAccount>,
    val appAccounts: List<AdminOnlineAccount> = emptyList(),
    val appOnlineCount: Int = appAccounts.size,
    val roomOnlineCount: Int = accounts.size
)

data class FriendProfile(
    val accountId: String,
    val nickname: String,
    val createdAt: Long,
    val online: Boolean,
    val lastSeenAt: Long? = null
)

data class FriendRequest(
    val requestId: String,
    val account: FriendProfile,
    val direction: String,
    val createdAt: Long
)

data class RoomInvitation(
    val invitationId: String,
    val roomCode: String,
    val gameType: String,
    val inviter: FriendProfile,
    val createdAt: Long,
    val expiresAt: Long
)

data class FriendsSnapshot(
    val friends: List<FriendProfile>,
    val requests: List<FriendRequest>,
    val roomInvitations: List<RoomInvitation> = emptyList()
)

data class AccountStats(
    val playerId: String,
    val totalScore: Int,
    val games: Int,
    val wins: Int,
    val landlordGames: Int = 0,
    val landlordWins: Int = 0,
    val farmerGames: Int = 0,
    val farmerWins: Int = 0,
    val recentMatches: List<RecentMatch> = emptyList(),
    val gameStats: Map<String, GameStats> = emptyMap()
)

data class GameStats(
    val totalScore: Int = 0,
    val games: Int = 0,
    val wins: Int = 0,
    val landlordGames: Int = 0,
    val landlordWins: Int = 0,
    val farmerGames: Int = 0,
    val farmerWins: Int = 0
)

data class RecentMatch(
    val matchId: String,
    val roomCode: String,
    val startedAt: Long,
    val updatedAt: Long,
    val gameType: String = "DOUDIZHU",
    val rounds: List<MatchRound>
)

data class MatchRound(
    val gameNumber: Int,
    val finishedAt: Long,
    val winnerPlayerId: String?,
    val landlordPlayerId: String?,
    val multiplier: Int,
    val players: List<MatchPlayerScore>
)

data class MatchPlayerScore(
    val playerId: String,
    val name: String,
    val delta: Int,
    val isAi: Boolean
)

data class PublicPlayerStats(
    val playerId: String,
    val nickname: String,
    val totalScore: Int,
    val games: Int,
    val wins: Int,
    val landlordGames: Int = 0,
    val landlordWins: Int = 0,
    val farmerGames: Int = 0,
    val farmerWins: Int = 0,
    val gameStats: Map<String, GameStats> = emptyMap()
)

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val code: String? = null
    ) : ApiResult<Nothing> {
        val isUnauthorized: Boolean
            get() = statusCode == 401 ||
                code.equals("UNAUTHORIZED", ignoreCase = true) ||
                code.equals("INVALID_TOKEN", ignoreCase = true) ||
                code.equals("AUTH_REQUIRED", ignoreCase = true)

        val isAdminRequired: Boolean
            get() = code.equals("ADMIN_REQUIRED", ignoreCase = true)
    }
}

class AuthApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun register(baseUrl: String, nickname: String, password: String): ApiResult<AuthSession> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/auth/register",
                method = "POST",
                body = JSONObject()
                    .put("nickname", nickname)
                    .put("password", password)
            ),
            parser = AuthResponseParser::parseSession
        )
    }

    suspend fun login(baseUrl: String, nickname: String, password: String): ApiResult<AuthSession> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/auth/login",
                method = "POST",
                body = JSONObject()
                    .put("nickname", nickname)
                    .put("password", password)
            ),
            parser = AuthResponseParser::parseSession
        )
    }

    suspend fun me(baseUrl: String, authToken: String): ApiResult<AuthSession> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/auth/me",
                method = "GET",
                authToken = authToken
            ),
            parser = { body ->
                AuthResponseParser.parseSession(body, fallbackToken = authToken)
            }
        )
    }

    suspend fun logout(baseUrl: String, authToken: String): ApiResult<Unit> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/auth/logout",
                method = "POST",
                body = JSONObject(),
                authToken = authToken
            ),
            parser = { Unit }
        )
    }

    suspend fun heartbeat(baseUrl: String, authToken: String): ApiResult<Unit> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/presence/heartbeat",
                method = "POST",
                body = JSONObject(),
                authToken = authToken
            ),
            parser = { Unit }
        )
    }

    suspend fun friends(baseUrl: String, authToken: String): ApiResult<FriendsSnapshot> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/friends",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseFriends
        )
    }

    suspend fun searchFriends(
        baseUrl: String,
        authToken: String,
        query: String
    ): ApiResult<List<FriendProfile>> {
        val encoded = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/friends/search?query=$encoded",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseFriendSearch
        )
    }

    suspend fun sendFriendRequest(
        baseUrl: String,
        authToken: String,
        targetAccountId: String
    ): ApiResult<Unit> = friendMutation(
        baseUrl,
        "/api/friends/requests",
        authToken,
        JSONObject().put("targetAccountId", targetAccountId)
    )

    suspend fun acceptFriendRequest(
        baseUrl: String,
        authToken: String,
        requestId: String
    ): ApiResult<Unit> = friendMutation(
        baseUrl,
        "/api/friends/requests/${java.net.URLEncoder.encode(requestId, Charsets.UTF_8.name())}/accept",
        authToken
    )

    suspend fun rejectFriendRequest(
        baseUrl: String,
        authToken: String,
        requestId: String
    ): ApiResult<Unit> = friendMutation(
        baseUrl,
        "/api/friends/requests/${java.net.URLEncoder.encode(requestId, Charsets.UTF_8.name())}/reject",
        authToken
    )

    suspend fun removeFriend(
        baseUrl: String,
        authToken: String,
        accountId: String
    ): ApiResult<Unit> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/friends/${java.net.URLEncoder.encode(accountId, Charsets.UTF_8.name())}",
                method = "DELETE",
                authToken = authToken
            ),
            parser = { Unit }
        )
    }

    suspend fun listRoomInvitations(
        baseUrl: String,
        authToken: String
    ): ApiResult<List<RoomInvitation>> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/invitations",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseRoomInvitations
        )
    }

    suspend fun createRoomInvitation(
        baseUrl: String,
        authToken: String,
        roomCode: String,
        gameType: String,
        targetAccountId: String
    ): ApiResult<RoomInvitation> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/rooms/${java.net.URLEncoder.encode(roomCode, Charsets.UTF_8.name())}/invitations",
                method = "POST",
                body = JSONObject()
                    .put("gameType", gameType)
                    .put("targetAccountId", targetAccountId),
                authToken = authToken
            ),
            parser = AuthResponseParser::parseRoomInvitation
        )
    }

    suspend fun acceptRoomInvitation(
        baseUrl: String,
        authToken: String,
        invitationId: String
    ): ApiResult<RoomInvitation> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/invitations/${java.net.URLEncoder.encode(invitationId, Charsets.UTF_8.name())}/accept",
                method = "POST",
                body = JSONObject(),
                authToken = authToken
            ),
            parser = AuthResponseParser::parseRoomInvitation
        )
    }

    suspend fun dismissRoomInvitation(
        baseUrl: String,
        authToken: String,
        invitationId: String
    ): ApiResult<Unit> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/invitations/${java.net.URLEncoder.encode(invitationId, Charsets.UTF_8.name())}/dismiss",
                method = "POST",
                body = JSONObject(),
                authToken = authToken
            ),
            parser = { Unit }
        )
    }

    suspend fun changePassword(
        baseUrl: String,
        authToken: String,
        currentPassword: String,
        newPassword: String
    ): ApiResult<AuthSession> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/auth/change-password",
                method = "POST",
                body = JSONObject()
                    .put("currentPassword", currentPassword)
                    .put("newPassword", newPassword),
                authToken = authToken
            ),
            parser = AuthResponseParser::parseSession
        )
    }

    suspend fun logoutOtherSessions(
        baseUrl: String,
        authToken: String
    ): ApiResult<AuthSession> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/auth/logout-other-sessions",
                method = "POST",
                body = JSONObject(),
                authToken = authToken
            ),
            parser = AuthResponseParser::parseSession
        )
    }

    suspend fun stats(baseUrl: String, authToken: String): ApiResult<AccountStats> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/stats/me",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseStats
        )
    }

    suspend fun leaderboard2048(baseUrl: String, authToken: String): ApiResult<List<Leaderboard2048Entry>> = execute(
        request = jsonRequest(baseUrl, "/api/games/2048/leaderboard", "GET", authToken = authToken),
        parser = AuthResponseParser::parse2048Leaderboard
    )

    suspend fun submit2048Score(baseUrl: String, authToken: String, score: Int, maxTile: Int, moves: Int): ApiResult<Leaderboard2048Entry> = execute(
        request = jsonRequest(
            baseUrl, "/api/games/2048/score", "POST",
            JSONObject().put("score", score).put("maxTile", maxTile).put("moves", moves), authToken
        ),
        parser = AuthResponseParser::parse2048Entry
    )

    suspend fun playerStats(
        baseUrl: String,
        authToken: String,
        playerId: String
    ): ApiResult<PublicPlayerStats> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/players/${java.net.URLEncoder.encode(playerId, Charsets.UTF_8.name())}/stats",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parsePublicStats
        )
    }

    suspend fun adminOverview(
        baseUrl: String,
        authToken: String
    ): ApiResult<AdminOverview> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/overview",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseAdminOverview
        )
    }

    suspend fun adminAccounts(
        baseUrl: String,
        authToken: String,
        query: String = "",
        limit: Int = 100
    ): ApiResult<AdminAccountList> {
        val safeLimit = limit.coerceIn(1, 100)
        val encodedQuery = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/accounts?query=$encodedQuery&limit=$safeLimit",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseAdminAccounts
        )
    }

    suspend fun createAdminAccount(
        baseUrl: String,
        authToken: String,
        nickname: String,
        temporaryPassword: String
    ): ApiResult<AdminAccount> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/accounts",
                method = "POST",
                body = JSONObject()
                    .put("nickname", nickname)
                    .put("temporaryPassword", temporaryPassword),
                authToken = authToken
            ),
            parser = AuthResponseParser::parseAdminAccountResult
        )
    }

    suspend fun adminRooms(
        baseUrl: String,
        authToken: String
    ): ApiResult<AdminRoomList> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/rooms",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseAdminRooms
        )
    }

    suspend fun adminOnlineAccounts(
        baseUrl: String,
        authToken: String
    ): ApiResult<AdminOnlineAccountList> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/online",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseAdminOnlineAccounts
        )
    }

    suspend fun resetAdminPassword(
        baseUrl: String,
        authToken: String,
        accountId: String,
        newPassword: String
    ): ApiResult<AdminAccount> {
        val encodedAccountId = java.net.URLEncoder.encode(
            accountId.trim(),
            Charsets.UTF_8.name()
        )
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/accounts/$encodedAccountId/reset-password",
                method = "POST",
                body = JSONObject().put("newPassword", newPassword),
                authToken = authToken
            ),
            parser = AuthResponseParser::parseResetPassword
        )
    }

    suspend fun adminRegistration(
        baseUrl: String,
        authToken: String
    ): ApiResult<RegistrationSettings> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/registration",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseRegistrationSettings
        )
    }

    suspend fun setAdminRegistration(
        baseUrl: String,
        authToken: String,
        enabled: Boolean
    ): ApiResult<RegistrationSettings> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/registration",
                method = "POST",
                body = JSONObject().put("enabled", enabled),
                authToken = authToken
            ),
            parser = AuthResponseParser::parseRegistrationSettings
        )
    }

    suspend fun setAdminAccountDisabled(
        baseUrl: String,
        authToken: String,
        accountId: String,
        disabled: Boolean
    ): ApiResult<AdminAccount> {
        val encodedAccountId = java.net.URLEncoder.encode(
            accountId.trim(),
            Charsets.UTF_8.name()
        )
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/accounts/$encodedAccountId/status",
                method = "POST",
                body = JSONObject().put("disabled", disabled),
                authToken = authToken
            ),
            parser = AuthResponseParser::parseAdminAccountResult
        )
    }

    suspend fun adminAudit(
        baseUrl: String,
        authToken: String,
        limit: Int = 50
    ): ApiResult<AdminAuditList> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = "/api/admin/audit?limit=${limit.coerceIn(1, 100)}",
                method = "GET",
                authToken = authToken
            ),
            parser = AuthResponseParser::parseAdminAudit
        )
    }

    private fun jsonRequest(
        baseUrl: String,
        path: String,
        method: String,
        body: JSONObject? = null,
        authToken: String? = null
    ): Request {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Accept", "application/json")
        if (!authToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $authToken")
        }
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        return when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post(requestBody ?: EMPTY_JSON_BODY).build()
            "DELETE" -> builder.delete().build()
            else -> error("Unsupported method: $method")
        }
    }

    private suspend fun friendMutation(
        baseUrl: String,
        path: String,
        authToken: String,
        body: JSONObject = JSONObject()
    ): ApiResult<Unit> {
        return execute(
            request = jsonRequest(
                baseUrl = baseUrl,
                path = path,
                method = "POST",
                body = body,
                authToken = authToken
            ),
            parser = { Unit }
        )
    }

    private suspend fun <T> execute(
        request: Request,
        parser: (JSONObject) -> T
    ): ApiResult<T> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) {
                    continuation.resume(ApiResult.Failure(error.message ?: "无法连接账号服务器"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrNull()
                    if (!it.isSuccessful || json?.optBoolean("ok", true) == false) {
                        val error = AuthResponseParser.parseError(json)
                        if (continuation.isActive) {
                            continuation.resume(
                                ApiResult.Failure(
                                    message = error.first.ifBlank { "账号请求失败" },
                                    statusCode = it.code,
                                    code = error.second
                                )
                            )
                        }
                        return
                    }
                    val result: ApiResult<T> = runCatching {
                        parser(json ?: JSONObject())
                    }.fold(
                        onSuccess = { value -> ApiResult.Success(value) },
                        onFailure = { error ->
                            ApiResult.Failure(
                                error.message ?: "账号服务器返回数据不完整",
                                it.code
                            )
                        }
                    )
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        })
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_JSON_BODY = "{}".toRequestBody(JSON_MEDIA_TYPE)
    }
}

internal object AuthResponseParser {
    fun parse2048Leaderboard(root: JSONObject): List<Leaderboard2048Entry> {
        val data = root.optJSONObject("data") ?: root
        val entries = data.optJSONArray("entries") ?: return emptyList()
        return List(entries.length()) { parse2048Entry(entries.getJSONObject(it)) }
    }

    fun parse2048Entry(root: JSONObject): Leaderboard2048Entry {
        val data = root.optJSONObject("data")
        val item = root.optJSONObject("entry") ?: data?.optJSONObject("entry") ?: data ?: root
        return Leaderboard2048Entry(
            playerId = item.optString("playerId"), name = item.optString("name"),
            score = item.optInt("score"), maxTile = item.optInt("maxTile"),
            moves = item.optInt("moves"), updatedAt = item.optLong("updatedAt")
        )
    }
    fun parseSession(root: JSONObject, fallbackToken: String = ""): AuthSession {
        val data = root.optJSONObject("data") ?: root
        val account = data.optJSONObject("account")
            ?: data.optJSONObject("user")
            ?: data
        val token = firstString(data, "authToken", "token", "sessionToken")
            .ifBlank { firstString(root, "authToken", "token", "sessionToken") }
            .ifBlank { fallbackToken }
        val accountId = firstString(account, "accountId", "id", "playerId")
            .ifBlank { firstString(data, "accountId", "id", "playerId") }
        val nickname = firstString(account, "nickname", "name", "username")
            .ifBlank { firstString(data, "nickname", "name", "username") }
        val declaredRole = firstString(account, "role")
            .ifBlank { firstString(data, "role") }
            .ifBlank { firstString(root, "role") }
        val declaredAdmin = firstBoolean(account, "isAdmin")
            ?: firstBoolean(data, "isAdmin")
            ?: firstBoolean(root, "isAdmin")
        val role = if (declaredAdmin == true) {
            AccountRole.ADMIN
        } else {
            AccountRole.from(declaredRole)
        }
        val isAdmin = declaredAdmin == true || role == AccountRole.ADMIN
        val mustChangePassword = firstBoolean(account, "mustChangePassword")
            ?: firstBoolean(data, "mustChangePassword")
            ?: firstBoolean(root, "mustChangePassword")
            ?: false
        require(token.isNotBlank()) { "账号服务器未返回登录令牌" }
        require(accountId.isNotBlank()) { "账号服务器未返回账号 ID" }
        require(nickname.isNotBlank()) { "账号服务器未返回昵称" }
        return AuthSession(
            authToken = token,
            accountId = accountId,
            nickname = nickname,
            role = role,
            isAdmin = isAdmin,
            mustChangePassword = mustChangePassword
        )
    }

    fun parseStats(root: JSONObject): AccountStats {
        val data = root.optJSONObject("data") ?: root
        val stats = data.optJSONObject("stats") ?: data
        return AccountStats(
            playerId = firstString(stats, "playerId", "accountId", "id"),
            totalScore = firstInt(stats, "totalScore", "score", "points"),
            games = firstInt(stats, "games", "gameCount", "totalGames"),
            wins = firstInt(stats, "wins", "winCount", "totalWins"),
            landlordGames = firstInt(stats, "landlordGames"),
            landlordWins = firstInt(stats, "landlordWins"),
            farmerGames = firstInt(stats, "farmerGames"),
            farmerWins = firstInt(stats, "farmerWins"),
            gameStats = parseGameStats(stats.optJSONObject("gameStats")),
            recentMatches = stats.optJSONArray("recentMatches")?.let { matches ->
                List(matches.length()) { index ->
                    parseRecentMatch(matches.getJSONObject(index))
                }
            }.orEmpty()
        )
    }

    private fun parseGameStats(value: JSONObject?): Map<String, GameStats> {
        if (value == null) return emptyMap()
        return value.keys().asSequence().associateWith { key ->
            val item = value.optJSONObject(key) ?: JSONObject()
            GameStats(
                totalScore = firstInt(item, "totalScore"),
                games = firstInt(item, "games"),
                wins = firstInt(item, "wins"),
                landlordGames = firstInt(item, "landlordGames"),
                landlordWins = firstInt(item, "landlordWins"),
                farmerGames = firstInt(item, "farmerGames"),
                farmerWins = firstInt(item, "farmerWins")
            )
        }
    }

    private fun parseRecentMatch(item: JSONObject): RecentMatch {
        val rounds = item.optJSONArray("rounds")
        return RecentMatch(
            matchId = item.optString("matchId"),
            roomCode = item.optString("roomCode"),
            startedAt = item.optLong("startedAt"),
            updatedAt = item.optLong("updatedAt"),
            gameType = item.optString("gameType").ifBlank { "DOUDIZHU" },
            rounds = if (rounds == null) emptyList() else {
                List(rounds.length()) { index ->
                    parseMatchRound(rounds.getJSONObject(index))
                }
            }
        )
    }

    private fun parseMatchRound(item: JSONObject): MatchRound {
        val players = item.optJSONArray("players")
        return MatchRound(
            gameNumber = item.optInt("gameNumber"),
            finishedAt = item.optLong("finishedAt"),
            winnerPlayerId = item.optString("winnerPlayerId").ifBlank { null },
            landlordPlayerId = item.optString("landlordPlayerId").ifBlank { null },
            multiplier = item.optInt("multiplier", 1),
            players = if (players == null) emptyList() else {
                List(players.length()) { index ->
                    val player = players.getJSONObject(index)
                    MatchPlayerScore(
                        playerId = player.optString("playerId"),
                        name = player.optString("name"),
                        delta = player.optInt("delta"),
                        isAi = player.optBoolean("isAi")
                    )
                }
            }
        )
    }

    fun parsePublicStats(root: JSONObject): PublicPlayerStats {
        val data = root.optJSONObject("data") ?: root
        val stats = data.optJSONObject("stats") ?: data
        return PublicPlayerStats(
            playerId = firstString(stats, "playerId", "accountId", "id"),
            nickname = firstString(stats, "name", "nickname", "username"),
            totalScore = firstInt(stats, "totalScore", "score", "points"),
            games = firstInt(stats, "games", "gameCount", "totalGames"),
            wins = firstInt(stats, "wins", "winCount", "totalWins"),
            landlordGames = firstInt(stats, "landlordGames"),
            landlordWins = firstInt(stats, "landlordWins"),
            farmerGames = firstInt(stats, "farmerGames"),
            farmerWins = firstInt(stats, "farmerWins"),
            gameStats = parseGameStats(stats.optJSONObject("gameStats"))
        )
    }

    fun parseAdminOverview(root: JSONObject): AdminOverview {
        val overview = unwrapObject(root, "overview", "summary")
        return AdminOverview(
            accountCount = firstInt(overview, "accountCount", "accounts", "totalAccounts"),
            roomCount = firstInt(overview, "roomCount", "rooms", "totalRooms"),
            connectedHumanCount = firstInt(
                overview,
                "connectedHumanCount",
                "connectedHumans",
                "onlinePlayers",
                "humanCount"
            ),
            activeGameCount = firstInt(
                overview,
                "activeGameCount",
                "activeGames",
                "playingRoomCount"
            ),
            appOnlineCount = firstInt(overview, "appOnlineCount")
        )
    }

    fun parseAdminAccounts(root: JSONObject): AdminAccountList {
        val data = root.optJSONObject("data") ?: root
        val container = data.optJSONObject("accounts")
        val array = when {
            data.optJSONArray("accounts") != null -> data.getJSONArray("accounts")
            container?.optJSONArray("items") != null -> container.getJSONArray("items")
            container?.optJSONArray("accounts") != null -> container.getJSONArray("accounts")
            root.optJSONArray("accounts") != null -> root.getJSONArray("accounts")
            else -> null
        }
        val metadata = container ?: data
        val accounts = if (array == null) emptyList() else {
            List(array.length()) { index -> parseAdminAccount(array.getJSONObject(index)) }
        }
        return AdminAccountList(
            accounts = accounts,
            total = firstInt(metadata, "total", "accountCount", "totalAccounts")
                .takeIf { it > 0 } ?: accounts.size,
            limit = firstInt(metadata, "limit", "pageSize")
                .takeIf { it > 0 } ?: accounts.size,
            query = firstString(metadata, "query", "search")
        )
    }

    fun parseAdminRooms(root: JSONObject): AdminRoomList {
        val data = root.optJSONObject("data") ?: root
        val container = data.optJSONObject("rooms")
        val array = when {
            data.optJSONArray("rooms") != null -> data.getJSONArray("rooms")
            container?.optJSONArray("items") != null -> container.getJSONArray("items")
            container?.optJSONArray("rooms") != null -> container.getJSONArray("rooms")
            root.optJSONArray("rooms") != null -> root.getJSONArray("rooms")
            else -> null
        }
        val metadata = container ?: data
        val rooms = if (array == null) emptyList() else {
            List(array.length()) { index -> parseAdminRoom(array.getJSONObject(index)) }
        }
        return AdminRoomList(
            rooms = rooms,
            total = firstInt(metadata, "total", "roomCount", "totalRooms")
                .takeIf { it > 0 } ?: rooms.size
        )
    }

    fun parseAdminOnlineAccounts(root: JSONObject): AdminOnlineAccountList {
        val data = root.optJSONObject("data") ?: root
        fun parse(array: org.json.JSONArray?): List<AdminOnlineAccount> {
            if (array == null) return emptyList()
            return List(array.length()) { index ->
                val item = array.getJSONObject(index)
                AdminOnlineAccount(
                    nickname = item.optString("nickname"),
                    gameType = item.optString("gameType"),
                    roomCode = item.optString("roomCode"),
                    lastActiveAt = item.optLong("lastActiveAt"),
                    location = item.optString("location", "ROOM")
                )
            }
        }
        val accounts = parse(data.optJSONArray("accounts"))
        val appAccounts = parse(data.optJSONArray("appAccounts"))
        return AdminOnlineAccountList(
            accounts = accounts,
            appAccounts = appAccounts,
            appOnlineCount = data.optInt("appOnlineCount", appAccounts.size),
            roomOnlineCount = data.optInt("roomOnlineCount", accounts.size)
        )
    }

    fun parseFriends(root: JSONObject): FriendsSnapshot {
        val data = root.optJSONObject("data") ?: root
        val friends = data.optJSONArray("friends")?.let { array ->
            List(array.length()) { index -> parseFriendProfile(array.getJSONObject(index)) }
        }.orEmpty()
        val requests = data.optJSONArray("requests")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                FriendRequest(
                    requestId = item.optString("requestId"),
                    account = parseFriendProfile(item.optJSONObject("account") ?: JSONObject()),
                    direction = item.optString("direction"),
                    createdAt = item.optLong("createdAt")
                )
            }
        }.orEmpty()
        return FriendsSnapshot(friends, requests)
    }

    fun parseRoomInvitations(root: JSONObject): List<RoomInvitation> {
        val data = root.optJSONObject("data") ?: root
        val array = data.optJSONArray("invitations") ?: return emptyList()
        return List(array.length()) { index ->
            parseRoomInvitationObject(array.getJSONObject(index))
        }
    }

    fun parseRoomInvitation(root: JSONObject): RoomInvitation {
        val data = root.optJSONObject("data") ?: root
        return parseRoomInvitationObject(data.optJSONObject("invitation") ?: data)
    }

    private fun parseRoomInvitationObject(item: JSONObject): RoomInvitation {
        return RoomInvitation(
            invitationId = firstString(item, "invitationId", "id"),
            roomCode = firstString(item, "roomCode", "room"),
            gameType = firstString(item, "gameType", "type"),
            inviter = parseFriendProfile(
                item.optJSONObject("inviter")
                    ?: item.optJSONObject("from")
                    ?: JSONObject()
            ),
            createdAt = firstLong(item, "createdAt"),
            expiresAt = firstLong(item, "expiresAt")
        )
    }

    fun parseFriendSearch(root: JSONObject): List<FriendProfile> {
        val data = root.optJSONObject("data") ?: root
        val accounts = data.optJSONArray("accounts") ?: return emptyList()
        return List(accounts.length()) { index ->
            parseFriendProfile(accounts.getJSONObject(index))
        }
    }

    fun parseResetPassword(root: JSONObject): AdminAccount {
        val data = root.optJSONObject("data") ?: root
        val account = data.optJSONObject("account")
            ?: data.optJSONObject("user")
            ?: data.optJSONObject("updatedAccount")
            ?: data
        return parseAdminAccount(account)
    }

    fun parseAdminAccountResult(root: JSONObject): AdminAccount {
        val data = root.optJSONObject("data") ?: root
        return parseAdminAccount(data.optJSONObject("account") ?: data)
    }

    fun parseRegistrationSettings(root: JSONObject): RegistrationSettings {
        val data = root.optJSONObject("data") ?: root
        return RegistrationSettings(enabled = data.optBoolean("enabled", true))
    }

    fun parseAdminAudit(root: JSONObject): AdminAuditList {
        val data = root.optJSONObject("data") ?: root
        val array = data.optJSONArray("records")
        val records = if (array == null) emptyList() else {
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                AdminAuditRecord(
                    auditId = item.optString("auditId"),
                    createdAt = item.optLong("createdAt"),
                    actorAccountId = item.optString("actorAccountId"),
                    actorNickname = item.optString("actorNickname"),
                    action = item.optString("action"),
                    targetAccountId = item.optString("targetAccountId").ifBlank { null },
                    targetNickname = item.optString("targetNickname").ifBlank { null }
                )
            }
        }
        return AdminAuditList(
            records = records,
            limit = data.optInt("limit", records.size)
        )
    }

    fun parseError(root: JSONObject?): Pair<String, String?> {
        if (root == null) return "账号服务器返回了无法识别的数据" to null
        val errorObject = root.optJSONObject("error")
        val message = when {
            errorObject != null -> firstString(errorObject, "message", "error", "detail")
            root.opt("error") is String -> root.optString("error")
            else -> firstString(root, "message", "detail")
        }
        val code = firstString(errorObject ?: root, "code", "errorCode").ifBlank { null }
        return message to code
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            obj.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
        }.orEmpty()
    }

    private fun firstInt(obj: JSONObject, vararg keys: String): Int {
        for (key in keys) {
            if (obj.has(key)) return obj.optInt(key, 0)
        }
        return 0
    }

    private fun firstLong(obj: JSONObject, vararg keys: String): Long {
        for (key in keys) {
            if (obj.has(key)) return obj.optLong(key, 0L)
        }
        return 0L
    }

    private fun firstBoolean(obj: JSONObject, vararg keys: String): Boolean? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            return when (val value = obj.opt(key)) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> when (value.trim().lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
                else -> null
            }
        }
        return null
    }

    private fun unwrapObject(root: JSONObject, vararg names: String): JSONObject {
        val data = root.optJSONObject("data") ?: root
        for (name in names) {
            data.optJSONObject(name)?.let { return it }
        }
        return data
    }

    private fun parseAdminAccount(item: JSONObject): AdminAccount {
        val accountId = firstString(item, "accountId", "id", "playerId")
        val nickname = firstString(item, "nickname", "name", "username")
        require(accountId.isNotBlank()) { "管理员账号数据缺少账号 ID" }
        require(nickname.isNotBlank()) { "管理员账号数据缺少昵称" }
        val declaredAdmin = firstBoolean(item, "isAdmin")
        val role = if (declaredAdmin == true) {
            AccountRole.ADMIN
        } else {
            AccountRole.from(firstString(item, "role"))
        }
        return AdminAccount(
            accountId = accountId,
            nickname = nickname,
            createdAt = firstLong(item, "createdAt", "registeredAt"),
            role = role,
            isAdmin = declaredAdmin == true || role == AccountRole.ADMIN,
            disabled = firstBoolean(item, "disabled") == true,
            mustChangePassword = firstBoolean(item, "mustChangePassword") == true,
            totalScore = firstInt(item, "totalScore", "score", "points"),
            games = firstInt(item, "games", "gameCount", "totalGames"),
            wins = firstInt(item, "wins", "winCount", "totalWins")
        )
    }

    private fun parseFriendProfile(item: JSONObject): FriendProfile {
        return FriendProfile(
            accountId = firstString(item, "accountId", "id", "playerId"),
            nickname = firstString(item, "nickname", "name"),
            createdAt = firstLong(item, "createdAt"),
            online = firstBoolean(item, "online") == true,
            lastSeenAt = firstLong(item, "lastSeenAt").takeIf { it > 0L }
        )
    }

    private fun parseAdminRoom(item: JSONObject): AdminRoom {
        val roomCode = firstString(item, "roomCode", "code", "room")
        require(roomCode.isNotBlank()) { "管理员房间数据缺少房号" }
        return AdminRoom(
            roomCode = roomCode,
            phase = firstString(item, "phase", "state", "status"),
            humanCount = firstInt(item, "humanCount", "playerCount", "humans"),
            connectedHumanCount = firstInt(
                item,
                "connectedHumanCount",
                "connectedHumans",
                "onlinePlayers"
            ),
            aiCount = firstInt(item, "aiCount", "ais"),
            hostName = firstString(item, "hostName", "ownerName", "host"),
            gameNumber = firstInt(item, "gameNumber", "roundNumber", "round"),
            createdAt = firstLong(item, "createdAt", "startedAt")
        )
    }
}
