package com.loves.doudizhu.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val forceUpdate: Boolean,
    val releaseNotes: List<String>
)

class UpdateApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
) {
    suspend fun latest(baseUrl: String): ApiResult<AppUpdateInfo> {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/api/app/latest")
            .header("Accept", "application/json")
            .get()
            .build()
        return execute(request) { root -> UpdateResponseParser.parse(root) }
    }

    suspend fun download(
        info: AppUpdateInfo,
        targetDir: File,
        onProgress: (Int) -> Unit
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        runCatching {
            targetDir.mkdirs()
            val target = File(targetDir, "friend-doudizhu-${info.versionCode}.apk")
            val temporary = File(targetDir, "${target.name}.part")
            temporary.delete()

            val request = Request.Builder().url(info.apkUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("下载失败：服务器返回 ${response.code}")
                }
                val body = response.body ?: throw IOException("下载内容为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        var lastProgress = -1
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            if (total > 0) {
                                val progress = ((copied * 100) / total).toInt().coerceIn(0, 100)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
            }

            val actualHash = sha256(temporary)
            if (!actualHash.equals(info.sha256, ignoreCase = true)) {
                temporary.delete()
                throw IOException("安装包校验失败，请重新下载")
            }
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            onProgress(100)
            target
        }.fold(
            onSuccess = { ApiResult.Success(it) },
            onFailure = { ApiResult.Failure(it.message ?: "更新下载失败") }
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
                    continuation.resume(ApiResult.Failure(error.message ?: "无法检查更新"))
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
                                    error.first.ifBlank { "检查更新失败" },
                                    it.code,
                                    error.second
                                )
                            )
                        }
                        return
                    }
                    val result = runCatching { parser(json ?: JSONObject()) }.fold(
                        onSuccess = { ApiResult.Success(it) },
                        onFailure = {
                            ApiResult.Failure(it.message ?: "版本信息不完整", response.code)
                        }
                    )
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        })
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal object UpdateResponseParser {
    fun parse(root: JSONObject): AppUpdateInfo {
        val data = root.optJSONObject("data") ?: root
        val versionCode = data.optInt("versionCode", 0)
        val versionName = data.optString("versionName").trim()
        val apkUrl = data.optString("apkUrl").trim()
        val sha256 = data.optString("sha256").trim()
        require(versionCode > 0) { "服务器未返回有效版本号" }
        require(versionName.isNotBlank()) { "服务器未返回版本名称" }
        require(apkUrl.startsWith("http://") || apkUrl.startsWith("https://")) {
            "服务器未返回有效下载地址"
        }
        require(sha256.matches(Regex("^[A-Fa-f0-9]{64}$"))) {
            "服务器未返回有效安装包校验值"
        }
        val notes = data.optJSONArray("releaseNotes")
        return AppUpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256.lowercase(),
            forceUpdate = data.optBoolean("forceUpdate", false),
            releaseNotes = if (notes == null) {
                emptyList()
            } else {
                List(notes.length()) { notes.optString(it).trim() }.filter(String::isNotBlank)
            }
        )
    }
}
