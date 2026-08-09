package com.loves.doudizhu

import com.loves.doudizhu.logic.ComboType
import com.loves.doudizhu.logic.HandEvaluator
import com.loves.doudizhu.model.parseCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandEvaluatorTest {

    @Test
    fun detectsRocket() {
        val pattern = HandEvaluator.classify(parseCards("X B"))
        assertNotNull(pattern)
        assertEquals(ComboType.ROCKET, pattern?.type)
    }

    @Test
    fun detectsBomb() {
        val pattern = HandEvaluator.classify(parseCards("3H 3S 3D 3C"))
        assertNotNull(pattern)
        assertEquals(ComboType.BOMB, pattern?.type)
    }

    @Test
    fun detectsStraight() {
        val pattern = HandEvaluator.classify(parseCards("3H 4S 5D 6C 7H"))
        assertNotNull(pattern)
        assertEquals(ComboType.STRAIGHT, pattern?.type)
        assertEquals(5, pattern?.length)
    }

    @Test
    fun straightCannotContainTwo() {
        val pattern = HandEvaluator.classify(parseCards("JH QS KD AC 2H"))
        assertNull(pattern)
    }

    @Test
    fun detectsPairStraight() {
        val pattern = HandEvaluator.classify(parseCards("3H 3S 4H 4S 5H 5S"))
        assertNotNull(pattern)
        assertEquals(ComboType.PAIR_STRAIGHT, pattern?.type)
        assertEquals(3, pattern?.length)
    }

    @Test
    fun detectsTripleWithSingle() {
        val pattern = HandEvaluator.classify(parseCards("3H 3S 3D 4H"))
        assertNotNull(pattern)
        assertEquals(ComboType.TRIPLE_SINGLE, pattern?.type)
    }

    @Test
    fun detectsAirplaneWithSingles() {
        val pattern = HandEvaluator.classify(parseCards("3H 3S 3D 4H 4S 4D 5H 6H"))
        assertNotNull(pattern)
        assertEquals(ComboType.AIRPLANE_SINGLE, pattern?.type)
        assertEquals(2, pattern?.length)
    }

    @Test
    fun sameTypeHigherRankBeats() {
        val pair7 = HandEvaluator.classify(parseCards("7H 7S"))!!
        val pair8 = HandEvaluator.classify(parseCards("8H 8S"))!!
        assertTrue(HandEvaluator.canBeat(pair8, pair7))
        assertFalse(HandEvaluator.canBeat(pair7, pair8))
    }

    @Test
    fun bombBeatsOrdinaryTypeAndRocketBeatsBomb() {
        val triple = HandEvaluator.classify(parseCards("7H 7S 7D"))!!
        val bomb = HandEvaluator.classify(parseCards("3H 3S 3D 3C"))!!
        val rocket = HandEvaluator.classify(parseCards("X B"))!!
        assertTrue(HandEvaluator.canBeat(bomb, triple))
        assertTrue(HandEvaluator.canBeat(rocket, bomb))
        assertFalse(HandEvaluator.canBeat(triple, rocket))
    }

    @Test
    fun containsAllRespectsMultiset() {
        val hand = parseCards("3H 4H 5H 6H 7H 8H")
        assertTrue(HandEvaluator.containsAll(hand, parseCards("3H 4H 5H")))
        assertFalse(HandEvaluator.containsAll(hand, parseCards("3H 3S 4H")))
    }
}
