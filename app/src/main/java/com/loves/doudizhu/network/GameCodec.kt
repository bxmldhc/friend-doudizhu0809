package com.loves.doudizhu.network

import com.loves.doudizhu.logic.EngineState
import com.loves.doudizhu.logic.Phase
import com.loves.doudizhu.logic.SeatState
import com.loves.doudizhu.logic.RoomLeaderboardEntry
import com.loves.doudizhu.logic.RoomRoundScore
import com.loves.doudizhu.logic.RoomRoundSummary
import com.loves.doudizhu.logic.SeatAction
import com.loves.doudizhu.logic.ServerGameEvent
import com.loves.doudizhu.model.Card
import com.loves.doudizhu.model.parseCards
import com.loves.doudizhu.model.toNotation
import org.json.JSONArray
import org.json.JSONObject

object GameCodec {

    fun encodeState(state: EngineState): String {
        val obj = JSONObject()
        obj.put("phase", state.phase.name)
        obj.put("seats", JSONArray().apply {
            state.seats.forEach { put(encodeSeat(it)) }
        })
        obj.put("turnIndex", state.turnIndex)
        obj.put("landlordIndex", state.landlordIndex ?: JSONObject.NULL)
        obj.put("bidStartIndex", state.bidStartIndex)
        obj.put("bidCandidateIndex", state.bidCandidateIndex ?: JSONObject.NULL)
        obj.put("bidCount", state.bidCount)
        obj.put("bidMultiplier", state.bidMultiplier)
        obj.put("actedCount", state.actedCount)
        obj.put("lastPlay", encodeCards(state.lastPlay))
        obj.put("lastPlayerIndex", state.lastPlayerIndex ?: JSONObject.NULL)
        obj.put("lastPlaysBySeat", JSONArray().apply {
            state.lastPlaysBySeat.forEach { put(encodeCards(it)) }
        })
        obj.put("lastActionsBySeat", JSONArray().apply {
            state.lastActionsBySeat.forEach { action ->
                put(action?.name ?: JSONObject.NULL)
            }
        })
        obj.put("passes", state.passes)
        obj.put("bottomCards", encodeCards(state.bottomCards))
        obj.put("multiplier", state.multiplier)
        obj.put("winnerIndex", state.winnerIndex ?: JSONObject.NULL)
        obj.put("scoreDelta", JSONArray().apply { state.scoreDelta.forEach { put(it) } })
        obj.put("message", state.message)
        obj.put("gameNumber", state.gameNumber)
        obj.put("actionDeadlineAt", state.actionDeadlineAt ?: JSONObject.NULL)
        obj.put("leaderboard", JSONArray().apply {
            state.leaderboard.forEach { entry ->
                put(JSONObject().apply {
                    put("playerId", entry.playerId)
                    put("name", entry.name)
                    put("score", entry.score)
                    put("isAi", entry.isAi)
                    put("seatIndex", entry.seatIndex ?: JSONObject.NULL)
                    put("isAway", entry.isAway)
                    put("isActive", entry.isActive)
                })
            }
        })
        obj.put("roundHistory", JSONArray().apply {
            state.roundHistory.forEach { round ->
                put(JSONObject().apply {
                    put("gameNumber", round.gameNumber)
                    put("winnerPlayerId", round.winnerPlayerId ?: JSONObject.NULL)
                    put("landlordPlayerId", round.landlordPlayerId ?: JSONObject.NULL)
                    put("multiplier", round.multiplier)
                    put("scores", JSONArray().apply {
                        round.scores.forEach { score ->
                            put(JSONObject().apply {
                                put("playerId", score.playerId)
                                put("name", score.name)
                                put("delta", score.delta)
                                put("totalScore", score.totalScore)
                                put("isAi", score.isAi)
                            })
                        }
                    })
                })
            }
        })
        obj.put("readySeats", JSONArray().apply {
            state.readySeats.forEach { put(it) }
        })
        obj.put("revision", state.revision)
        obj.put("lastEvent", state.lastEvent?.let { event ->
            JSONObject().apply {
                put("id", event.id)
                put("type", event.type)
                put("seat", event.seat ?: JSONObject.NULL)
                put("automatic", event.automatic)
                event.comboType?.let { put("comboType", it) }
                event.primaryRank?.let { put("primaryRank", it) }
                event.comboLength?.let { put("comboLength", it) }
            }
        } ?: JSONObject.NULL)
        return obj.toString()
    }

    fun decodeState(json: String): EngineState {
        val obj = JSONObject(json)
        val seatsArray = obj.getJSONArray("seats")
        val seats = List(seatsArray.length()) { i ->
            decodeSeat(seatsArray.getJSONObject(i))
        }
        val leaderboard = if (obj.has("leaderboard")) {
            val arr = obj.getJSONArray("leaderboard")
            List(arr.length()) { index ->
                val item = arr.getJSONObject(index)
                RoomLeaderboardEntry(
                    playerId = item.optString("playerId"),
                    name = item.optString("name"),
                    score = item.optInt("score"),
                    isAi = item.optBoolean("isAi", false),
                    seatIndex = nullableInt(item, "seatIndex"),
                    isAway = item.optBoolean("isAway", false),
                    isActive = item.optBoolean("isActive", true)
                )
            }
        } else {
            emptyList()
        }
        val roundHistory = if (obj.has("roundHistory")) {
            val arr = obj.getJSONArray("roundHistory")
            List(arr.length()) { index ->
                val item = arr.getJSONObject(index)
                val scores = item.optJSONArray("scores")
                RoomRoundSummary(
                    gameNumber = item.optInt("gameNumber"),
                    winnerPlayerId = nullableString(item, "winnerPlayerId"),
                    landlordPlayerId = nullableString(item, "landlordPlayerId"),
                    multiplier = item.optInt("multiplier", 1),
                    scores = if (scores == null) emptyList() else List(scores.length()) { scoreIndex ->
                        val score = scores.getJSONObject(scoreIndex)
                        RoomRoundScore(
                            playerId = score.optString("playerId"),
                            name = score.optString("name"),
                            delta = score.optInt("delta"),
                            totalScore = score.optInt("totalScore"),
                            isAi = score.optBoolean("isAi", false)
                        )
                    }
                )
            }
        } else {
            emptyList()
        }
        val readySeats = if (obj.has("readySeats")) {
            val arr = obj.getJSONArray("readySeats")
            List(3) { index -> index < arr.length() && arr.optBoolean(index, false) }
        } else {
            List(3) { false }
        }
        val lastActionsBySeat = obj.optJSONArray("lastActionsBySeat")?.let { arr ->
            List(3) { index ->
                if (index >= arr.length() || arr.isNull(index)) {
                    null
                } else {
                    runCatching {
                        SeatAction.valueOf(arr.optString(index))
                    }.getOrNull()
                }
            }
        } ?: List(3) { null }
        return EngineState(
            phase = Phase.valueOf(obj.getString("phase")),
            seats = seats,
            turnIndex = obj.getInt("turnIndex"),
            landlordIndex = nullableInt(obj, "landlordIndex"),
            bidStartIndex = obj.optInt("bidStartIndex", obj.getInt("turnIndex")),
            bidCandidateIndex = nullableInt(obj, "bidCandidateIndex"),
            bidCount = obj.getInt("bidCount"),
            bidMultiplier = obj.optInt("bidMultiplier", obj.optInt("multiplier", 1)),
            actedCount = obj.getInt("actedCount"),
            lastPlay = decodeCards(obj.getJSONArray("lastPlay")),
            lastPlayerIndex = nullableInt(obj, "lastPlayerIndex"),
            lastPlaysBySeat = if (obj.has("lastPlaysBySeat")) {
                val arr = obj.getJSONArray("lastPlaysBySeat")
                List(3) { index ->
                    if (index < arr.length()) decodeCards(arr.getJSONArray(index)) else emptyList()
                }
            } else {
                List(3) { index ->
                    if (index == nullableInt(obj, "lastPlayerIndex")) decodeCards(obj.getJSONArray("lastPlay")) else emptyList()
                }
            },
            lastActionsBySeat = lastActionsBySeat,
            passes = obj.getInt("passes"),
            bottomCards = decodeCards(obj.getJSONArray("bottomCards")),
            multiplier = obj.getInt("multiplier"),
            winnerIndex = nullableInt(obj, "winnerIndex"),
            scoreDelta = if (obj.has("scoreDelta")) {
                val arr = obj.getJSONArray("scoreDelta")
                List(arr.length()) { arr.getInt(it) }
            } else {
                emptyList()
            },
            message = obj.optString("message"),
            gameNumber = obj.getInt("gameNumber"),
            actionDeadlineAt = if (obj.has("actionDeadlineAt") && !obj.isNull("actionDeadlineAt")) {
                obj.getLong("actionDeadlineAt")
            } else {
                null
            },
            leaderboard = leaderboard,
            roundHistory = roundHistory,
            readySeats = readySeats,
            revision = obj.optLong("revision", 0),
            lastEvent = obj.optJSONObject("lastEvent")?.let { event ->
                ServerGameEvent(
                    id = event.optString("id"),
                    type = event.optString("type"),
                    seat = nullableInt(event, "seat"),
                    automatic = event.optBoolean("automatic", false),
                    comboType = nullableString(event, "comboType"),
                    primaryRank = nullableInt(event, "primaryRank"),
                    comboLength = nullableInt(event, "comboLength")
                )
            }
        )
    }

    fun encodeCards(cards: List<Card>): JSONArray {
        return JSONArray().apply {
            cards.forEach { put(it.toNotation()) }
        }
    }

    fun decodeCards(array: JSONArray): List<Card> {
        return List(array.length()) { i ->
            parseCards(array.getString(i)).first()
        }
    }

    private fun encodeSeat(seat: SeatState): JSONObject {
        val obj = JSONObject()
        obj.put("index", seat.index)
        obj.put("name", seat.name)
        obj.put("isHuman", seat.isHuman)
        obj.put("isAway", seat.isAway)
        obj.put("hand", encodeCards(seat.hand))
        obj.put("handCount", seat.handCount)
        obj.put("isLandlord", seat.isLandlord)
        obj.put("score", seat.score)
        return obj
    }

    private fun decodeSeat(obj: JSONObject): SeatState {
        val hand = decodeCards(obj.getJSONArray("hand"))
        return SeatState(
            index = obj.getInt("index"),
            name = obj.getString("name"),
            isHuman = obj.getBoolean("isHuman"),
            isAway = obj.optBoolean("isAway", false),
            hand = hand,
            isLandlord = obj.getBoolean("isLandlord"),
            score = obj.getInt("score"),
            handCount = obj.optInt("handCount", hand.size)
        )
    }

    private fun nullableInt(obj: JSONObject, key: String): Int? {
        return if (obj.has(key) && !obj.isNull(key)) obj.getInt(key) else null
    }

    private fun nullableString(obj: JSONObject, key: String): String? {
        return if (obj.has(key) && !obj.isNull(key)) obj.optString(key).ifBlank { null } else null
    }
}
