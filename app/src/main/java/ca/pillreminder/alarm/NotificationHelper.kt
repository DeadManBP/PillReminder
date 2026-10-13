package ca.pillreminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ca.pillreminder.MainActivity
import ca.pillreminder.R
import ca.pillreminder.data.MedStore
import ca.pillreminder.data.Medication

object NotificationHelper {

    const val CHANNEL_ID = "dose_alarms"

    /** Vibration pattern: urgent pulsing. */
    private val VIBRATE_PATTERN = longArrayOf(0, 800, 400, 800, 400, 1200)

    fun defaultSoundUri(): android.net.Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    private fun soundUri(ctx: Context): android.net.Uri {
        val custom = MedStore(ctx).getNotifSoundUri()
        return if (custom != null) android.net.Uri.parse(custom) else defaultSoundUri()
    }

    private fun vibrationEnabled(ctx: Context): Boolean =
        MedStore(ctx).isVibrationEnabled()

    /**
     * Ensures the channel exists with the current settings. On Android 8+,
     * channel sound/vibration are immutable after creation, so the channel
     * is deleted and recreated when settings change.
     */
    fun syncChannel(ctx: Context) {
        val appCtx = ctx.applicationContext
        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val store = MedStore(appCtx)
        val configHash = "${store.getNotifSoundUri() ?: "default"}|${store.isVibrationEnabled()}"
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null && store.getChannelConfigHash() == configHash) return
        // Settings changed (or first run): recreate the channel.
        nm.deleteNotificationChannel(CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dose alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Persistent reminders when a dose is due"
            setSound(soundUri(appCtx), null)
            vibrationPattern = VIBRATE_PATTERN
            enableVibration(vibrationEnabled(appCtx))
        }
        nm.createNotificationChannel(channel)
        store.setChannelConfigHash(configHash)
    }

    fun ensureChannel(ctx: Context) = syncChannel(ctx)

    fun showDose(
        ctx: Context,
        med: Medication,
        timeIndex: Int,
        scheduledAt: Long,
        nagCount: Int
    ) {
        syncChannel(ctx)
        val appCtx = ctx.applicationContext

        val openIntent = Intent(appCtx, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPi = PendingIntent.getActivity(
            appCtx, AlarmScheduler.requestCode(med.id, timeIndex), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doseLine = listOfNotNull(
            med.dose.takeIf { it.isNotBlank() },
            formatTime(scheduledAt)
        ).joinToString(" · ")
        val text = if (nagCount > 0) "$doseLine — still waiting" else doseLine

        val builder = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("\uD83D\uDC8A Time to take ${med.name}")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .setSound(soundUri(appCtx))
        if (vibrationEnabled(appCtx)) {
            builder.setVibrate(VIBRATE_PATTERN)
        }
        val notification = builder
            .addAction(
                0, "Taken",
                DoseActionReceiver.actionIntent(
                    appCtx, DoseActionReceiver.ACTION_TAKEN, med.id, timeIndex, scheduledAt
                )
            )
            .addAction(
                0, "Snooze 10 min",
                DoseActionReceiver.actionIntent(
                    appCtx, DoseActionReceiver.ACTION_SNOOZE, med.id, timeIndex, scheduledAt
                )
            )
            .addAction(
                0, "Skip",
                DoseActionReceiver.actionIntent(
                    appCtx, DoseActionReceiver.ACTION_SKIP, med.id, timeIndex, scheduledAt
                )
            )
            .build()

        try {
            NotificationManagerCompat.from(appCtx)
                .notify(AlarmScheduler.requestCode(med.id, timeIndex), notification)
        } catch (se: SecurityException) {
            // Notifications not granted yet; the alarm state is still tracked.
        }
    }

    fun cancel(ctx: Context, medId: String, timeIndex: Int) {
        NotificationManagerCompat.from(ctx.applicationContext)
            .cancel(AlarmScheduler.requestCode(medId, timeIndex))
    }

    /** Low-stock or out-of-stock refill reminder. Not persistent; informational. */
    fun showRefillAlert(ctx: Context, med: Medication, outOfStock: Boolean) {
        syncChannel(ctx)
        val appCtx = ctx.applicationContext

        val openIntent = Intent(appCtx, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPi = PendingIntent.getActivity(
            appCtx, refillRequestCode(med.id), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (outOfStock) "\uD83D\uDEA8 Out of ${med.name}"
        else "\u26A0\uFE0F Low on ${med.name}"
        val text = if (outOfStock)
            "You're out. Time to refill your prescription."
        else
            "Only ${med.pillsRemaining} pills left — time to refill."

        val builder = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .setSound(soundUri(appCtx))
        if (vibrationEnabled(appCtx)) {
            builder.setVibrate(VIBRATE_PATTERN)
        }
        val notification = builder.build()

        try {
            NotificationManagerCompat.from(appCtx)
                .notify(refillRequestCode(med.id), notification)
        } catch (se: SecurityException) {
            // Notifications not granted; nothing more to do.
        }
    }

    /** Fires a test dose notification so the user can preview sound/vibration. */
    fun showTest(ctx: Context) {
        syncChannel(ctx)
        val appCtx = ctx.applicationContext
        val openIntent = Intent(appCtx, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPi = PendingIntent.getActivity(
            appCtx, -999999, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("\uD83D\uDC8A Test notification")
            .setContentText("This is how your dose alarms will sound and vibrate.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .setSound(soundUri(appCtx))
        if (vibrationEnabled(appCtx)) {
            builder.setVibrate(VIBRATE_PATTERN)
        }
        try {
            NotificationManagerCompat.from(appCtx).notify(-999999, builder.build())
        } catch (se: SecurityException) {
        }
    }

    fun cancelRefillAlert(ctx: Context, medId: String) {
        NotificationManagerCompat.from(ctx.applicationContext)
            .cancel(refillRequestCode(medId))
    }

    private fun refillRequestCode(medId: String): Int {
        val h = "refill#$medId".hashCode()
        val v = if (h == Int.MIN_VALUE) 0 else kotlin.math.abs(h)
        // Negative range: dose alarms use non-negative codes.
        return -(v % 1_000_000_000) - 1
    }

    private fun formatTime(millis: Long): String {
        val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(millis))
    }
}
