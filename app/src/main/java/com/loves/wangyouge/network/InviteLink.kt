package com.loves.wangyouge.network

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object InviteLink {
    private const val SHARE_BASE_URL = "http://114.55.142.169:18080"
    private val ROOM_CODE_PATTERN = Regex("""^\d{6}$""")

    fun create(roomCode: String): String =
        "wangyouge://join?room=${requireRoomCode(roomCode)}"

    fun createShareUrl(roomCode: String): String =
        "$SHARE_BASE_URL/invite/${requireRoomCode(roomCode)}"

    fun parse(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        return when {
            uri.scheme.equals("wangyouge", ignoreCase = true) &&
                uri.host.equals("join", ignoreCase = true) -> roomFromQuery(uri.rawQuery)

            uri.scheme.equals("intent", ignoreCase = true) &&
                uri.host.equals("join", ignoreCase = true) -> roomFromQuery(uri.rawQuery)

            (uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true)) -> {
                Regex("""^/invite/(\d{6})/?$""")
                    .matchEntire(uri.path.orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)
            }

            else -> null
        }
    }

    private fun roomFromQuery(rawQuery: String?): String? {
        val room = rawQuery.orEmpty()
            .split('&')
            .asSequence()
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size != 2) return@mapNotNull null
                val key = decodeQueryValue(pieces[0]) ?: return@mapNotNull null
                if (!key.equals("room", ignoreCase = true)) return@mapNotNull null
                decodeQueryValue(pieces[1])
            }
            .firstOrNull()
        return room?.takeIf(ROOM_CODE_PATTERN::matches)
    }

    private fun decodeQueryValue(value: String): String? =
        runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrNull()

    private fun requireRoomCode(roomCode: String): String {
        val normalized = roomCode.trim()
        require(ROOM_CODE_PATTERN.matches(normalized)) { "房号必须是六位数字" }
        return normalized
    }
}
