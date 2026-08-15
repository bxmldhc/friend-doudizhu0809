package com.loves.doudizhu.network

object InviteLink {
    private const val SHARE_BASE_URL = "http://114.55.142.169:18080"

    fun create(roomCode: String): String = "friendddz://join?room=${roomCode.filter(Char::isDigit).take(6)}"

    fun createShareUrl(roomCode: String): String =
        "$SHARE_BASE_URL/invite/${roomCode.filter(Char::isDigit).take(6)}"

    fun parse(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val invitePath = Regex("""https?://[^/]+/invite/(\d{6})(?:[/?#].*)?$""", RegexOption.IGNORE_CASE)
            .matchEntire(raw.trim())
            ?.groupValues
            ?.getOrNull(1)
        if (invitePath != null) return invitePath
        val prefix = "friendddz://join?"
        if (!raw.startsWith(prefix, ignoreCase = true)) return null
        val query = raw.substring(prefix.length)
        val room = query.split('&')
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2 && pieces[0].equals("room", ignoreCase = true)) pieces[1] else null
            }
            .firstOrNull()
            ?.filter(Char::isDigit)
        return room?.takeIf { it.length == 6 }
    }
}
