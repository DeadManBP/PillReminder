package ca.pillreminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.pillreminder.alarm.AlarmScheduler
import ca.pillreminder.alarm.NotificationHelper
import ca.pillreminder.data.DoseEvent
import ca.pillreminder.data.MedStore
import ca.pillreminder.data.Medication
import ca.pillreminder.data.PendingDose
import ca.pillreminder.ui.HistoryScreen
import ca.pillreminder.ui.MedEditorDialog
import ca.pillreminder.ui.MedsScreen
import ca.pillreminder.ui.TodayScreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PillColors = darkColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB0BEC5),
    tertiary = Color(0xFFFFD54F),
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF141417),
    surfaceVariant = Color(0xFF1E1E22),
    onBackground = Color(0xFFF2F2F2),
    onSurface = Color(0xFFF2F2F2)
)

/** Shared UI state backed by [MedStore]. */
class PillState(val ctx: Context) {
    private val store = MedStore(ctx)

    var meds by mutableStateOf(listOf<Medication>())
        private set
    var log by mutableStateOf(listOf<DoseEvent>())
        private set
    var pending by mutableStateOf<PendingDose?>(null)
        private set

    fun reload() {
        meds = store.loadMeds().sortedBy { it.name.lowercase(Locale.getDefault()) }
        log = store.loadLog(200)
        pending = store.getPending()
    }

    fun saveMed(med: Medication) {
        val clean = med.copy(timesMinutes = med.timesMinutes.sorted().distinct())
        store.upsertMed(clean)
        AlarmScheduler.scheduleMed(ctx, clean)
        reload()
    }

    fun deleteMed(med: Medication) {
        med.timesMinutes.indices.forEach { idx ->
            AlarmScheduler.cancelDoseAlarm(ctx, med.id, idx)
            AlarmScheduler.cancelNag(ctx, med.id, idx)
        }
        val p = pending
        if (p != null && p.medId == med.id) {
            store.clearPending()
            NotificationHelper.cancel(ctx, med.id, p.timeIndex)
        }
        store.deleteMed(med.id)
        reload()
    }

    fun toggleActive(med: Medication, active: Boolean) {
        val updated = med.copy(active = active)
        store.upsertMed(updated)
        if (active) {
            AlarmScheduler.scheduleMed(ctx, updated)
        } else {
            med.timesMinutes.indices.forEach { idx ->
                AlarmScheduler.cancelDoseAlarm(ctx, med.id, idx)
                AlarmScheduler.cancelNag(ctx, med.id, idx)
            }
            val p = pending
            if (p != null && p.medId == med.id) {
                store.clearPending()
                NotificationHelper.cancel(ctx, med.id, p.timeIndex)
            }
        }
        reload()
    }

    fun markTaken(med: Medication, timeIndex: Int, scheduledAt: Long) =
        finishDose(med, timeIndex, scheduledAt, DoseEvent.TAKEN)

    fun markSkipped(med: Medication, timeIndex: Int, scheduledAt: Long) =
        finishDose(med, timeIndex, scheduledAt, DoseEvent.SKIPPED)

    private fun finishDose(med: Medication, timeIndex: Int, scheduledAt: Long, status: String) {
        store.logDose(
            DoseEvent(
                medId = med.id, medName = med.name, dose = med.dose,
                scheduledAt = scheduledAt, status = status
            )
        )
        val p = pending
        if (p != null && p.medId == med.id && p.timeIndex == timeIndex) store.clearPending()
        AlarmScheduler.cancelNag(ctx, med.id, timeIndex)
        NotificationHelper.cancel(ctx, med.id, timeIndex)
        AlarmScheduler.scheduleNextOccurrence(ctx, med.id, timeIndex, scheduledAt)
        reload()
    }

    fun snoozeDose(med: Medication, timeIndex: Int, scheduledAt: Long) {
        NotificationHelper.cancel(ctx, med.id, timeIndex)
        AlarmScheduler.snooze(ctx, med.id, timeIndex, scheduledAt)
        reload()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Today),
    MEDS("Meds", Icons.Filled.Medication),
    HISTORY("History", Icons.Filled.History)
}

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)

        // First launch: arm every active dose.
        val prefs = getSharedPreferences("pillreminder", MODE_PRIVATE)
        if (!prefs.getBoolean("alarms_bootstrapped_v1", false)) {
            AlarmScheduler.scheduleAll(this)
            prefs.edit().putBoolean("alarms_bootstrapped_v1", true).apply()
        }

        setContent {
            MaterialTheme(colorScheme = PillColors) {
                PillApp(onRequestNotifPermission = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PillApp(onRequestNotifPermission: () -> Unit) {
    val ctx = LocalContext.current
    val state = remember { PillState(ctx.applicationContext) }
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var editorTarget by remember { mutableStateOf<Medication?>(null) }

    // Reload whenever the app comes to the foreground.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) state.reload()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // Ask for notification permission once.
    LaunchedEffect(Unit) {
        state.reload()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onRequestNotifPermission()
        }
    }

    var exactOk by remember { mutableStateOf(AlarmScheduler.canScheduleExact(ctx)) }
    LaunchedEffect(tab) { exactOk = AlarmScheduler.canScheduleExact(ctx) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\uD83D\uDC80 Pill Reminder") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.MEDS) {
                FloatingActionButton(
                    onClick = {
                        editorTarget = Medication(
                            id = java.util.UUID.randomUUID().toString(),
                            name = ""
                        )
                    }
                ) { Icon(Icons.Filled.Add, contentDescription = "Add medication") }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            if (!exactOk) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Exact alarms are off — dose reminders may arrive late.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${ctx.packageName}")
                                )
                            )
                        }) { Text("Allow exact alarms") }
                    }
                }
            }
            when (tab) {
                Tab.TODAY -> TodayScreen(state)
                Tab.MEDS -> MedsScreen(state, onEdit = { editorTarget = it })
                Tab.HISTORY -> HistoryScreen(state)
            }
        }
    }

    editorTarget?.let { med ->
        MedEditorDialog(
            initial = med,
            onDismiss = { editorTarget = null },
            onSave = {
                state.saveMed(it)
                editorTarget = null
            }
        )
    }
}

// ---------- formatting helpers ----------

fun minutesLabel(min: Int): String {
    val h = min / 60
    val m = min % 60
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:${m.toString().padStart(2, '0')} $ampm"
}

fun dayShort(dow: Int): String = when (dow) {
    Calendar.MONDAY -> "Mon"
    Calendar.TUESDAY -> "Tue"
    Calendar.WEDNESDAY -> "Wed"
    Calendar.THURSDAY -> "Thu"
    Calendar.FRIDAY -> "Fri"
    Calendar.SATURDAY -> "Sat"
    Calendar.SUNDAY -> "Sun"
    else -> "?"
}

fun dateTimeLabel(millis: Long): String =
    SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(millis))

fun countdownLabel(at: Long, now: Long): String {
    val mins = ((at - now) / 60_000L).toInt().coerceAtLeast(0)
    val h = mins / 60
    return when {
        mins < 1 -> "any moment now"
        h > 0 -> "in ${h}h ${mins % 60}m"
        else -> "in ${mins}m"
    }
}
