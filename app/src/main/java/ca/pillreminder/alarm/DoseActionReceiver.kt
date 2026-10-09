package ca.pillreminder.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ca.pillreminder.data.DoseEvent
import ca.pillreminder.data.MedStore

/**
 * Handles the Taken / Snooze / Skip buttons on the dose notification.
 */
class DoseActionReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val medId = intent.getStringExtra(AlarmScheduler.EXTRA_MED_ID) ?: return
        val timeIndex = intent.getIntExtra(AlarmScheduler.EXTRA_TIME_INDEX, -1)
        if (timeIndex < 0) return
        val scheduledAt =
            intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, System.currentTimeMillis())

        val store = MedStore(ctx.applicationContext)
        val med = store.getMed(medId)

        when (intent.action) {
            ACTION_TAKEN -> {
                if (med != null) {
                    store.logDose(
                        DoseEvent(
                            medId = medId, medName = med.name, dose = med.dose,
                            scheduledAt = scheduledAt, status = DoseEvent.TAKEN
                        )
                    )
                }
                finishDose(ctx, store, medId, timeIndex, scheduledAt)
            }
            ACTION_SKIP -> {
                if (med != null) {
                    store.logDose(
                        DoseEvent(
                            medId = medId, medName = med.name, dose = med.dose,
                            scheduledAt = scheduledAt, status = DoseEvent.SKIPPED
                        )
                    )
                }
                finishDose(ctx, store, medId, timeIndex, scheduledAt)
            }
            ACTION_SNOOZE -> {
                NotificationHelper.cancel(ctx, medId, timeIndex)
                AlarmScheduler.snooze(ctx, medId, timeIndex, scheduledAt)
            }
        }
    }

    private fun finishDose(
        ctx: Context,
        store: MedStore,
        medId: String,
        timeIndex: Int,
        scheduledAt: Long
    ) {
        val pending = store.getPending()
        if (pending != null && pending.medId == medId && pending.timeIndex == timeIndex) {
            store.clearPending()
        }
        AlarmScheduler.cancelNag(ctx, medId, timeIndex)
        NotificationHelper.cancel(ctx, medId, timeIndex)
        AlarmScheduler.scheduleNextOccurrence(ctx, medId, timeIndex, scheduledAt)
    }

    companion object {
        const val ACTION_TAKEN = "ca.pillreminder.action.TAKEN"
        const val ACTION_SNOOZE = "ca.pillreminder.action.SNOOZE"
        const val ACTION_SKIP = "ca.pillreminder.action.SKIP"

        fun actionIntent(
            ctx: Context,
            action: String,
            medId: String,
            timeIndex: Int,
            scheduledAt: Long
        ): PendingIntent {
            val intent = Intent(ctx, DoseActionReceiver::class.java)
                .setAction(action)
                .putExtra(AlarmScheduler.EXTRA_MED_ID, medId)
                .putExtra(AlarmScheduler.EXTRA_TIME_INDEX, timeIndex)
                .putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
            val rc = AlarmScheduler.requestCode(medId, timeIndex) + when (action) {
                ACTION_TAKEN -> 100_000_000
                ACTION_SNOOZE -> 200_000_000
                else -> 300_000_000
            }
            return PendingIntent.getBroadcast(
                ctx, rc, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
