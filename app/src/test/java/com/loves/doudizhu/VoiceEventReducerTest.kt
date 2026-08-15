package com.loves.doudizhu

import com.loves.doudizhu.audio.AudioCue
import com.loves.doudizhu.audio.VoiceEventReducer
import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.logic.Phase
import com.loves.doudizhu.logic.SeatAction
import com.loves.doudizhu.logic.SeatState
import com.loves.doudizhu.logic.ServerGameEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceEventReducerTest {
    private val reducer = VoiceEventReducer()

    @Test
    fun reconnectSnapshotEstablishesBaselineWithoutPlayingOldEvent() {
        reducer.reset("123456", suppressNextSnapshot = true)

        val cues = reducer.reduce(
            roomCode = "123456",
            state = state(
                turnIndex = 0,
                lastEvent = event("123456:1:8", "CALL", 2)
            ),
            mySeat = 0
        )

        assertEquals(emptyList<AudioCue>(), cues)
    }

    @Test
    fun authoritativeActionEventPlaysOnce() {
        reducer.reset("123456")
        val snapshot = state(
            turnIndex = 1,
            lastEvent = event("123456:1:1", "CALL", 0)
        )

        assertEquals(
            listOf(AudioCue.CALL_LANDLORD),
            reducer.reduce("123456", snapshot, mySeat = 2)
        )
        assertEquals(
            emptyList<AudioCue>(),
            reducer.reduce("123456", snapshot, mySeat = 2)
        )
    }

    @Test
    fun authoritativePlayEventMapsServerComboTypes() {
        val mappings = listOf(
            "SINGLE" to listOf(AudioCue.RANK_Q),
            "PAIR" to listOf(AudioCue.PLAY_PAIR, AudioCue.RANK_Q),
            "TRIPLE" to listOf(AudioCue.PLAY_TRIPLE, AudioCue.RANK_Q),
            "TRIPLE_SINGLE" to listOf(AudioCue.PLAY_TRIPLE_SINGLE),
            "TRIPLE_PAIR" to listOf(AudioCue.PLAY_TRIPLE_PAIR),
            "STRAIGHT" to listOf(AudioCue.PLAY_STRAIGHT),
            "PAIR_STRAIGHT" to listOf(AudioCue.PLAY_PAIR_STRAIGHT),
            "AIRPLANE" to listOf(AudioCue.PLAY_AIRPLANE),
            "AIRPLANE_SINGLE" to listOf(AudioCue.PLAY_AIRPLANE_SINGLE),
            "AIRPLANE_PAIR" to listOf(AudioCue.PLAY_AIRPLANE_PAIR),
            "FOUR_TWO_SINGLE" to listOf(AudioCue.PLAY_FOUR_TWO_SINGLE),
            "FOUR_TWO_PAIR" to listOf(AudioCue.PLAY_FOUR_TWO_PAIR),
            "BOMB" to listOf(AudioCue.PLAY_BOMB),
            "ROCKET" to listOf(AudioCue.PLAY_ROCKET)
        )

        mappings.forEachIndexed { index, (comboType, expectedCues) ->
            val cues = reducer.reduce(
                "123456",
                state(
                    phase = Phase.PLAYING,
                    turnIndex = 1,
                    lastEvent = event(
                        id = "123456:1:${index + 1}",
                        type = "PLAY",
                        seat = 0,
                        comboType = comboType,
                        primaryRank = 12
                    )
                ),
                mySeat = 2
            )
            assertEquals(expectedCues, cues)
        }
    }

    @Test
    fun singleRankMapsAllServerRankValues() {
        val expected = listOf(
            AudioCue.RANK_3,
            AudioCue.RANK_4,
            AudioCue.RANK_5,
            AudioCue.RANK_6,
            AudioCue.RANK_7,
            AudioCue.RANK_8,
            AudioCue.RANK_9,
            AudioCue.RANK_10,
            AudioCue.RANK_J,
            AudioCue.RANK_Q,
            AudioCue.RANK_K,
            AudioCue.RANK_A,
            AudioCue.RANK_2,
            AudioCue.RANK_SMALL_JOKER,
            AudioCue.RANK_BIG_JOKER
        )

        expected.forEachIndexed { index, cue ->
            val rank = index + 3
            assertEquals(
                listOf(cue),
                reducer.reduce(
                    "123456",
                    state(
                        phase = Phase.PLAYING,
                        turnIndex = 1,
                        lastEvent = event(
                            id = "123456:1:rank-$rank",
                            type = "PLAY",
                            seat = 0,
                            comboType = "SINGLE",
                            primaryRank = rank
                        )
                    ),
                    mySeat = 2
                )
            )
        }
    }

    @Test
    fun legacyPlayEventWithoutComboDoesNotGuessAudio() {
        reducer.reset("123456")

        val cues = reducer.reduce(
            "123456",
            state(
                phase = Phase.PLAYING,
                turnIndex = 1,
                lastEvent = event("123456:1:1", "PLAY", 0)
            ),
            mySeat = 2
        )

        assertEquals(emptyList<AudioCue>(), cues)
    }

    @Test
    fun ownPlayAnnouncesLowCardsAndNewTurn() {
        reducer.reset("123456")
        reducer.reduce(
            "123456",
            state(turnIndex = 0, handCounts = listOf(3, 17, 17)),
            mySeat = 0
        )

        val lowCardsCues = reducer.reduce(
            "123456",
            state(
                phase = Phase.PLAYING,
                turnIndex = 2,
                handCounts = listOf(2, 17, 17),
                lastEvent = event("123456:1:2", "PLAY", 0)
            ),
            mySeat = 0
        )
        assertEquals(listOf(AudioCue.CARDS_LEFT_TWO), lowCardsCues)

        val myTurnCues = reducer.reduce(
            "123456",
            state(
                phase = Phase.PLAYING,
                turnIndex = 0,
                handCounts = listOf(2, 16, 17),
                lastEvent = event("123456:1:3", "PLAY", 1)
            ),
            mySeat = 0
        )
        assertEquals(listOf(AudioCue.YOUR_TURN), myTurnCues)
    }

    @Test
    fun legacyServerFallsBackToSeatActions() {
        reducer.reset("123456")
        reducer.reduce(
            "123456",
            state(turnIndex = 0, actedCount = 0),
            mySeat = 2
        )

        val cues = reducer.reduce(
            "123456",
            state(
                turnIndex = 2,
                actedCount = 1,
                actions = listOf(SeatAction.NOT_CALL, null, null)
            ),
            mySeat = 2
        )

        assertEquals(listOf(AudioCue.NOT_CALL, AudioCue.YOUR_TURN), cues)
    }

    @Test
    fun changingRoomClearsEventDeduplication() {
        val snapshot = state(
            turnIndex = 1,
            lastEvent = event("shared-event-id", "PASS", 0)
        )
        reducer.reduce("123456", snapshot, mySeat = 2)

        assertEquals(
            listOf(AudioCue.PASS),
            reducer.reduce("654321", snapshot, mySeat = 2)
        )
    }

    private fun state(
        phase: Phase = Phase.BIDDING,
        turnIndex: Int,
        handCounts: List<Int> = listOf(17, 17, 17),
        actedCount: Int = 0,
        actions: List<SeatAction?> = List(3) { null },
        lastEvent: ServerGameEvent? = null
    ): EngineState = EngineState(
        phase = phase,
        seats = handCounts.mapIndexed { index, count ->
            SeatState(
                index = index,
                name = "player-$index",
                isHuman = true,
                hand = emptyList(),
                isLandlord = false,
                score = 0,
                handCount = count
            )
        },
        turnIndex = turnIndex,
        actedCount = actedCount,
        lastActionsBySeat = actions,
        gameNumber = 1,
        lastEvent = lastEvent
    )

    private fun event(
        id: String,
        type: String,
        seat: Int,
        comboType: String? = null,
        primaryRank: Int? = null
    ): ServerGameEvent =
        ServerGameEvent(
            id = id,
            type = type,
            seat = seat,
            automatic = false,
            comboType = comboType,
            primaryRank = primaryRank
        )
}
