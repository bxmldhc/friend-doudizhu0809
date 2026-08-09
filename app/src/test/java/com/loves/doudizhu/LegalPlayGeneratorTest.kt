package com.loves.doudizhu

import com.loves.doudizhu.logic.ComboType
import com.loves.doudizhu.logic.HandEvaluator
import com.loves.doudizhu.logic.LegalPlayGenerator
import com.loves.doudizhu.model.parseCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalPlayGeneratorTest {

    @Test
    fun allPlaysContainsStraight() {
        val hand = parseCards("3H 4S 5D 6C 7H 8S")
        val plays = LegalPlayGenerator.allPlays(hand)
        val straight = plays.firstOrNull { HandEvaluator.classify(it)?.type == ComboType.STRAIGHT }
        assertNotNull(straight)
        assertEquals(5, straight?.size)
    }

    @Test
    fun beatsReturnsOnlyHigherSameType() {
        val hand = parseCards("8H 8S 9H 9S")
        val last = parseCards("7H 7S")
        val plays = LegalPlayGenerator.beats(hand, last)
        assertTrue(plays.isNotEmpty())
        assertTrue(plays.all { HandEvaluator.classify(it)?.type == ComboType.PAIR })
        assertTrue(plays.all { (HandEvaluator.classify(it)?.primaryRank?.order ?: 0) > 7 })
    }

    @Test
    fun hintChoosesSmallestMove() {
        val hand = parseCards("3H 4S 5D 6C 7H")
        val hint = LegalPlayGenerator.hint(hand, emptyList())
        assertNotNull(hint)
        assertEquals(1, hint?.size)
        assertEquals(3, hint?.first()?.rank?.order)
    }
}
