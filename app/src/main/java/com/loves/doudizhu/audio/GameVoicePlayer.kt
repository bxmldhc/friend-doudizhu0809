package com.loves.doudizhu.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.loves.doudizhu.R
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
        AudioCue.PLAY_SINGLE -> R.raw.card_play
        AudioCue.PLAY_PAIR -> R.raw.play_pair
        AudioCue.PLAY_TRIPLE -> R.raw.play_triple
        AudioCue.PLAY_STRAIGHT -> R.raw.play_straight
        AudioCue.PLAY_AIRPLANE -> R.raw.play_airplane
        AudioCue.PLAY_FOUR_WITH_TWO -> R.raw.card_play
        AudioCue.PLAY_BOMB -> R.raw.play_bomb
        AudioCue.PLAY_ROCKET -> R.raw.play_rocket
    }
}
