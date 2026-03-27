package com.sjtech.wearpod.playback

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VolumeSnapshot(
    val current: Int = 0,
    val max: Int = 0,
) {
    val canIncrease: Boolean
        get() = current < max

    val canDecrease: Boolean
        get() = current > 0

    val percentage: Int
        get() = if (max <= 0) 0 else ((current.toFloat() / max.toFloat()) * 100f).roundToInt()
}

class VolumeController(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mutableState = MutableStateFlow(readSnapshot())

    val state: StateFlow<VolumeSnapshot> = mutableState.asStateFlow()

    fun refresh(): VolumeSnapshot {
        val snapshot = readSnapshot()
        mutableState.value = snapshot
        return snapshot
    }

    fun increase(): VolumeSnapshot {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI,
        )
        return refresh()
    }

    fun decrease(): VolumeSnapshot {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
        return refresh()
    }

    fun showSystemPanel(): VolumeSnapshot {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_SAME,
            AudioManager.FLAG_SHOW_UI,
        )
        return refresh()
    }

    private fun readSnapshot(): VolumeSnapshot =
        VolumeSnapshot(
            current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        )
}
