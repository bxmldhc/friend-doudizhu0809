package com.loves.doudizhu.logic

import com.loves.doudizhu.model.Card
import com.loves.doudizhu.model.DECK
import com.loves.doudizhu.model.GameConfig
import com.loves.doudizhu.model.Rank
import kotlin.random.Random

enum class Phase {
    BIDDING,
    PLAYING,
    FINISHED
}

data class SeatState(
    val index: Int,
    val name: String,
    val isHuman: Boolean,
    val hand: List<Card>,
    val isLandlord: Boolean,
    val score: Int,
    val isAway: Boolean = false,
    val handCount: Int = hand.size
)

data class RoomLeaderboardEntry(
    val playerId: String,
    val name: String,
    val score: Int,
    val isAi: Boolean,
    val seatIndex: Int?,
    val isAway: Boolean,
    val isActive: Boolean
)

data class RoomRoundScore(
    val playerId: String,
    val name: String,
    val delta: Int,
    val totalScore: Int,
    val isAi: Boolean
)

data class RoomRoundSummary(
    val gameNumber: Int,
    val winnerPlayerId: String?,
    val landlordPlayerId: String?,
    val multiplier: Int,
    val scores: List<RoomRoundScore>
)

data class EngineState(
    val phase: Phase,
    val seats: List<SeatState>,
    val turnIndex: Int,
    val landlordIndex: Int? = null,
    val bidStartIndex: Int = turnIndex,
    val bidCandidateIndex: Int? = null,
    val bidCount: Int = 0,
    val actedCount: Int = 0,
    val lastPlay: List<Card> = emptyList(),
    val lastPlayerIndex: Int? = null,
    val lastPlaysBySeat: List<List<Card>> = List(3) { emptyList() },
    val passes: Int = 0,
    val bottomCards: List<Card> = emptyList(),
    val multiplier: Int = 1,
    val seenRanks: Map<Rank, Int> = emptyMap(),
    val winnerIndex: Int? = null,
    val scoreDelta: List<Int> = emptyList(),
    val message: String = "",
    val gameNumber: Int = 0,
    val actionDeadlineAt: Long? = null,
    val leaderboard: List<RoomLeaderboardEntry> = emptyList(),
    val roundHistory: List<RoomRoundSummary> = emptyList()
) {
    val currentSeat: SeatState
        get() = seats[turnIndex]

    fun isHumanTurn(): Boolean {
        return phase != Phase.FINISHED && seats[turnIndex].isHuman && !seats[turnIndex].isAway
    }

    fun isAiTurn(): Boolean {
        return phase != Phase.FINISHED && (!seats[turnIndex].isHuman || seats[turnIndex].isAway)
    }

    fun isLeadingTurn(): Boolean {
        return lastPlay.isEmpty()
    }

    fun canPass(): Boolean {
        return lastPlay.isNotEmpty() && lastPlayerIndex != turnIndex
    }
}

fun validatePlay(
    hand: List<Card>,
    lastPlay: List<Card>,
    isLeading: Boolean,
    cards: List<Card>
): String? {
    if (cards.isEmpty()) return "请选择要出的牌"
    if (!HandEvaluator.containsAll(hand, cards)) return "只能出自己手牌中的牌"
    val pattern = HandEvaluator.classify(cards.sorted())
        ?: return "不是合法牌型"
    if (!isLeading) {
        val lastPattern = HandEvaluator.classify(lastPlay)
            ?: return "上一手牌型无效"
        if (!HandEvaluator.canBeat(pattern, lastPattern)) {
            return "管不上上一手"
        }
    }
    return null
}

class DoudizhuEngine(
    val config: GameConfig,
    private val random: Random = Random.Default
) {
    var state = EngineState(Phase.BIDDING, emptyList(), 0)
        private set

    fun startNewGame() {
        val previousScores = if (state.seats.size == 3) {
            state.seats.map { it.score }
        } else {
            listOf(0, 0, 0)
        }

        val deck = DECK.shuffled(random)
        val hands = List(3) { index ->
            deck.subList(index * 17, index * 17 + 17).sorted()
        }
        val bottom = deck.subList(51, 54).sorted()

        val humanNames = listOf("玩家1", "玩家2", "玩家3")
        val aiNames = listOf("小智", "阿强", "老K")
        val seats = List(3) { index ->
            val isHuman = index < config.humanCount
            val name = if (isHuman) humanNames[index] else aiNames[index]
            SeatState(index, name, isHuman, hands[index], false, previousScores[index])
        }

        state = EngineState(
            phase = Phase.BIDDING,
            seats = seats,
            turnIndex = random.nextInt(3),
            bottomCards = bottom,
            message = "等待叫地主",
            gameNumber = state.gameNumber + 1
        )
    }

    fun submitCall(want: Boolean) {
        check(state.phase == Phase.BIDDING) { "当前不在叫地主阶段" }

        val seat = state.seats[state.turnIndex]
        val candidate = if (want) state.turnIndex else state.bidCandidateIndex
        val bidCount = state.bidCount + if (want) 1 else 0
        val acted = state.actedCount + 1
        val message = when {
            !want -> "${seat.name} 不抢"
            state.bidCandidateIndex == null -> "${seat.name} 叫地主"
            else -> "${seat.name} 抢地主"
        }

        if (acted >= 3) {
            if (candidate == null) {
                startNewGame()
                return
            }
            val bottom = state.bottomCards
            val newSeats = state.seats.mapIndexed { index, seatState ->
                if (index == candidate) {
                    seatState.copy(
                        hand = (seatState.hand + bottom).sorted(),
                        isLandlord = true,
                        handCount = seatState.hand.size + bottom.size
                    )
                } else {
                    seatState
                }
            }
            state = state.copy(
                phase = Phase.PLAYING,
                seats = newSeats,
                turnIndex = candidate,
                landlordIndex = candidate,
                bidCandidateIndex = null,
                bidCount = bidCount.coerceAtLeast(1),
                actedCount = 0,
                lastPlay = emptyList(),
                lastPlayerIndex = null,
                lastPlaysBySeat = List(3) { emptyList() },
                passes = 0,
                multiplier = 1,
                seenRanks = emptyMap(),
                message = "${newSeats[candidate].name} 成为地主"
            )
        } else {
            state = state.copy(
                turnIndex = nextSeat(state.turnIndex),
                bidCandidateIndex = candidate,
                bidCount = bidCount,
                actedCount = acted,
                message = message
            )
        }
    }

    fun submitPass() {
        check(state.phase == Phase.PLAYING) { "当前不能不出" }
        check(canPass()) { "本轮不能不出" }

        val next = nextSeat(state.turnIndex)
        val message = "${state.seats[state.turnIndex].name} 不出"
        if (state.passes + 1 >= 2) {
            state = state.copy(
                turnIndex = next,
                passes = 0,
                lastPlay = emptyList(),
                lastPlayerIndex = null,
                message = "轮到 ${state.seats[next].name} 出牌"
            )
        } else {
            state = state.copy(
                turnIndex = next,
                passes = state.passes + 1,
                message = message
            )
        }
    }

    fun submitPlay(cards: List<Card>) {
        check(state.phase == Phase.PLAYING) { "现在不能出牌" }
        val error = validateCurrentPlay(cards)
        check(error == null) { error ?: "非法出牌" }

        val seat = state.seats[state.turnIndex]
        val pattern = HandEvaluator.classify(cards.sorted())!!
        val newHand = removeCards(seat.hand, cards)
        val seen = state.seenRanks.toMutableMap()
        for ((rank, count) in cards.groupingBy { it.rank }.eachCount()) {
            seen[rank] = (seen[rank] ?: 0) + count
        }

        val isBomb = pattern.type == ComboType.BOMB || pattern.type == ComboType.ROCKET
        val multiplier = if (isBomb) state.multiplier * 2 else state.multiplier

        if (newHand.isEmpty()) {
            finishGame(state.turnIndex, multiplier)
            return
        }

        state = state.copy(
            seats = state.seats.mapIndexed { index, seatState ->
                if (index == state.turnIndex) {
                    seatState.copy(hand = newHand, handCount = newHand.size)
                } else {
                    seatState
                }
            },
                turnIndex = nextSeat(state.turnIndex),
                lastPlay = cards.sorted(),
                lastPlayerIndex = state.turnIndex,
                lastPlaysBySeat = state.lastPlaysBySeat.mapIndexed { index, play ->
                    if (index == state.turnIndex) cards.sorted() else play
                },
                passes = 0,
            seenRanks = seen,
            multiplier = multiplier,
            message = "${seat.name} 出牌"
        )
    }

    fun validateCurrentPlay(cards: List<Card>): String? {
        if (state.phase != Phase.PLAYING) return "现在不能出牌"
        val seat = state.seats[state.turnIndex]
        return validatePlay(seat.hand, state.lastPlay, state.isLeadingTurn(), cards)
    }

    fun hintCards(): List<Card>? {
        if (state.phase != Phase.PLAYING) return null
        val hand = state.seats[state.turnIndex].hand
        val last = if (state.isLeadingTurn()) emptyList() else state.lastPlay
        return LegalPlayGenerator.hint(hand, last)
    }

    private fun finishGame(winnerIndex: Int, multiplier: Int) {
        val landlordIndex = requireNotNull(state.landlordIndex)
        val base = state.bidCount.coerceAtLeast(1) * multiplier
        val landlordWon = winnerIndex == landlordIndex
        val delta = List(3) { index ->
            when {
                index == landlordIndex -> if (landlordWon) 2 * base else -2 * base
                else -> if (landlordWon) -base else base
            }
        }
        val newSeats = state.seats.mapIndexed { index, seatState ->
            seatState.copy(score = seatState.score + delta[index])
        }
        state = state.copy(
            phase = Phase.FINISHED,
            seats = newSeats,
            winnerIndex = winnerIndex,
            scoreDelta = delta,
            multiplier = multiplier,
            message = "${state.seats[winnerIndex].name} 获胜"
        )
    }

    private fun removeCards(hand: List<Card>, cards: List<Card>): List<Card> {
        val remaining = hand.toMutableList()
        for (card in cards) {
            val index = remaining.indexOfFirst { it == card }
            check(index >= 0) { "手牌中不存在这张牌" }
            remaining.removeAt(index)
        }
        return remaining
    }

    private fun canPass(): Boolean = state.canPass()
}

private fun nextSeat(index: Int): Int = (index + 2) % 3
