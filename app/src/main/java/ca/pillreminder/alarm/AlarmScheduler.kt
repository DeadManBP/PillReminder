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
        val store = MedStore(ctx)
        val todayStr = todayString(now)
        med.timesMinutes.forEachIndexed { idx, t ->
            // One-time override for today takes precedence if still in the future.
            val overrideMin = store.getDoseOverride(med.id, idx, todayStr)
            val trigger = if (overrideMin != null) {
                val c = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, overrideMin / 60)
                    set(Calendar.MINUTE, overrideMin % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (c.timeInMillis > now) c.timeInMillis else nextTrigger(med, t, now)
            } else {
                nextTrigger(med, t, now)
            }
            setAlarm(ctx, med.id, idx, trigger, ACTION_DOSE, false, trigger)
        }
    }

    /** Shift one dose to [newMinutes] (minutes since midnight) for today only. */
    fun rescheduleDoseForToday(
        ctx: Context,
        medId: String,
        timeIndex: Int,
        newMinutes: Int
    ): Boolean {
        val store = MedStore(ctx)
        val med = store.getMed(medId) ?: return false
        if (!med.active || timeIndex !in med.timesMinutes.indices) return false
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, newMinutes / 60)
            set(Calendar.MINUTE, newMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) return false
        val todayStr = todayString(now)
        store.setDoseOverride(medId, timeIndex, todayStr, newMinutes)
        cancelDoseAlarm(ctx, medId, timeIndex)
        cancelNag(ctx, medId, timeIndex)
        setAlarm(ctx, medId, timeIndex, c.timeInMillis, ACTION_DOSE, false, c.timeInMillis)
        return true
    }

    /** Clear today's one-time shift and re-arm the normal schedule. */
    fun clearTodayOverride(ctx: Context, medId: String, timeIndex: Int) {
        val store = MedStore(ctx)
        val todayStr = todayString(System.currentTimeMillis())
        store.clearDoseOverride(medId, timeIndex, todayStr)
        cancelDoseAlarm(ctx, medId, timeIndex)
        cancelNag(ctx, medId, timeIndex)
        val med = store.getMed(medId) ?: return
        if (!med.active || timeIndex !in med.timesMinutes.indices) return
        val trigger = nextTrigger(med, med.timesMinutes[timeIndex], System.currentTimeMillis())
        setAlarm(ctx, medId, timeIndex, trigger, ACTION_DOSE, false, trigger)
    }

    fun todayString(millis: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return fmt.format(java.util.Date(millis))
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
