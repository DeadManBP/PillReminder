package ca.pillreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ca.pillreminder.data.DoseEvent
import ca.pillreminder.data.MedStore
import ca.pillreminder.data.PendingDose

/**
 * Fires when a dose is due (or a nag re-fires). Shows the persistent
 * notification, arms the next nag, and queues the dose's next occurrence.
 */
class DoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val medId = intent.getStringExtra(AlarmScheduler.EXTRA_MED_ID) ?: return
        val timeIndex = intent.getIntExtra(AlarmScheduler.EXTRA_TIME_INDEX, -1)
        if (timeIndex < 0) return
        val scheduledAt =
            intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, System.currentTimeMillis())
        val isNag = intent.action == AlarmScheduler.ACTION_NAG
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)

        val store = MedStore(ctx.applicationContext)
        val med = store.getMed(medId)
        if (med == null || !med.active || timeIndex >= med.timesMinutes.size) {
            NotificationHelper.cancel(ctx, medId, timeIndex)
            return
        }

        if (isNag) {
            val pending = store.getPending()
            // Dose already handled (taken / skipped / snoozed / med changed).
            if (pending == null ||
                pending.medId != medId ||
                pending.timeIndex != timeIndex ||
                pending.scheduledAt != scheduledAt
            ) return
            if (pending.nagCount < AlarmScheduler.MAX_NAGS) {
                val updated = pending.copy(nagCount = pending.nagCount + 1)
                store.setPending(updated)
                NotificationHelper.showDose(ctx, med, timeIndex, scheduledAt, updated.nagCount)
                AlarmScheduler.setAlarm(
                    ctx, medId, timeIndex,
                    System.currentTimeMillis() + AlarmScheduler.NAG_DELAY_MIN * 60_000L,
                    AlarmScheduler.ACTION_NAG, false, scheduledAt
                )
            } else {
                store.logDose(
                    DoseEvent(
                        medId = medId, medName = med.name, dose = med.dose,
                        scheduledAt = scheduledAt, status = DoseEvent.MISSED
                    )
                )
                store.clearPending()
                NotificationHelper.cancel(ctx, medId, timeIndex)
            }
            return
        }

        // Fresh dose alarm (or snooze re-fire).
        if (!isSnooze) {
            store.setPending(PendingDose(medId, timeIndex, scheduledAt, 0))
            AlarmScheduler.scheduleNextOccurrence(ctx, medId, timeIndex, scheduledAt)
        }
        NotificationHelper.showDose(ctx, med, timeIndex, scheduledAt, 0)
        AlarmScheduler.setAlarm(
            ctx, medId, timeIndex,
            System.currentTimeMillis() + AlarmScheduler.NAG_DELAY_MIN * 60_000L,
            AlarmScheduler.ACTION_NAG, false, scheduledAt
        )
    }
}
