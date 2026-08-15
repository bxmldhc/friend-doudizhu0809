package com.loves.doudizhu.audio

import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.logic.Phase
import com.loves.doudizhu.logic.SeatAction

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
                    playCue(event.comboType)?.let(::add)
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

    private fun playCue(comboType: String?): AudioCue? = when (comboType) {
        "SINGLE" -> AudioCue.PLAY_SINGLE
        "PAIR" -> AudioCue.PLAY_PAIR
        "TRIPLE", "TRIPLE_SINGLE", "TRIPLE_PAIR" -> AudioCue.PLAY_TRIPLE
        "STRAIGHT", "PAIR_STRAIGHT" -> AudioCue.PLAY_STRAIGHT
        "AIRPLANE", "AIRPLANE_SINGLE", "AIRPLANE_PAIR" -> AudioCue.PLAY_AIRPLANE
        "FOUR_TWO_SINGLE", "FOUR_TWO_PAIR" -> AudioCue.PLAY_FOUR_WITH_TWO
        "BOMB" -> AudioCue.PLAY_BOMB
        "ROCKET" -> AudioCue.PLAY_ROCKET
        else -> null
    }

    private fun handCountCue(handCount: Int?): AudioCue? = when (handCount) {
        2 -> AudioCue.CARDS_LEFT_TWO
        1 -> AudioCue.CARDS_LEFT_ONE
        else -> null
    }
}
