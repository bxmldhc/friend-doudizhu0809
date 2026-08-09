package com.loves.doudizhu

import com.loves.doudizhu.logic.DoudizhuEngine
import com.loves.doudizhu.logic.Phase
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

    @Test
    fun biddingCreatesLandlordWithBottomCards() {
        val engine = newEngine()
        engine.startNewGame()
        repeat(3) { engine.submitCall(true) }

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
        repeat(3) { engine.submitCall(true) }

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
        repeat(3) { engine.submitCall(true) }

        val hand = engine.state.currentSeat.hand
        val error = engine.validateCurrentPlay(parseCards("3H 4H"))
        assertNotNull(error)
        assertEquals(hand, engine.state.currentSeat.hand)
    }

    @Test
    fun hintIsAlwaysAvailableForLeadingPlayer() {
        val engine = newEngine()
        engine.startNewGame()
        repeat(3) { engine.submitCall(true) }

        val hint = engine.hintCards()
        assertNotNull(hint)
        assertTrue(hint!!.isNotEmpty())
        assertTrue(engine.state.currentSeat.hand.containsAll(hint))
    }
}
