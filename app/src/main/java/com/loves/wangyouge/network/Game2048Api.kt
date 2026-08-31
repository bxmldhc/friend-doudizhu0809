package com.loves.wangyouge.network

import org.json.JSONObject

data class Leaderboard2048Entry(
    val playerId: String,
    val name: String,
    val score: Int,
    val maxTile: Int,
    val moves: Int,
    val updatedAt: Long
)
