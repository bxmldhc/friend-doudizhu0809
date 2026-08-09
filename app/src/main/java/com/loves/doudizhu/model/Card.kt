package com.loves.doudizhu.model

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADE("♠", false),
    HEART("♥", true),
    CLUB("♣", false),
    DIAMOND("♦", true),
    JOKER("", false)
}

enum class Rank(val label: String, val order: Int) {
    R3("3", 3),
    R4("4", 4),
    R5("5", 5),
    R6("6", 6),
    R7("7", 7),
    R8("8", 8),
    R9("9", 9),
    R10("10", 10),
    RJ("J", 11),
    RQ("Q", 12),
    RK("K", 13),
    RA("A", 14),
    R2("2", 15),
    SMALL_JOKER("小王", 16),
    BIG_JOKER("大王", 17)
}

data class Card(val rank: Rank, val suit: Suit) : Comparable<Card> {
    override fun compareTo(other: Card): Int {
        val byRank = rank.order.compareTo(other.rank.order)
        if (byRank != 0) return byRank
        return suit.ordinal.compareTo(other.suit.ordinal)
    }

    val isRed: Boolean
        get() = when (rank) {
            Rank.SMALL_JOKER -> false
            Rank.BIG_JOKER -> true
            else -> suit.isRed
        }

    val displayText: String
        get() = when (rank) {
            Rank.SMALL_JOKER, Rank.BIG_JOKER -> rank.label
            else -> rank.label + suit.symbol
        }
}

val DECK: List<Card> = buildList {
    val normalSuits = listOf(Suit.SPADE, Suit.HEART, Suit.CLUB, Suit.DIAMOND)
    val normalRanks = Rank.entries.filter { it != Rank.SMALL_JOKER && it != Rank.BIG_JOKER }
    for (suit in normalSuits) {
        for (rank in normalRanks) {
            add(Card(rank, suit))
        }
    }
    add(Card(Rank.SMALL_JOKER, Suit.JOKER))
    add(Card(Rank.BIG_JOKER, Suit.JOKER))
}

fun parseCards(notation: String): List<Card> {
    val tokens = notation.trim().split(Regex("\\s+|,")).filter { it.isNotBlank() }
    return tokens.map { token ->
        when (token) {
            "X" -> Card(Rank.SMALL_JOKER, Suit.JOKER)
            "B" -> Card(Rank.BIG_JOKER, Suit.JOKER)
            else -> {
                require(token.length >= 2) { "无法识别的牌: $token" }
                val rankPart = token.dropLast(1)
                val suitPart = token.last().uppercase()
                val rank = when (rankPart.uppercase()) {
                    "3" -> Rank.R3
                    "4" -> Rank.R4
                    "5" -> Rank.R5
                    "6" -> Rank.R6
                    "7" -> Rank.R7
                    "8" -> Rank.R8
                    "9" -> Rank.R9
                    "10", "T" -> Rank.R10
                    "J" -> Rank.RJ
                    "Q" -> Rank.RQ
                    "K" -> Rank.RK
                    "A" -> Rank.RA
                    "2" -> Rank.R2
                    else -> throw IllegalArgumentException("无法识别的牌: $token")
                }
                val suit = when (suitPart) {
                    "S" -> Suit.SPADE
                    "H" -> Suit.HEART
                    "C" -> Suit.CLUB
                    "D" -> Suit.DIAMOND
                    else -> throw IllegalArgumentException("无法识别的花色: $token")
                }
                Card(rank, suit)
            }
        }
    }
}

fun Card.toNotation(): String = when (rank) {
    Rank.SMALL_JOKER -> "X"
    Rank.BIG_JOKER -> "B"
    else -> rank.label + when (suit) {
        Suit.SPADE -> "S"
        Suit.HEART -> "H"
        Suit.CLUB -> "C"
        Suit.DIAMOND -> "D"
        Suit.JOKER -> ""
    }
}
