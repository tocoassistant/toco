package com.toco.ai.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.toco.ai.R

/**
 * Tells the user the overlay cannot be drawn, and takes them straight to the
 * screen that fixes it.
 *
 * Exists because a refused overlay is invisible by definition — there is no
 * way for the app to show the problem in the place the problem occurs.
 */
object OverlayPermissionNotice {

    private const val CHANNEL = "toco_overlay_help"
    private const val ID = 53

    fun post(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.overlay_help_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val settings = PendingIntent.getActivity(
            context,
            3,
            Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION")
                .setData(Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        manager.notify(
            ID,
            builder
                .setContentTitle(context.getString(R.string.overlay_help_title))
                .setContentText(context.getString(R.string.overlay_help_text))
                .setStyle(
                    Notification.BigTextStyle()
                        .bigText(context.getString(R.string.overlay_help_text))
                )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(settings)
                .setAutoCancel(true)
                .build()
        )
    }
}
