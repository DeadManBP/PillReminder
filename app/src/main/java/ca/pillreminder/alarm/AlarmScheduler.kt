package ca.pillreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ca.pillreminder.data.MedStore
import ca.pillreminder.data.Medication
import java.util.Calendar
import kotlin.math.abs

/**
 * Schedules exact alarms for every active dose. Alarms survive app kills;
 * [BootReceiver] re-arms everything after reboot or app update.
 */
object AlarmScheduler {

    const val ACTION_DOSE = "ca.pillreminder.action.DOSE"
    const val ACTION_NAG = "ca.pillreminder.action.NAG"
    const val EXTRA_MED_ID = "med_id"
    const val EXTRA_TIME_INDEX = "time_index"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"
    const val EXTRA_SNOOZE = "snooze"

    const val NAG_DELAY_MIN = 10L
    const val MAX_NAGS = 6

    private fun alarms(ctx: Context): AlarmManager =
        ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) alarms(ctx).canScheduleExactAlarms() else true

    /** Stable non-negative request code per (med, time slot). */
    fun requestCode(medId: String, timeIndex: Int): Int {
        val h = "$medId#$timeIndex".hashCode()
        val v = if (h == Int.MIN_VALUE) 0 else abs(h)
        return v % 1_000_000_000
    }

    private fun alarmIntent(
        ctx: Context,
        medId: String,
        timeIndex: Int,
        scheduledAt: Long,
        action: String,
        snooze: Boolean
    ): PendingIntent {
        val intent = Intent(ctx, DoseAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_MED_ID, medId)
            .putExtra(EXTRA_TIME_INDEX, timeIndex)
            .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
            .putExtra(EXTRA_SNOOZE, snooze)
        val rc = requestCode(medId, timeIndex) + if (action == ACTION_NAG) 700_000_000 else 0
        return PendingIntent.getBroadcast(
            ctx, rc, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun setAlarm(
        ctx: Context,
        medId: String,
        timeIndex: Int,
        triggerAt: Long,
        action: String,
        snooze: Boolean,
        scheduledAt: Long
    ) {
        val pi = alarmIntent(ctx, medId, timeIndex, scheduledAt, action, snooze)
        try {
            alarms(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (se: SecurityException) {
            // Exact alarms denied: fall back to an inexact alarm rather than nothing.
            alarms(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelDoseAlarm(ctx: Context, medId: String, timeIndex: Int) {
        alarms(ctx).cancel(alarmIntent(ctx, medId, timeIndex, 0L, ACTION_DOSE, false))
    }

    fun cancelNag(ctx: Context, medId: String, timeIndex: Int) {
        alarms(ctx).cancel(alarmIntent(ctx, medId, timeIndex, 0L, ACTION_NAG, false))
    }

    /** Next wall-clock trigger for [timeMinutes] strictly after [fromMillis], honouring days. */
    fun nextTrigger(med: Medication, timeMinutes: Int, fromMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, timeMinutes / 60)
            set(Calendar.MINUTE, timeMinutes % 60)
        }
        repeat(8) {
            val dayOk = med.daysOfWeek.isEmpty() ||
                med.daysOfWeek.contains(cal.get(Calendar.DAY_OF_WEEK))
            if (dayOk && cal.timeInMillis > fromMillis) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun scheduleMed(ctx: Context, med: Medication) {
        if (!med.active || med.timesMinutes.isEmpty()) return
        val now = System.currentTimeMillis()
        med.timesMinutes.forEachIndexed { idx, t ->
            val trigger = nextTrigger(med, t, now)
            setAlarm(ctx, med.id, idx, trigger, ACTION_DOSE, false, trigger)
        }
    }

    /** Cancel everything tracked, then re-arm all active meds. */
    fun scheduleAll(ctx: Context) {
        val store = MedStore(ctx)
        val meds = store.loadMeds()
        meds.forEach { med ->
            med.timesMinutes.indices.forEach { idx ->
                cancelDoseAlarm(ctx, med.id, idx)
                cancelNag(ctx, med.id, idx)
            }
        }
        meds.forEach { scheduleMed(ctx, it) }
    }

    /** Arm the next occurrence of one dose slot after [afterMillis]. */
    fun scheduleNextOccurrence(ctx: Context, medId: String, timeIndex: Int, afterMillis: Long) {
        val med = MedStore(ctx).getMed(medId) ?: return
        if (!med.active || timeIndex !in med.timesMinutes.indices) return
        val trigger = nextTrigger(med, med.timesMinutes[timeIndex], afterMillis)
        setAlarm(ctx, medId, timeIndex, trigger, ACTION_DOSE, false, trigger)
    }

    fun snooze(ctx: Context, medId: String, timeIndex: Int, scheduledAt: Long, minutes: Long = NAG_DELAY_MIN) {
        cancelNag(ctx, medId, timeIndex)
        setAlarm(
            ctx, medId, timeIndex,
            System.currentTimeMillis() + minutes * 60_000L,
            ACTION_DOSE, true, scheduledAt
        )
    }
}
