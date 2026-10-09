package ca.pillreminder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.pillreminder.PillState
import ca.pillreminder.countdownLabel
import ca.pillreminder.data.DoseEvent
import ca.pillreminder.data.Medication
import ca.pillreminder.minutesLabel
import kotlinx.coroutines.delay
import java.util.Calendar

private enum class DoseStatus { DUE, TAKEN, SKIPPED, MISSED, OPEN, UPCOMING }

private data class TodayDose(
    val med: Medication,
    val timeIndex: Int,
    val scheduledAt: Long,
    val status: DoseStatus
)

@Composable
fun TodayScreen(state: PillState) {
    // Tick every minute so countdowns stay fresh.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick++
        }
    }

    val now = remember(tick) { System.currentTimeMillis() }
    val doses = remember(state.meds, state.log, state.pending, now) {
        computeTodayDoses(state, now)
    }

    if (doses.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("\uD83D\uDC8A", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text("No doses scheduled today.", color = MaterialTheme.colorScheme.secondary)
                Text(
                    "Add your meds on the Meds tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(doses, key = { "${it.med.id}#${it.timeIndex}#${it.scheduledAt}" }) { dose ->
            DoseCard(dose, state, now)
        }
    }
}

private fun computeTodayDoses(state: PillState, now: Long): List<TodayDose> {
    val base = Calendar.getInstance()
    val todayDow = base.get(Calendar.DAY_OF_WEEK)
    val out = mutableListOf<TodayDose>()
    for (med in state.meds) {
        if (!med.active) continue
        if (med.daysOfWeek.isNotEmpty() && todayDow !in med.daysOfWeek) continue
        med.timesMinutes.forEachIndexed { idx, t ->
            val c = (base.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, t / 60)
                set(Calendar.MINUTE, t % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val at = c.timeInMillis
            val pending = state.pending
            val status = when {
                pending != null && pending.medId == med.id && pending.timeIndex == idx -> DoseStatus.DUE
                else -> {
                    val e = state.log.find { it.medId == med.id && it.scheduledAt == at }
                    when {
                        e != null -> when (e.status) {
                            DoseEvent.TAKEN -> DoseStatus.TAKEN
                            DoseEvent.SKIPPED -> DoseStatus.SKIPPED
                            else -> DoseStatus.MISSED
                        }
                        at <= now -> DoseStatus.OPEN
                        else -> DoseStatus.UPCOMING
                    }
                }
            }
            out.add(TodayDose(med, idx, at, status))
        }
    }
    return out.sortedWith(compareBy({ it.status != DoseStatus.DUE }, { it.scheduledAt }))
}

@Composable
private fun DoseCard(dose: TodayDose, state: PillState, now: Long) {
    val med = dose.med
    val due = dose.status == DoseStatus.DUE

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (due) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (due)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(dose.status)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(med.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val sub = listOfNotNull(
                        med.dose.takeIf { it.isNotBlank() },
                        minutesLabel(med.timesMinutes[dose.timeIndex])
                    ).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(Modifier.height(6.dp))
            when (dose.status) {
                DoseStatus.DUE -> {
                    Text(
                        "\u23F0 DUE NOW — tap Taken once you've had it",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { state.markTaken(med, dose.timeIndex, dose.scheduledAt) }) {
                            Text("Taken")
                        }
                        OutlinedButton(onClick = { state.snoozeDose(med, dose.timeIndex, dose.scheduledAt) }) {
                            Text("Snooze 10m")
                        }
                        TextButton(onClick = { state.markSkipped(med, dose.timeIndex, dose.scheduledAt) }) {
                            Text("Skip")
                        }
                    }
                }
                DoseStatus.UPCOMING -> {
                    Text(
                        countdownLabel(dose.scheduledAt, now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                DoseStatus.OPEN -> {
                    Text(
                        "Time passed — not logged yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFB74D)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { state.markTaken(med, dose.timeIndex, dose.scheduledAt) }) {
                            Text("Take now")
                        }
                        TextButton(onClick = { state.markSkipped(med, dose.timeIndex, dose.scheduledAt) }) {
                            Text("Skip")
                        }
                    }
                }
                DoseStatus.TAKEN -> StatusLine("\u2705 Taken", Color(0xFF66BB6A))
                DoseStatus.SKIPPED -> StatusLine("\u23ED\uFE0F Skipped", Color(0xFFB0BEC5))
                DoseStatus.MISSED -> StatusLine("\u274C Missed", Color(0xFFE57373))
            }
        }
    }
}

@Composable
private fun StatusIcon(status: DoseStatus) {
    when (status) {
        DoseStatus.DUE -> Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.primary)
        DoseStatus.TAKEN -> Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF66BB6A))
        DoseStatus.SKIPPED, DoseStatus.MISSED -> Icon(Icons.Filled.Cancel, null, tint = Color(0xFFE57373))
        DoseStatus.OPEN -> Icon(Icons.Filled.Alarm, null, tint = Color(0xFFFFB74D))
        DoseStatus.UPCOMING -> Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}
