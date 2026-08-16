package com.loves.doudizhu.logic

import com.loves.doudizhu.model.Card
import com.loves.doudizhu.model.Rank

enum class ComboType {
    SINGLE,
    PAIR,
    TRIPLE,
    TRIPLE_SINGLE,
    TRIPLE_PAIR,
    STRAIGHT,
    PAIR_STRAIGHT,
    AIRPLANE,
    AIRPLANE_SINGLE,
    AIRPLANE_PAIR,
    FOUR_TWO_SINGLE,
    FOUR_TWO_PAIR,
    BOMB,
    ROCKET
}

data class CardPattern(
    val type: ComboType,
    val primaryRank: Rank,
    val length: Int,
    val cards: List<Card>
)

object HandEvaluator {

    private data class RankGroup(val rank: Rank, val count: Int)

    fun classify(cards: List<Card>): CardPattern? {
        if (cards.isEmpty()) return null
        val sorted = cards.sorted()
        val groups = sorted
            .groupBy { it.rank }
            .map { (rank, list) -> RankGroup(rank, list.size) }
        val ranks = groups.map { it.rank }.sortedBy { it.order }
        val sizes = groups.map { it.count }

        if (sorted.size == 2 &&
            groups.map { it.rank }.toSet() == setOf(Rank.SMALL_JOKER, Rank.BIG_JOKER)
        ) {
            return CardPattern(ComboType.ROCKET, Rank.BIG_JOKER, 1, sorted)
        }

        if (groups.size == 1) {
            val group = groups.first()
            val type = when (group.count) {
                1 -> ComboType.SINGLE
                2 -> ComboType.PAIR
                3 -> ComboType.TRIPLE
                4 -> ComboType.BOMB
                else -> return null
            }
            return CardPattern(type, group.rank, 1, sorted)
        }

        if (groups.size == 2) {
            val triple = groups.firstOrNull { it.count == 3 }
            if (triple != null) {
                val other = groups.first { it != triple }
                when (other.count) {
                    1 -> return CardPattern(ComboType.TRIPLE_SINGLE, triple.rank, 1, sorted)
                    2 -> return CardPattern(ComboType.TRIPLE_PAIR, triple.rank, 1, sorted)
                }
            }
        }

        val maxOrder = ranks.maxOfOrNull { it.order } ?: 0
        if (sizes.all { it == 1 } &&
            ranks.size >= 5 &&
            maxOrder < Rank.R2.order &&
            isConsecutive(ranks)
        ) {
            return CardPattern(ComboType.STRAIGHT, ranks.first(), ranks.size, sorted)
        }

        if (sizes.all { it == 2 } &&
            ranks.size >= 3 &&
            maxOrder < Rank.R2.order &&
            isConsecutive(ranks)
        ) {
            return CardPattern(ComboType.PAIR_STRAIGHT, ranks.first(), ranks.size, sorted)
        }

        val tripleGroups = groups.filter { it.count == 3 }
        val tripleRanks = tripleGroups.map { it.rank }.sortedBy { it.order }
        if (tripleGroups.size == groups.size &&
            tripleGroups.size >= 2 &&
            (tripleRanks.lastOrNull()?.order ?: 0) < Rank.R2.order &&
            isConsecutive(tripleRanks)
        ) {
            return CardPattern(ComboType.AIRPLANE, tripleRanks.first(), tripleRanks.size, sorted)
        }

        if (groups.count { it.count == 4 } == 1 &&
            groups.size == 3 &&
            groups.count { it.count == 1 } == 2
        ) {
            val bombRank = groups.first { it.count == 4 }.rank
            return CardPattern(ComboType.FOUR_TWO_SINGLE, bombRank, 1, sorted)
        }

        if (groups.count { it.count == 4 } == 1 &&
            groups.size == 3 &&
            groups.count { it.count == 2 } == 2
        ) {
            val bombRank = groups.first { it.count == 4 }.rank
            return CardPattern(ComboType.FOUR_TWO_PAIR, bombRank, 1, sorted)
        }

        if (tripleGroups.size >= 2 &&
            (tripleRanks.lastOrNull()?.order ?: 0) < Rank.R2.order &&
            isConsecutive(tripleRanks)
        ) {
            if (tripleGroups.size == groups.count { it.count == 1 }) {
                return CardPattern(ComboType.AIRPLANE_SINGLE, tripleRanks.first(), tripleRanks.size, sorted)
            }
            if (tripleGroups.size == groups.count { it.count == 2 }) {
                return CardPattern(ComboType.AIRPLANE_PAIR, tripleRanks.first(), tripleRanks.size, sorted)
            }
        }

        return null
    }

    fun canBeat(pattern: CardPattern, last: CardPattern): Boolean {
        if (pattern.type == ComboType.ROCKET) {
            return last.type != ComboType.ROCKET
        }
        if (last.type == ComboType.ROCKET) {
            return false
        }
        if (pattern.type == ComboType.BOMB && last.type != ComboType.BOMB) {
            return true
        }
        if (pattern.type == ComboType.BOMB && last.type == ComboType.BOMB) {
            return pattern.primaryRank.order > last.primaryRank.order
        }
        return pattern.type == last.type &&
            pattern.length == last.length &&
            pattern.primaryRank.order > last.primaryRank.order
    }

    fun containsAll(hand: List<Card>, wanted: List<Card>): Boolean {
        val wantedCounts = wanted.groupingBy { it.rank }.eachCount()
        val handCounts = hand.groupingBy { it.rank }.eachCount()
        return wantedCounts.all { (rank, count) -> (handCounts[rank] ?: 0) >= count }
    }

    private fun isConsecutive(ranks: List<Rank>): Boolean {
        return ranks.zipWithNext().all { (a, b) -> b.order == a.order + 1 }
    }
}
