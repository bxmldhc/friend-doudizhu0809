package com.loves.wangyouge

import com.loves.wangyouge.logic.ComboType
import com.loves.wangyouge.logic.HandEvaluator
import com.loves.wangyouge.logic.LegalPlayGenerator
import com.loves.wangyouge.model.parseCards
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

    @Test
    fun allGeneratedPlaysAreValidAndIncludeWingChoices() {
        val hand = parseCards("3H 3S 3D 4H 4S 4D 5H 5S 6H 6S 7H 8H 9H X B")
        val plays = LegalPlayGenerator.allPlays(hand)

        assertTrue(plays.all { HandEvaluator.classify(it) != null })
        val airplaneSingles = plays.filter {
            HandEvaluator.classify(it)?.type == ComboType.AIRPLANE_SINGLE
        }
        val airplanePairs = plays.filter {
            HandEvaluator.classify(it)?.type == ComboType.AIRPLANE_PAIR
        }
        assertEquals(21, airplaneSingles.size)
        assertEquals(1, airplanePairs.size)
        assertTrue(airplaneSingles.any { play -> play.any { it.rank.order == 16 } })
        assertTrue(airplaneSingles.any { play -> play.any { it.rank.order == 17 } })
    }
}
