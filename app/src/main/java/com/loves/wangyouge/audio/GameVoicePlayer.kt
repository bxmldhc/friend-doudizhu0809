package com.loves.wangyouge.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.loves.wangyouge.R
import java.util.ArrayDeque

class GameVoicePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val pending = ArrayDeque<AudioCue>()
    private var current: MediaPlayer? = null
    private var released = false

    fun play(cue: AudioCue) {
        if (released) return
        pending.addLast(cue)
        playNext()
    }

    fun stop() {
        pending.clear()
        current?.release()
        current = null
    }

    fun release() {
        released = true
        stop()
    }

    private fun playNext() {
        if (released || current != null || pending.isEmpty()) return
        val cue = pending.removeFirst()
        val player = runCatching {
            val descriptor = appContext.resources.openRawResourceFd(cue.resourceId())
                ?: error("无法读取语音资源")
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                descriptor.use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                prepare()
            }
        }.getOrNull() ?: run {
            playNext()
            return
        }
        player.setOnCompletionListener { finishCurrent(it) }
        player.setOnErrorListener { failed, _, _ ->
            finishCurrent(failed)
            true
        }
        current = player
        player.start()
    }

    private fun finishCurrent(player: MediaPlayer) {
        if (current === player) current = null
        player.release()
        playNext()
    }

    private fun AudioCue.resourceId(): Int = when (this) {
        AudioCue.CALL_LANDLORD -> R.raw.call_landlord
        AudioCue.ROB_LANDLORD -> R.raw.rob_landlord
        AudioCue.NOT_CALL -> R.raw.not_call
        AudioCue.NOT_ROB -> R.raw.not_rob
        AudioCue.PASS -> R.raw.pass
        AudioCue.CARDS_LEFT_TWO -> R.raw.cards_left_two
        AudioCue.CARDS_LEFT_ONE -> R.raw.cards_left_one
        AudioCue.YOUR_TURN -> R.raw.your_turn
        AudioCue.RANK_3 -> R.raw.single_3
        AudioCue.RANK_4 -> R.raw.single_4
        AudioCue.RANK_5 -> R.raw.single_5
        AudioCue.RANK_6 -> R.raw.single_6
        AudioCue.RANK_7 -> R.raw.single_7
        AudioCue.RANK_8 -> R.raw.single_8
        AudioCue.RANK_9 -> R.raw.single_9
        AudioCue.RANK_10 -> R.raw.single_10
        AudioCue.RANK_J -> R.raw.single_j
        AudioCue.RANK_Q -> R.raw.single_q
        AudioCue.RANK_K -> R.raw.single_k
        AudioCue.RANK_A -> R.raw.single_a
        AudioCue.RANK_2 -> R.raw.single_2
        AudioCue.RANK_SMALL_JOKER -> R.raw.single_small_joker
        AudioCue.RANK_BIG_JOKER -> R.raw.single_big_joker
        AudioCue.PLAY_PAIR -> R.raw.pair
        AudioCue.PLAY_TRIPLE -> R.raw.triple
        AudioCue.PLAY_TRIPLE_SINGLE -> R.raw.triple_single
        AudioCue.PLAY_TRIPLE_PAIR -> R.raw.triple_pair
        AudioCue.PLAY_STRAIGHT -> R.raw.straight
        AudioCue.PLAY_PAIR_STRAIGHT -> R.raw.pair_straight
        AudioCue.PLAY_AIRPLANE -> R.raw.airplane
        AudioCue.PLAY_AIRPLANE_SINGLE -> R.raw.airplane_single
        AudioCue.PLAY_AIRPLANE_PAIR -> R.raw.airplane_pair
        AudioCue.PLAY_FOUR_TWO_SINGLE -> R.raw.four_two_single
        AudioCue.PLAY_FOUR_TWO_PAIR -> R.raw.four_two_pair
        AudioCue.PLAY_BOMB -> R.raw.bomb
        AudioCue.PLAY_ROCKET -> R.raw.rocket
    }
}
