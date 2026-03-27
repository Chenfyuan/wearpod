package com.sjtech.wearpod.playback

import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.provider.Settings

class AudioOutputController(
    private val appContext: Context,
) {
    fun showSystemOutputSwitcher() {
        val shown = runCatching {
            MediaRouter2.getInstance(appContext).showSystemOutputSwitcher()
        }.getOrDefault(false)

        if (!shown) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
    }
}
