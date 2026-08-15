package com.loves.doudizhu.network

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
    val isAdmin: Boolean = role == AccountRole.ADMIN
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
    val activeGameCount: Int
)

data class AdminAccount(
    val accountId: String,
    val nickname: String,
    val createdAt: Long,
    val role: AccountRole,
    val isAdmin: Boolean,
    val totalScore: Int = 0,
    val games: Int = 0,
    val wins: Int = 0
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

data class AccountStats(
    val playerId: String,
    val totalScore: Int,
    val games: Int,
    val wins: Int,
    val recentMatches: List<RecentMatch> = emptyList()
)

data class RecentMatch(
    val matchId: String,
    val roomCode: String,
    val startedAt: Long,
    val updatedAt: Long,
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
    val wins: Int
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
            else -> error("Unsupported method: $method")
        }
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
        require(token.isNotBlank()) { "账号服务器未返回登录令牌" }
        require(accountId.isNotBlank()) { "账号服务器未返回账号 ID" }
        require(nickname.isNotBlank()) { "账号服务器未返回昵称" }
        return AuthSession(token, accountId, nickname, role, isAdmin)
    }

    fun parseStats(root: JSONObject): AccountStats {
        val data = root.optJSONObject("data") ?: root
        val stats = data.optJSONObject("stats") ?: data
        return AccountStats(
            playerId = firstString(stats, "playerId", "accountId", "id"),
            totalScore = firstInt(stats, "totalScore", "score", "points"),
            games = firstInt(stats, "games", "gameCount", "totalGames"),
            wins = firstInt(stats, "wins", "winCount", "totalWins"),
            recentMatches = stats.optJSONArray("recentMatches")?.let { matches ->
                List(matches.length()) { index ->
                    parseRecentMatch(matches.getJSONObject(index))
                }
            }.orEmpty()
        )
    }

    private fun parseRecentMatch(item: JSONObject): RecentMatch {
        val rounds = item.optJSONArray("rounds")
        return RecentMatch(
            matchId = item.optString("matchId"),
            roomCode = item.optString("roomCode"),
            startedAt = item.optLong("startedAt"),
            updatedAt = item.optLong("updatedAt"),
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
            wins = firstInt(stats, "wins", "winCount", "totalWins")
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
            )
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

    fun parseResetPassword(root: JSONObject): AdminAccount {
        val data = root.optJSONObject("data") ?: root
        val account = data.optJSONObject("account")
            ?: data.optJSONObject("user")
            ?: data.optJSONObject("updatedAccount")
            ?: data
        return parseAdminAccount(account)
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
            totalScore = firstInt(item, "totalScore", "score", "points"),
            games = firstInt(item, "games", "gameCount", "totalGames"),
            wins = firstInt(item, "wins", "winCount", "totalWins")
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
