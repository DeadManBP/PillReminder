package ca.pillreminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.pillreminder.PillState
import ca.pillreminder.data.DoseEvent
import ca.pillreminder.dateTimeLabel

@Composable
fun HistoryScreen(state: PillState) {
    if (state.log.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing logged yet.", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }

    // Group entries by day (based on when the action was taken).
    val byDay = remember(state.log) {
        state.log.groupBy { dayKey(it.actedAt) }
            .toList()
            .sortedByDescending { it.first }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for ((day, entries) in byDay) {
            item(key = "header-$day") {
                DayHeader(day, entries)
            }
            items(entries, key = { e: DoseEvent -> e.id }) { e ->
                HistoryEntry(e)
            }
        }
    }
}

/** yyyy-MM-dd key for grouping. */
private fun dayKey(millis: Long): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return fmt.format(java.util.Date(millis))
}

private fun dayLabel(day: String): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val date = fmt.parse(day) ?: return day
    return java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault()).format(date)
}

@Composable
private fun DayHeader(day: String, entries: List<DoseEvent>) {
    val taken = entries.count { it.status == DoseEvent.TAKEN }
    val total = entries.size
    val allTaken = taken == total && total > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allTaken)
                Color(0xFF1B5E20).copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                dayLabel(day),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (allTaken) "\u2705 $taken/$total" else "$taken/$total taken",
                style = MaterialTheme.typography.bodyMedium,
                color = if (allTaken) Color(0xFF66BB6A) else MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun HistoryEntry(e: DoseEvent) {
    val (icon, tint, label) = when (e.status) {
        DoseEvent.TAKEN -> Triple(Icons.Filled.CheckCircle, Color(0xFF66BB6A), "Taken")
        DoseEvent.SKIPPED -> Triple(Icons.Filled.Cancel, Color(0xFFB0BEC5), "Skipped")
        else -> Triple(Icons.Filled.AlarmOff, Color(0xFFE57373), "Missed")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = tint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.medName + (e.dose.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                // Show actual time acted, plus scheduled time for context.
                val timeLine = when (e.status) {
                    DoseEvent.TAKEN -> {
                        val acted = dateTimeLabel(e.actedAt)
                        val sched = dateTimeLabel(e.scheduledAt)
                        if (acted == sched) "Taken $acted"
                        else "Taken $acted (scheduled $sched)"
                    }
                    else -> "Scheduled ${dateTimeLabel(e.scheduledAt)}"
                }
                Text(
                    timeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(label, color = tint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
