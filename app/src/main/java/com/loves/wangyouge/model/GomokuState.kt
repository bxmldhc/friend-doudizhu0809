package com.loves.wangyouge.model

const val GOMOKU_BOARD_SIZE = 15

enum class RemoteGameType {
    DOUDIZHU,
    GOMOKU,
    LOVE_LETTER;

    companion object {
        fun fromWire(value: String?): RemoteGameType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || (it == LOVE_LETTER && value.equals("love-letter", true)) }
                ?: DOUDIZHU
    }
}

data class LoveLetterCard(
    val id: String,
    val value: Int,
    val name: String
)

data class LoveLetterPlayer(
    val seat: Int,
    val alive: Boolean,
    val protected: Boolean,
    val hand: List<LoveLetterCard>,
    val handCount: Int,
    val tokens: Int
)

data class LoveLetterState(
    val phase: String,
    val players: List<LoveLetterPlayer>,
    val deckCount: Int,
    val turnIndex: Int,
    val gameNumber: Int,
    val message: String,
    val winnerIndex: Int?,
    val actionDeadlineAt: Long? = null,
    val readySeats: List<Boolean> = emptyList(),
    val lastAction: LoveLetterActionResult? = null
)

data class LoveLetterActionResult(
    val seat: Int,
    val card: String,
    val targetSeat: Int?,
    val guessValue: Int?,
    val result: String
)

enum class GomokuPhase {
    PLAYING,
    FINISHED
}

data class GomokuSeat(
    val index: Int,
    val name: String,
    val isAi: Boolean,
    val isAway: Boolean,
    val stone: String,
    val score: Int,
    val playerId: String = ""
)

data class GomokuPoint(
    val row: Int,
    val col: Int
)

data class GomokuState(
    val phase: GomokuPhase,
    val board: List<List<Int>>,
    val seats: List<GomokuSeat>,
    val turnIndex: Int,
    val winnerIndex: Int?,
    val winningLine: List<GomokuPoint>,
    val moveCount: Int,
    val message: String,
    val gameNumber: Int,
    val readySeats: List<Boolean>,
    val actionDeadlineAt: Long? = null
)
