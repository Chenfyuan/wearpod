package com.sjtech.wearpod.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.common.collect.ImmutableList

/**
 * Media3's default playback notification, additionally marked as a Wear OS Ongoing Activity so
 * the watch face and recents show a "now playing" chip that returns to the app.
 */
// DefaultMediaNotificationProvider and its addNotificationActions hook are @UnstableApi in Media3.
@OptIn(UnstableApi::class)
class OngoingMediaNotificationProvider(
    private val context: Context,
) : DefaultMediaNotificationProvider(context) {

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory,
    ): IntArray {
        val compactViewIndices = super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        val touchIntent = mediaSession.sessionActivity ?: return compactViewIndices
        val title = mediaSession.player.mediaMetadata.title?.toString().orEmpty()

        OngoingActivity.Builder(context, DEFAULT_NOTIFICATION_ID, builder)
            .setStaticIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
            .setTouchIntent(touchIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .apply {
                if (title.isNotBlank()) {
                    setStatus(
                        Status.Builder()
                            .addTemplate("#$STATUS_TITLE_PART#")
                            .addPart(STATUS_TITLE_PART, Status.TextPart(title))
                            .build(),
                    )
                }
            }
            .build()
            .apply(context)

        return compactViewIndices
    }

    private companion object {
        const val STATUS_TITLE_PART = "title"
    }
}
