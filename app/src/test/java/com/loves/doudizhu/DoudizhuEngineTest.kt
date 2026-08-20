package com.loves.doudizhu

import com.loves.doudizhu.logic.DoudizhuEngine
import com.loves.doudizhu.logic.Phase
import com.loves.doudizhu.logic.SeatAction
import com.loves.doudizhu.model.AiDifficulty
import com.loves.doudizhu.model.GameConfig
import com.loves.doudizhu.model.GameMode
import com.loves.doudizhu.model.parseCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DoudizhuEngineTest {

    private fun newEngine() = DoudizhuEngine(
        config = GameConfig(
            humanCount = 1,
            difficulty = AiDifficulty.MEDIUM,
            mode = GameMode.HOTSEAT
        ),
        random = Random(42)
    )

    private fun confirmOpeningPlayerAsLandlord(engine: DoudizhuEngine) {
        engine.submitCall(true)
        engine.submitCall(false)
        engine.submitCall(false)
    }

    @Test
    fun biddingCreatesLandlordWithBottomCards() {
        val engine = newEngine()
        engine.startNewGame()
        confirmOpeningPlayerAsLandlord(engine)

        assertEquals(Phase.PLAYING, engine.state.phase)
        val landlord = engine.state.landlordIndex
        assertNotNull(landlord)
        assertEquals(20, engine.state.seats[landlord!!].hand.size)
        assertTrue(engine.state.seats[landlord].isLandlord)
        assertEquals(17, engine.state.seats[(landlord + 1) % 3].hand.size)
        assertEquals(17, engine.state.seats[(landlord + 2) % 3].hand.size)
        assertEquals(3, engine.state.bottomCards.size)
    }

    @Test
    fun twoPassesReturnLeadToPreviousPlayer() {
        val engine = newEngine()
        engine.startNewGame()
        confirmOpeningPlayerAsLandlord(engine)

        val landlord = engine.state.landlordIndex!!
        val firstCard = engine.state.seats[landlord].hand.first()
        engine.submitPlay(listOf(firstCard))

        assertEquals(landlord, engine.state.lastPlayerIndex)
        assertFalse(engine.state.isLeadingTurn())

        engine.submitPass()
        engine.submitPass()

        assertEquals(landlord, engine.state.turnIndex)
        assertTrue(engine.state.lastPlay.isEmpty())
        assertTrue(engine.state.isLeadingTurn())
    }

    @Test
    fun invalidSelectionIsRejectedWithoutChangingState() {
        val engine = newEngine()
        engine.startNewGame()
        confirmOpeningPlayerAsLandlord(engine)

        val hand = engine.state.currentSeat.hand
        val error = engine.validateCurrentPlay(parseCards("3H 4H"))
        assertNotNull(error)
        assertEquals(hand, engine.state.currentSeat.hand)
    }

    @Test
    fun hintIsAlwaysAvailableForLeadingPlayer() {
        val engine = newEngine()
        engine.startNewGame()
        confirmOpeningPlayerAsLandlord(engine)

        val hint = engine.hintCards()
        assertNotNull(hint)
        assertTrue(hint!!.isNotEmpty())
        assertTrue(engine.state.currentSeat.hand.containsAll(hint))
    }

    @Test
    fun eachRobDoublesMultiplierAndActionsAreRecorded() {
        val engine = newEngine()
        engine.startNewGame()
        val openingSeat = engine.state.turnIndex

        engine.submitCall(true)
        assertEquals(SeatAction.CALL, engine.state.lastActionsBySeat[openingSeat])
        assertEquals(1, engine.state.bidMultiplier)

        val challenger = engine.state.turnIndex
        engine.submitCall(true)
        assertEquals(SeatAction.ROB, engine.state.lastActionsBySeat[challenger])
        assertEquals(2, engine.state.bidMultiplier)

        engine.submitCall(false)
        assertEquals(Phase.BIDDING, engine.state.phase)
        engine.submitCall(true)

        assertEquals(Phase.PLAYING, engine.state.phase)
        assertEquals(openingSeat, engine.state.landlordIndex)
        assertEquals(4, engine.state.multiplier)
    }

    @Test
    fun passReplacesPreviousPlayWithPassAction() {
        val engine = newEngine()
        engine.startNewGame()
        confirmOpeningPlayerAsLandlord(engine)
        val landlord = engine.state.landlordIndex!!
        engine.submitPlay(listOf(engine.state.seats[landlord].hand.first()))

        val passingSeat = engine.state.turnIndex
        engine.submitPass()

        assertEquals(SeatAction.PASS, engine.state.lastActionsBySeat[passingSeat])
    }

    @Test
    fun allPlayersNotCallingRedealsWithoutAdvancingGameNumber() {
        val engine = newEngine()
        engine.startNewGame()
        val gameNumber = engine.state.gameNumber
        val originalHands = engine.state.seats.map { seat -> seat.hand }

        repeat(3) { engine.submitCall(false) }

        assertEquals(Phase.BIDDING, engine.state.phase)
        assertEquals(gameNumber, engine.state.gameNumber)
        assertEquals(0, engine.state.actedCount)
        assertNull(engine.state.bidCandidateIndex)
        assertEquals(listOf(17, 17, 17), engine.state.seats.map { it.hand.size })
        assertEquals(3, engine.state.bottomCards.size)
        assertTrue(engine.state.seats.map { it.hand } != originalHands)
        assertTrue(engine.state.message.contains("重新发牌"))
    }
}
