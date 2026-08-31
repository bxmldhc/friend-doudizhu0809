package com.loves.wangyouge.logic

import kotlin.random.Random

data class Game2048State(
    val cells: List<Int> = List(16) { 0 },
    val score: Int = 0,
    val moves: Int = 0,
    val won: Boolean = false,
    val ended: Boolean = false
) {
    val maxTile: Int get() = cells.maxOrNull() ?: 0
}

enum class Move2048 { LEFT, RIGHT, UP, DOWN }

object Game2048 {
    fun newGame(random: Random = Random.Default): Game2048State {
        var state = Game2048State()
        repeat(2) { state = addTile(state, random) }
        return state
    }

    fun move(state: Game2048State, direction: Move2048, random: Random = Random.Default): Game2048State {
        if (state.ended) return state
        val next = MutableList(16) { 0 }
        var changed = false
        var gained = 0
        for (line in 0 until 4) {
            val ids = lineIds(line, direction)
            val values = ids.map { state.cells[it] }.filter { it != 0 }
            val merged = mutableListOf<Int>()
            var index = 0
            while (index < values.size) {
                if (index + 1 < values.size && values[index] == values[index + 1]) {
                    val value = values[index] * 2
                    merged += value
                    gained += value
                    index += 2
                } else {
                    merged += values[index]
                    index++
                }
            }
            ids.forEachIndexed { i, id -> next[id] = merged.getOrNull(i) ?: 0 }
            if (ids.map { state.cells[it] } != ids.map { next[it] }) changed = true
        }
        if (!changed) return state
        var result = state.copy(cells = next, score = state.score + gained, moves = state.moves + 1)
        result = addTile(result, random)
        return result.copy(
            won = state.won || result.cells.any { it >= 2048 },
            ended = result.cells.none { it == 0 } && !canMove(result.cells)
        )
    }

    private fun addTile(state: Game2048State, random: Random): Game2048State {
        val empty = state.cells.indices.filter { state.cells[it] == 0 }
        if (empty.isEmpty()) return state
        val cells = state.cells.toMutableList()
        cells[empty[random.nextInt(empty.size)]] = if (random.nextFloat() < .9f) 2 else 4
        return state.copy(cells = cells)
    }

    private fun lineIds(line: Int, direction: Move2048): List<Int> = when (direction) {
        Move2048.LEFT -> (0..3).map { line * 4 + it }
        Move2048.RIGHT -> (0..3).map { line * 4 + it }.reversed()
        Move2048.UP -> (0..3).map { line + it * 4 }
        Move2048.DOWN -> (0..3).map { line + it * 4 }.reversed()
    }

    private fun canMove(cells: List<Int>): Boolean = cells.indices.any { i ->
        (i % 4 < 3 && cells[i] == cells[i + 1]) || (i < 12 && cells[i] == cells[i + 4])
    }
}
