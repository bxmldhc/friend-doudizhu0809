package com.loves.doudizhu.ai

import com.loves.doudizhu.logic.CardPattern
import com.loves.doudizhu.logic.ComboType
import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.logic.HandEvaluator
import com.loves.doudizhu.logic.LegalPlayGenerator
import com.loves.doudizhu.logic.Phase
import com.loves.doudizhu.model.AiDifficulty
import com.loves.doudizhu.model.Card
import com.loves.doudizhu.model.Rank
import kotlin.random.Random

sealed interface AiAction {
    data class Call(val want: Boolean) : AiAction
    data class Play(val cards: List<Card>) : AiAction
    data object Pass : AiAction
}

object AiDecider {

    fun decide(
        state: EngineState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): AiAction {
        if (state.phase == Phase.BIDDING) {
            return biddingDecision(state, difficulty, random)
        }
        if (state.phase != Phase.PLAYING) {
            return AiAction.Pass
        }

        val hand = state.seats[state.turnIndex].hand
        val legal = if (state.isLeadingTurn()) {
            LegalPlayGenerator.allPlays(hand)
        } else {
            LegalPlayGenerator.beats(hand, state.lastPlay)
        }
        if (legal.isEmpty()) return AiAction.Pass

        val strategicOverride = strategicResponse(state, hand, legal)
        if (strategicOverride != null) return strategicOverride

        return when (difficulty) {
            AiDifficulty.EASY -> easyDecision(legal, random)
            AiDifficulty.MEDIUM -> mediumDecision(legal)
            AiDifficulty.HARD -> hardDecision(state, hand, legal)
        }
    }

    private fun biddingDecision(
        state: EngineState,
        difficulty: AiDifficulty,
        random: Random
    ): AiAction {
        val strength = handStrength(state.seats[state.turnIndex].hand)
        val hasCandidate = state.bidCandidateIndex != null
        val shouldCall = when (difficulty) {
            AiDifficulty.EASY -> random.nextFloat() < 0.22f
            AiDifficulty.MEDIUM -> strength >= 24
            AiDifficulty.HARD -> if (hasCandidate) strength >= 28 else strength >= 22
        }
        return AiAction.Call(shouldCall)
    }

    /**
     * Farmers should not take a teammate's trick unless the landlord is close
     * to winning. In that emergency case, taking the trick is preferable to
     * preserving a small amount of card structure.
     */
    private fun strategicResponse(
        state: EngineState,
        hand: List<Card>,
        legal: List<List<Card>>
    ): AiAction? {
        if (state.isLeadingTurn()) return null

        val landlordIndex = state.landlordIndex ?: return null
        val isFarmer = state.turnIndex != landlordIndex
        val lastPlayerIndex = state.lastPlayerIndex ?: return null
        val teammatePlayed = isFarmer &&
            lastPlayerIndex != state.turnIndex &&
            lastPlayerIndex != landlordIndex

        if (teammatePlayed && !landlordIsThreatening(state)) {
            return AiAction.Pass
        }
        if (!landlordIsThreatening(state)) return null

        val normal = legal.filterNot(::isBomb)
        val bombs = legal.filter(::isBomb)
        val selected = if (teammatePlayed) {
            legal.maxWithOrNull(
                compareBy(
                    { responsePriority(it) },
                    { rankOf(it).order },
                    { it.size }
                )
            )
        } else {
            normal.minWithOrNull(
                compareBy(
                    { breakPenalty(hand, it) },
                    { rankOf(it).order },
                    { it.size }
                )
            ) ?: bombs.minWithOrNull(
                compareBy(
                    { responsePriority(it) },
                    { rankOf(it).order },
                    { it.size }
                )
            )
        }
        return AiAction.Play(selected ?: legal.first())
    }

    private fun landlordIsThreatening(state: EngineState): Boolean {
        val landlordIndex = state.landlordIndex ?: return false
        return state.seats[landlordIndex].hand.size in 1..3
    }

    private fun responsePriority(cards: List<Card>): Int {
        return when (HandEvaluator.classify(cards)?.type) {
            ComboType.ROCKET -> 3
            ComboType.BOMB -> 2
            else -> 1
        }
    }

    private fun isBomb(cards: List<Card>): Boolean {
        return HandEvaluator.classify(cards)?.type in setOf(
            ComboType.BOMB,
            ComboType.ROCKET
        )
    }

    private fun easyDecision(legal: List<List<Card>>, random: Random): AiAction {
        return AiAction.Play(legal[random.nextInt(legal.size)])
    }

    private fun mediumDecision(legal: List<List<Card>>): AiAction {
        val best = legal.minWithOrNull(
            compareBy({ it.size }, { rankOf(it).order })
        )
        return AiAction.Play(best ?: legal.first())
    }

    private fun hardDecision(
        state: EngineState,
        hand: List<Card>,
        legal: List<List<Card>>
    ): AiAction {
        val finish = legal.firstOrNull { it.size == hand.size }
        if (finish != null) return AiAction.Play(finish)

        return if (state.isLeadingTurn()) {
            chooseLeading(state, hand, legal)
        } else {
            chooseResponse(state, hand, legal, state.lastPlay)
        }
    }

    private fun chooseLeading(
        state: EngineState,
        hand: List<Card>,
        legal: List<List<Card>>
    ): AiAction {
        val sequenceTypes = setOf(
            ComboType.STRAIGHT,
            ComboType.PAIR_STRAIGHT,
            ComboType.AIRPLANE,
            ComboType.AIRPLANE_SINGLE,
            ComboType.AIRPLANE_PAIR
        )
        val sequences = legal.filter { HandEvaluator.classify(it)?.type in sequenceTypes }
        if (sequences.isNotEmpty()) {
            val best = sequences.maxWithOrNull(
                compareBy({ it.size }, { rankOf(it).order })
            )
            if (best != null) return AiAction.Play(best)
        }

        val groups = hand.groupingBy { it.rank }.eachCount()
        val pairCount = groups.count { it.value >= 2 }
        val tripleCount = groups.count { it.value >= 3 }
        val singleCount = groups.count { it.value == 1 }

        if (pairCount >= 2) {
            val pair = legal
                .filter { HandEvaluator.classify(it)?.type == ComboType.PAIR }
                .minWithOrNull(compareBy({ rankOf(it).order }, { it.size }))
            if (pair != null) return AiAction.Play(pair)
        }

        if (tripleCount >= 2) {
            val triple = legal
                .filter {
                    val pattern = HandEvaluator.classify(it)
                    pattern != null &&
                        pattern.type in setOf(
                            ComboType.TRIPLE,
                            ComboType.TRIPLE_SINGLE,
                            ComboType.TRIPLE_PAIR
                        ) &&
                        pattern.primaryRank.order < Rank.R2.order
                }
                .minWithOrNull(compareBy({ rankOf(it).order }, { it.size }))
            if (triple != null) return AiAction.Play(triple)
        }

        if (singleCount <= 1 && pairCount == 0 && tripleCount == 0) {
            val single = legal
                .filter { HandEvaluator.classify(it)?.type == ComboType.SINGLE }
                .minWithOrNull(compareBy({ rankOf(it).order }))
            if (single != null) return AiAction.Play(single)
        }

        val single = legal
            .filter { HandEvaluator.classify(it)?.type == ComboType.SINGLE }
            .minWithOrNull(compareBy({ rankOf(it).order }, { it.size }))
        return AiAction.Play(single ?: legal.first())
    }

    private fun chooseResponse(
        state: EngineState,
        hand: List<Card>,
        legal: List<List<Card>>,
        last: List<Card>
    ): AiAction {
        val lastPattern = HandEvaluator.classify(last) ?: return AiAction.Pass
        val normal = legal.filter { play ->
            val type = HandEvaluator.classify(play)?.type
            type != ComboType.BOMB && type != ComboType.ROCKET
        }
        val bombs = legal.filter { play ->
            val type = HandEvaluator.classify(play)?.type
            type == ComboType.BOMB || type == ComboType.ROCKET
        }

        return when (lastPattern.type) {
            ComboType.SINGLE -> chooseSingleResponse(state, hand, normal, bombs, lastPattern)
            ComboType.PAIR -> choosePairResponse(state, hand, normal, bombs, lastPattern)
            else -> chooseMinimalResponse(state, hand, normal, bombs)
        }
    }

    private fun chooseSingleResponse(
        state: EngineState,
        hand: List<Card>,
        normal: List<List<Card>>,
        bombs: List<List<Card>>,
        lastPattern: CardPattern
    ): AiAction {
        val singles = normal
            .filter { HandEvaluator.classify(it)?.type == ComboType.SINGLE }
            .sortedBy { rankOf(it).order }

        if (singles.isNotEmpty()) {
            val safe = singles.firstOrNull {
                val rank = rankOf(it)
                rank.order < Rank.R2.order && unknownRemaining(state, rank) == 0
            }
            if (safe != null) return AiAction.Play(safe)

            val low = singles
                .filter { rankOf(it).order < Rank.R2.order }
                .minWithOrNull(compareBy({ rankOf(it).order }, { breakPenalty(hand, it) }))
            if (low != null) {
                val penalty = breakPenalty(hand, low)
                if (penalty <= 1 ||
                    hand.size <= 6 ||
                    lastPattern.primaryRank.order >= Rank.RA.order ||
                    minOpponentCount(state) <= 5
                ) {
                    return AiAction.Play(low)
                }
                return AiAction.Pass
            }

            val lowest = singles.first()
            if (hand.size <= 5 || lastPattern.primaryRank.order >= Rank.RA.order) {
                return AiAction.Play(lowest)
            }
            return AiAction.Pass
        }

        if (bombs.isNotEmpty() && (hand.size <= 8 || minOpponentCount(state) <= 4)) {
            return AiAction.Play(bombs.minWith(compareBy({ rankOf(it).order }, { it.size })))
        }
        return AiAction.Pass
    }

    private fun choosePairResponse(
        state: EngineState,
        hand: List<Card>,
        normal: List<List<Card>>,
        bombs: List<List<Card>>,
        lastPattern: CardPattern
    ): AiAction {
        val pairs = normal.filter { HandEvaluator.classify(it)?.type == ComboType.PAIR }
        if (pairs.isNotEmpty()) {
            val best = pairs.minWithOrNull(
                compareBy({ rankOf(it).order }, { breakPenalty(hand, it) })
            ) ?: pairs.first()
            val penalty = breakPenalty(hand, best)
            if (penalty <= 1 ||
                hand.size <= 6 ||
                lastPattern.primaryRank.order >= Rank.RK.order ||
                minOpponentCount(state) <= 5
            ) {
                return AiAction.Play(best)
            }
            return AiAction.Pass
        }

        if (bombs.isNotEmpty() && (hand.size <= 8 || minOpponentCount(state) <= 4)) {
            return AiAction.Play(bombs.minWith(compareBy({ rankOf(it).order }, { it.size })))
        }
        return AiAction.Pass
    }

    private fun chooseMinimalResponse(
        state: EngineState,
        hand: List<Card>,
        normal: List<List<Card>>,
        bombs: List<List<Card>>
    ): AiAction {
        val candidates = normal.map { play -> play to (play.size + breakPenalty(hand, play)) }
        val best = candidates.minWithOrNull(
            compareBy({ it.second }, { rankOf(it.first).order })
        )
        if (best != null) return AiAction.Play(best.first)

        if (bombs.isNotEmpty() && (hand.size <= 8 || minOpponentCount(state) <= 5)) {
            return AiAction.Play(bombs.minWith(compareBy({ rankOf(it).order }, { it.size })))
        }
        return AiAction.Pass
    }

    private fun minOpponentCount(state: EngineState): Int {
        val turn = state.turnIndex
        return minOf(
            state.seats[(turn + 1) % 3].hand.size,
            state.seats[(turn + 2) % 3].hand.size
        )
    }

    private fun unknownRemaining(state: EngineState, rank: Rank): Int {
        val total = if (rank == Rank.SMALL_JOKER || rank == Rank.BIG_JOKER) 1 else 4
        val seen = state.seenRanks[rank] ?: 0
        val own = state.seats[state.turnIndex].hand.count { it.rank == rank }
        return (total - seen - own).coerceAtLeast(0)
    }

    private fun breakPenalty(hand: List<Card>, cards: List<Card>): Int {
        val pattern = HandEvaluator.classify(cards) ?: return 0
        val primary = pattern.primaryRank
        val groupSize = hand.count { it.rank == primary }
        val used = cards.count { it.rank == primary }
        return when {
            groupSize >= 4 && used < 4 -> 4
            groupSize == 3 && used < 3 -> 3
            groupSize == 2 && used < 2 -> 1
            else -> 0
        }
    }

    private fun rankOf(cards: List<Card>): Rank {
        return HandEvaluator.classify(cards)?.primaryRank ?: Rank.R3
    }

    private fun handStrength(hand: List<Card>): Int {
        val highScores = mapOf(
            Rank.R2 to 6,
            Rank.RA to 4,
            Rank.RK to 3,
            Rank.RQ to 2,
            Rank.RJ to 1,
            Rank.SMALL_JOKER to 7,
            Rank.BIG_JOKER to 8
        )
        var score = hand.sumOf { highScores[it.rank] ?: 0 }
        val counts = hand.groupingBy { it.rank }.eachCount()
        score += counts.values.count { it == 4 } * 9
        score += counts.values.count { it == 3 } * 2
        return score
    }
}
