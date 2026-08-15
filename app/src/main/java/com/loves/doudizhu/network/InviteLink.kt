package com.loves.doudizhu.network

object InviteLink {
    fun create(roomCode: String): String = "friendddz://join?room=${roomCode.filter(Char::isDigit).take(6)}"

    fun parse(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
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
