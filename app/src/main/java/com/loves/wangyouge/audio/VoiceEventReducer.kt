package com.loves.wangyouge.audio

import com.loves.wangyouge.logic.EngineState
import com.loves.wangyouge.logic.Phase
import com.loves.wangyouge.logic.SeatAction

class VoiceEventReducer {
    private var roomCode = ""
    private var previousState: EngineState? = null
    private var lastEventId: String? = null
    private var suppressNextSnapshot = false

    fun reset(roomCode: String, suppressNextSnapshot: Boolean = false) {
        this.roomCode = roomCode
        previousState = null
        lastEventId = null
        this.suppressNextSnapshot = suppressNextSnapshot
    }

    fun suppressNextSnapshot() {
        suppressNextSnapshot = true
    }

    fun reduce(roomCode: String, state: EngineState, mySeat: Int): List<AudioCue> {
        if (this.roomCode != roomCode) reset(roomCode)

        val previous = previousState
        if (suppressNextSnapshot) {
            previousState = state
            lastEventId = state.lastEvent?.id
            suppressNextSnapshot = false
            return emptyList()
        }

        val cues = buildList {
            val event = state.lastEvent
            if (event != null && event.id != lastEventId) {
                if (event.type == "PLAY") {
                    addAll(playCues(event.comboType, event.primaryRank))
                } else {
                    actionCue(event.type)?.let(::add)
                }
                if (event.type == "PLAY" && event.seat == mySeat) {
                    handCountCue(state.seats.getOrNull(mySeat)?.handCount)?.let(::add)
                }
                lastEventId = event.id
            } else if (event == null && previous != null) {
                fallbackAction(previous, state)?.let(::add)
                val previousCount = previous.seats.getOrNull(mySeat)?.handCount
                val currentCount = state.seats.getOrNull(mySeat)?.handCount
                if (currentCount != previousCount) handCountCue(currentCount)?.let(::add)
            }

            val becameMyTurn = state.phase != Phase.FINISHED &&
                state.turnIndex == mySeat &&
                (previous == null ||
                    previous.gameNumber != state.gameNumber ||
                    previous.phase != state.phase ||
                    previous.turnIndex != mySeat)
            if (becameMyTurn) add(AudioCue.YOUR_TURN)
        }

        previousState = state
        return cues.distinct()
    }

    private fun fallbackAction(previous: EngineState, state: EngineState): AudioCue? {
        if (previous.gameNumber != state.gameNumber || previous.phase == Phase.FINISHED) return null
        val actingSeat = previous.turnIndex
        val progressed = state.phase != previous.phase ||
            state.turnIndex != previous.turnIndex ||
            state.actedCount != previous.actedCount ||
            state.seats.getOrNull(actingSeat)?.handCount !=
            previous.seats.getOrNull(actingSeat)?.handCount
        if (!progressed) return null
        return when (state.lastActionsBySeat.getOrNull(actingSeat)) {
            SeatAction.CALL -> AudioCue.CALL_LANDLORD
            SeatAction.ROB -> AudioCue.ROB_LANDLORD
            SeatAction.NOT_CALL -> AudioCue.NOT_CALL
            SeatAction.NOT_ROB -> AudioCue.NOT_ROB
            SeatAction.PASS -> AudioCue.PASS
            else -> null
        }
    }

    private fun actionCue(type: String): AudioCue? = when (type) {
        "CALL" -> AudioCue.CALL_LANDLORD
        "ROB" -> AudioCue.ROB_LANDLORD
        "NOT_CALL" -> AudioCue.NOT_CALL
        "NOT_ROB" -> AudioCue.NOT_ROB
        "PASS" -> AudioCue.PASS
        else -> null
    }

    private fun playCues(comboType: String?, primaryRank: Int?): List<AudioCue> = when (comboType) {
        "SINGLE" -> rankCue(primaryRank)?.let(::listOf).orEmpty()
        "PAIR" -> listOfNotNull(AudioCue.PLAY_PAIR, rankCue(primaryRank))
        "TRIPLE" -> listOfNotNull(AudioCue.PLAY_TRIPLE, rankCue(primaryRank))
        "TRIPLE_SINGLE" -> listOf(AudioCue.PLAY_TRIPLE_SINGLE)
        "TRIPLE_PAIR" -> listOf(AudioCue.PLAY_TRIPLE_PAIR)
        "STRAIGHT" -> listOf(AudioCue.PLAY_STRAIGHT)
        "PAIR_STRAIGHT" -> listOf(AudioCue.PLAY_PAIR_STRAIGHT)
        "AIRPLANE" -> listOf(AudioCue.PLAY_AIRPLANE)
        "AIRPLANE_SINGLE" -> listOf(AudioCue.PLAY_AIRPLANE_SINGLE)
        "AIRPLANE_PAIR" -> listOf(AudioCue.PLAY_AIRPLANE_PAIR)
        "FOUR_TWO_SINGLE" -> listOf(AudioCue.PLAY_FOUR_TWO_SINGLE)
        "FOUR_TWO_PAIR" -> listOf(AudioCue.PLAY_FOUR_TWO_PAIR)
        "BOMB" -> listOf(AudioCue.PLAY_BOMB)
        "ROCKET" -> listOf(AudioCue.PLAY_ROCKET)
        else -> emptyList()
    }

    private fun rankCue(rank: Int?): AudioCue? = when (rank) {
        3 -> AudioCue.RANK_3
        4 -> AudioCue.RANK_4
        5 -> AudioCue.RANK_5
        6 -> AudioCue.RANK_6
        7 -> AudioCue.RANK_7
        8 -> AudioCue.RANK_8
        9 -> AudioCue.RANK_9
        10 -> AudioCue.RANK_10
        11 -> AudioCue.RANK_J
        12 -> AudioCue.RANK_Q
        13 -> AudioCue.RANK_K
        14 -> AudioCue.RANK_A
        15 -> AudioCue.RANK_2
        16 -> AudioCue.RANK_SMALL_JOKER
        17 -> AudioCue.RANK_BIG_JOKER
        else -> null
    }

    private fun handCountCue(handCount: Int?): AudioCue? = when (handCount) {
        2 -> AudioCue.CARDS_LEFT_TWO
        1 -> AudioCue.CARDS_LEFT_ONE
        else -> null
    }
}
