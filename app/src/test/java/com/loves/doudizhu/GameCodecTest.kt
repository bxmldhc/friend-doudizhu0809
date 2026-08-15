package com.loves.doudizhu

import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.logic.Phase
import com.loves.doudizhu.logic.ServerGameEvent
import com.loves.doudizhu.model.Rank
import com.loves.doudizhu.network.GameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameCodecTest {
    @Test
    fun playEventMetadataSurvivesRoundTrip() {
        val state = EngineState(
            phase = Phase.PLAYING,
            seats = emptyList(),
            turnIndex = 0,
            lastEvent = ServerGameEvent(
                id = "123456:1:9",
                type = "PLAY",
                seat = 2,
                automatic = true,
                comboType = "TRIPLE_PAIR",
                primaryRank = 12,
                comboLength = 1
            )
        )

        val decoded = GameCodec.decodeState(GameCodec.encodeState(state))

        assertEquals(state.lastEvent, decoded.lastEvent)
    }

    @Test
    fun oldPlayEventWithoutMetadataRemainsCompatible() {
        val json = GameCodec.encodeState(
            EngineState(
                phase = Phase.PLAYING,
                seats = emptyList(),
                turnIndex = 0,
                lastEvent = ServerGameEvent(
                    id = "123456:1:10",
                    type = "PLAY",
                    seat = 1,
                    automatic = false
                )
            )
        )

        val event = GameCodec.decodeState(json).lastEvent

        assertNull(event?.comboType)
        assertNull(event?.primaryRank)
        assertNull(event?.comboLength)
    }

    @Test
    fun seenRanksSurviveRoundTrip() {
        val state = EngineState(
            phase = Phase.PLAYING,
            seats = emptyList(),
            turnIndex = 0,
            seenRanks = mapOf(
                Rank.R3 to 2,
                Rank.R2 to 1,
                Rank.BIG_JOKER to 1
            )
        )

        val decoded = GameCodec.decodeState(GameCodec.encodeState(state))

        assertEquals(state.seenRanks, decoded.seenRanks)
    }
}
