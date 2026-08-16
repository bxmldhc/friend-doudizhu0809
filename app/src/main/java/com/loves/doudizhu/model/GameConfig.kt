package com.loves.doudizhu.model

enum class AiDifficulty {
    EASY,
    MEDIUM,
    HARD
}

enum class GameMode {
    HOTSEAT,
    SINGLE
}

data class GameConfig(
    val humanCount: Int = 1,
    val difficulty: AiDifficulty = AiDifficulty.MEDIUM,
    val mode: GameMode = GameMode.HOTSEAT,
    val actionTimeoutSeconds: Int = 20
) {
    val aiCount: Int
        get() = (3 - humanCount).coerceIn(0, 2)

    fun normalized(): GameConfig {
        val fixedHumans = if (mode == GameMode.SINGLE) 1 else humanCount.coerceIn(1, 3)
        val fixedTimeout = actionTimeoutSeconds.takeIf { it in setOf(15, 20, 30, 45) } ?: 20
        return copy(humanCount = fixedHumans, actionTimeoutSeconds = fixedTimeout)
    }
}
