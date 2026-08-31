package com.loves.wangyouge.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Game2048Test {
    @Test
    fun mergesEachPairOnlyOnce() {
        val state = Game2048State(cells = listOf(2, 2, 2, 2) + List(12) { 0 })
        val result = Game2048.move(state, Move2048.LEFT, kotlin.random.Random(1))
        assertEquals(listOf(4, 4, 0, 0), result.cells.take(4))
        assertEquals(8, result.score)
    }

    @Test
    fun movesVerticallyAndDoesNotChangeWhenBlocked() {
        val state = Game2048State(cells = listOf(2, 0, 0, 0, 2, 0, 0, 0) + List(8) { 0 })
        val result = Game2048.move(state, Move2048.UP, kotlin.random.Random(2))
        assertEquals(4, result.cells[0])
        assertEquals(4, result.score)

        val blocked = Game2048State(cells = listOf(2, 4, 8, 16) + List(12) { 0 })
        assertEquals(blocked, Game2048.move(blocked, Move2048.LEFT, kotlin.random.Random(3)))
        assertTrue(blocked.cells.none { it == 0 }.not())
    }
}
