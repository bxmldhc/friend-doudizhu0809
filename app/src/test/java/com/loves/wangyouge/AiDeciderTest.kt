package com.loves.wangyouge

import com.loves.wangyouge.ai.AiAction
import com.loves.wangyouge.ai.AiDecider
import com.loves.wangyouge.logic.EngineState
import com.loves.wangyouge.logic.Phase
import com.loves.wangyouge.logic.SeatState
import com.loves.wangyouge.model.AiDifficulty
import com.loves.wangyouge.model.parseCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AiDeciderTest {

    private fun playingState(
        hand: String,
        last: String = ""
    ): EngineState {
        val seats = listOf(
            SeatState(0, "玩家1", true, parseCards(hand), false, 0),
            SeatState(1, "小智", false, parseCards("3H 3S 4H 4S"), false, 0),
            SeatState(2, "阿强", false, parseCards("5H 5S 6H 6S"), false, 0)
        )
        return EngineState(
            phase = Phase.PLAYING,
            seats = seats,
            turnIndex = 0,
            lastPlay = if (last.isEmpty()) emptyList() else parseCards(last),
            lastPlayerIndex = if (last.isEmpty()) null else 2,
            landlordIndex = 0,
            bidCount = 1
        )
    }

    private fun farmerPlayingState(
        hand: String,
        last: String,
        lastPlayerIndex: Int = 2,
        landlordHand: String
    ): EngineState {
        val seats = listOf(
            SeatState(
                0,
                "鐜╁1",
                true,
                parseCards(landlordHand),
                true,
                0
            ),
            SeatState(
                1,
                "灏忔櫤",
                false,
                parseCards(hand),
                false,
                0
            ),
            SeatState(
                2,
                "闃垮己",
                false,
                parseCards("10H 10S JH JS"),
                false,
                0
            )
        )
        return EngineState(
            phase = Phase.PLAYING,
            seats = seats,
            turnIndex = 1,
            lastPlay = parseCards(last),
            lastPlayerIndex = lastPlayerIndex,
            landlordIndex = 0,
            bidCount = 1
        )
    }

    @Test
    fun mediumAlwaysPlaysWhenItCanBeat() {
        val state = playingState(hand = "8H 8S 9H", last = "7H 7S")
        val action = AiDecider.decide(state, AiDifficulty.MEDIUM, Random(1))
        assertTrue(action is AiAction.Play)
        assertTrue((action as AiAction.Play).cards.isNotEmpty())
    }

    @Test
    fun hardLeadingChoosesAValidPlay() {
        val state = playingState(hand = "3H 4S 5D 6C 7H")
        val action = AiDecider.decide(state, AiDifficulty.HARD, Random(1))
        assertTrue(action is AiAction.Play)
        assertTrue((action as AiAction.Play).cards.isNotEmpty())
    }

    @Test
    fun farmerPassesWhenTeammateHasTheTrick() {
        val state = farmerPlayingState(
            hand = "8H 9H",
            last = "7H",
            landlordHand = "3H 3S 4H 4S 5H 5S 6H 6S"
        )

        val action = AiDecider.decide(state, AiDifficulty.HARD, Random(1))

        assertEquals(AiAction.Pass, action)
    }

    @Test
    fun farmerBlocksTeammatePlayWhenLandlordHasThreeCards() {
        val state = farmerPlayingState(
            hand = "8H 9H",
            last = "7H",
            landlordHand = "AH AS"
        )

        val action = AiDecider.decide(state, AiDifficulty.HARD, Random(1))

        assertTrue(action is AiAction.Play)
        assertEquals(parseCards("9H"), (action as AiAction.Play).cards)
    }

    @Test
    fun farmerStillAnswersLandlordWhenLandlordIsAboutToWin() {
        val state = farmerPlayingState(
            hand = "8H 9H",
            last = "7H",
            lastPlayerIndex = 0,
            landlordHand = "AH AS"
        )

        val action = AiDecider.decide(state, AiDifficulty.MEDIUM, Random(1))

        assertTrue(action is AiAction.Play)
    }

    @Test
    fun easyBiddingReturnsACallDecision() {
        val seats = listOf(
            SeatState(0, "玩家1", true, parseCards("3H 4S 5D 6C 7H"), false, 0),
            SeatState(1, "小智", false, parseCards("3H 3S 4H 4S"), false, 0),
            SeatState(2, "阿强", false, parseCards("5H 5S 6H 6S"), false, 0)
        )
        val state = EngineState(
            phase = Phase.BIDDING,
            seats = seats,
            turnIndex = 1
        )
        val action = AiDecider.decide(state, AiDifficulty.EASY, Random(1))
        assertTrue(action is AiAction.Call)
    }
}
