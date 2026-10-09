package ca.pillreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arm all dose alarms after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> AlarmScheduler.scheduleAll(ctx)
        }
    }
}
