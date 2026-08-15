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
    val nickname: String
)

data class AccountStats(
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
            get() = statusCode == 401 || statusCode == 403 ||
                code.equals("UNAUTHORIZED", ignoreCase = true) ||
                code.equals("INVALID_TOKEN", ignoreCase = true)
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
        require(token.isNotBlank()) { "账号服务器未返回登录令牌" }
        require(accountId.isNotBlank()) { "账号服务器未返回账号 ID" }
        require(nickname.isNotBlank()) { "账号服务器未返回昵称" }
        return AuthSession(token, accountId, nickname)
    }

    fun parseStats(root: JSONObject): AccountStats {
        val data = root.optJSONObject("data") ?: root
        val stats = data.optJSONObject("stats") ?: data
        return AccountStats(
            totalScore = firstInt(stats, "totalScore", "score", "points"),
            games = firstInt(stats, "games", "gameCount", "totalGames"),
            wins = firstInt(stats, "wins", "winCount", "totalWins")
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
}
