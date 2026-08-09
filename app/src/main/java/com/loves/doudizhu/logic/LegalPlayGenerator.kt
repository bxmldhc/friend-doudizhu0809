package com.loves.doudizhu.logic

import com.loves.doudizhu.model.Card
import com.loves.doudizhu.model.Rank

object LegalPlayGenerator {

    fun allPlays(hand: List<Card>): List<List<Card>> {
        val cards = hand.sorted()
        if (cards.isEmpty()) return emptyList()

        val groups = cards.groupBy { it.rank }.toSortedMap(compareBy { it.order })
        val result = LinkedHashSet<List<Card>>()

        for ((_, list) in groups) {
            for (count in 1..minOf(4, list.size)) {
                result.add(list.take(count).sorted())
            }
        }

        val smallJoker = cards.firstOrNull { it.rank == Rank.SMALL_JOKER }
        val bigJoker = cards.firstOrNull { it.rank == Rank.BIG_JOKER }
        if (smallJoker != null && bigJoker != null) {
            result.add(listOf(smallJoker, bigJoker).sorted())
        }

        addStraights(groups, result)
        addPairStraights(groups, result)
        addAirplanes(groups, result)
        addTripleWings(groups, result)
        addFourWings(groups, result)

        return result.sortedWith(
            compareBy(
                { it.size },
                { HandEvaluator.classify(it)?.primaryRank?.order ?: 0 },
                { HandEvaluator.classify(it)?.type?.ordinal ?: 0 }
            )
        )
    }

    fun beats(hand: List<Card>, last: List<Card>): List<List<Card>> {
        val lastPattern = HandEvaluator.classify(last) ?: return emptyList()
        return allPlays(hand).filter { play ->
            val pattern = HandEvaluator.classify(play)
            pattern != null && HandEvaluator.canBeat(pattern, lastPattern)
        }
    }

    fun hint(hand: List<Card>, last: List<Card>): List<Card>? {
        val plays = if (last.isEmpty()) allPlays(hand) else beats(hand, last)
        return plays.minWithOrNull(
            compareBy(
                { it.size },
                { HandEvaluator.classify(it)?.primaryRank?.order ?: 0 }
            )
        )
    }

    private fun addStraights(
        groups: Map<Rank, List<Card>>,
        result: MutableSet<List<Card>>
    ) {
        val ranks = groups.keys.filter { it.order < Rank.R2.order }
        for (length in 5..ranks.size) {
            for (start in 0..ranks.size - length) {
                val run = ranks.subList(start, start + length)
                if (isConsecutive(run)) {
                    result.add(run.map { groups.getValue(it).first() })
                }
            }
        }
    }

    private fun addPairStraights(
        groups: Map<Rank, List<Card>>,
        result: MutableSet<List<Card>>
    ) {
        val ranks = groups.filterValues { it.size >= 2 }.keys.filter { it.order < Rank.R2.order }
        for (length in 3..ranks.size) {
            for (start in 0..ranks.size - length) {
                val run = ranks.subList(start, start + length)
                if (isConsecutive(run)) {
                    result.add(run.flatMap { groups.getValue(it).take(2) }.sorted())
                }
            }
        }
    }

    private fun addAirplanes(
        groups: Map<Rank, List<Card>>,
        result: MutableSet<List<Card>>
    ) {
        val tripleRanks = groups
            .filterValues { it.size >= 3 }
            .keys
            .filter { it.order < Rank.R2.order }

        for (length in 2..tripleRanks.size) {
            for (start in 0..tripleRanks.size - length) {
                val run = tripleRanks.subList(start, start + length)
                if (!isConsecutive(run)) continue

                val tripleCards = run.flatMap { groups.getValue(it).take(3) }.sorted()
                result.add(tripleCards)

                val excluded = run.toSet()
                val singleWings = pickWings(groups, excluded, length, pair = false)
                if (singleWings != null) {
                    result.add((tripleCards + singleWings).sorted())
                }
                val pairWings = pickWings(groups, excluded, length, pair = true)
                if (pairWings != null) {
                    result.add((tripleCards + pairWings).sorted())
                }
            }
        }
    }

    private fun addTripleWings(
        groups: Map<Rank, List<Card>>,
        result: MutableSet<List<Card>>
    ) {
        for ((rank, list) in groups) {
            if (list.size < 3) continue
            val triple = list.take(3)
            val otherGroups = groups.filterKeys { it != rank }

            val single = otherGroups.entries
                .minByOrNull { it.key.order }
                ?.value
                ?.firstOrNull()
            if (single != null) {
                result.add((triple + single).sorted())
            }

            val pair = otherGroups
                .filterValues { it.size >= 2 }
                .entries
                .minByOrNull { it.key.order }
                ?.value
                ?.take(2)
            if (pair != null) {
                result.add((triple + pair).sorted())
            }
        }
    }

    private fun addFourWings(
        groups: Map<Rank, List<Card>>,
        result: MutableSet<List<Card>>
    ) {
        for ((rank, list) in groups) {
            if (list.size < 4) continue
            val bomb = list.take(4)
            val otherGroups = groups.filterKeys { it != rank }

            val singleWings = pickDistinctRanks(otherGroups, 2, pair = false)
            if (singleWings != null) {
                result.add((bomb + singleWings).sorted())
            }
            val pairWings = pickDistinctRanks(otherGroups, 2, pair = true)
            if (pairWings != null) {
                result.add((bomb + pairWings).sorted())
            }
        }
    }

    private fun pickWings(
        groups: Map<Rank, List<Card>>,
        excluded: Set<Rank>,
        count: Int,
        pair: Boolean
    ): List<Card>? {
        val eligible = groups
            .filterKeys { it !in excluded && it != Rank.SMALL_JOKER && it != Rank.BIG_JOKER }
            .filterValues { it.size >= if (pair) 2 else 1 }
        if (eligible.size < count) return null
        return eligible.entries
            .sortedBy { it.key.order }
            .take(count)
            .flatMap { it.value.take(if (pair) 2 else 1) }
    }

    private fun pickDistinctRanks(
        groups: Map<Rank, List<Card>>,
        count: Int,
        pair: Boolean
    ): List<Card>? {
        val eligible = groups
            .filterKeys { if (pair) it != Rank.SMALL_JOKER && it != Rank.BIG_JOKER else true }
            .filterValues { it.size >= if (pair) 2 else 1 }
        if (eligible.size < count) return null
        return eligible.entries
            .sortedBy { it.key.order }
            .take(count)
            .flatMap { it.value.take(if (pair) 2 else 1) }
    }

    private fun isConsecutive(ranks: List<Rank>): Boolean {
        return ranks.zipWithNext().all { (a, b) -> b.order == a.order + 1 }
    }
}
