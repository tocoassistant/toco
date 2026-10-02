package com.toco.ai.island

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.toco.ai.R
import com.toco.ai.ui.widget.OrbView

/** Lightweight one-shot sounds for TOCO Island mascot moods. */
class SoundEngine(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sounds: Map<OrbView.Mood, Int> = mapOf(
        OrbView.Mood.IDLE to pool.load(context, R.raw.island_idle, 1),
        OrbView.Mood.SLEEP to pool.load(context, R.raw.island_sleep, 1),
        OrbView.Mood.YAWN to pool.load(context, R.raw.island_yawn, 1),
        OrbView.Mood.WINK to pool.load(context, R.raw.island_wink, 1),
        OrbView.Mood.PEEK to pool.load(context, R.raw.island_peek, 1),
        OrbView.Mood.THINK to pool.load(context, R.raw.island_think, 1),
        OrbView.Mood.DIZZY to pool.load(context, R.raw.island_dizzy, 1),
        OrbView.Mood.PROUD to pool.load(context, R.raw.island_proud, 1),
        OrbView.Mood.ANNOYED to pool.load(context, R.raw.island_annoyed, 1),
        OrbView.Mood.LOVE to pool.load(context, R.raw.island_love, 1)
    )

    fun play(mood: OrbView.Mood) {
        val id = sounds[mood] ?: return
        pool.play(id, 0.55f, 0.55f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
