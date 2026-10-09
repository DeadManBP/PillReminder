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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.log, key = { it.id }) { e ->
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
                        Text(
                            "Scheduled ${dateTimeLabel(e.scheduledAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(label, color = tint, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
