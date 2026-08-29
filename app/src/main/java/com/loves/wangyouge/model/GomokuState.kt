package com.loves.wangyouge.model

const val GOMOKU_BOARD_SIZE = 15

enum class RemoteGameType {
    DOUDIZHU,
    GOMOKU;

    companion object {
        fun fromWire(value: String?): RemoteGameType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: DOUDIZHU
    }
}

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
    val score: Int
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
    val readySeats: List<Boolean>
)
