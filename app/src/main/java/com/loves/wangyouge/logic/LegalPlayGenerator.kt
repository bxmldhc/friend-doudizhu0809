package com.loves.wangyouge.logic

import com.loves.wangyouge.model.Card
import com.loves.wangyouge.model.Rank

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
                for (wings in wingChoices(groups, excluded, length, pair = false)) {
                    result.add((tripleCards + wings).sorted())
                }
                for (wings in wingChoices(groups, excluded, length, pair = true)) {
                    result.add((tripleCards + wings).sorted())
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

            for ((_, cards) in otherGroups) {
                result.add((triple + cards.first()).sorted())
                if (cards.size >= 2) {
                    result.add((triple + cards.take(2)).sorted())
                }
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

            for (wings in wingChoices(otherGroups, emptySet(), 2, pair = false)) {
                result.add((bomb + wings).sorted())
            }
            for (wings in wingChoices(otherGroups, emptySet(), 2, pair = true)) {
                result.add((bomb + wings).sorted())
            }
        }
    }

    private fun wingChoices(
        groups: Map<Rank, List<Card>>,
        excluded: Set<Rank>,
        count: Int,
        pair: Boolean
    ): List<List<Card>> {
        val eligible = groups
            .filterKeys { it !in excluded }
            .filterValues { it.size >= if (pair) 2 else 1 }
            .entries
            .sortedBy { it.key.order }
        if (eligible.size < count) return emptyList()

        val result = mutableListOf<List<Card>>()
        val selected = mutableListOf<List<Card>>()
        fun choose(start: Int) {
            if (selected.size == count) {
                result.add(selected.flatten())
                return
            }
            val remaining = count - selected.size
            for (index in start..eligible.size - remaining) {
                selected.add(eligible[index].value.take(if (pair) 2 else 1))
                choose(index + 1)
                selected.removeAt(selected.lastIndex)
            }
        }
        choose(0)
        return result
    }

    private fun isConsecutive(ranks: List<Rank>): Boolean {
        return ranks.zipWithNext().all { (a, b) -> b.order == a.order + 1 }
    }
}
