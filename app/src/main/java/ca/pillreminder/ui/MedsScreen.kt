package ca.pillreminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.pillreminder.PillState
import ca.pillreminder.data.Medication
import ca.pillreminder.dayShort
import ca.pillreminder.minutesLabel

@Composable
fun MedsScreen(state: PillState, onEdit: (Medication) -> Unit) {
    var deleteTarget by remember { mutableStateOf<Medication?>(null) }

    if (state.meds.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("\uD83D\uDC8A", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text("No medications yet.", color = MaterialTheme.colorScheme.secondary)
                Text(
                    "Tap + to add your first one.",
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
        items(state.meds, key = { it.id }) { med ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                med.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (med.dose.isNotBlank()) {
                                Text(
                                    med.dose,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Switch(
                            checked = med.active,
                            onCheckedChange = { state.toggleActive(med, it) }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        med.timesMinutes.joinToString(" · ") { minutesLabel(it) },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (med.daysOfWeek.isEmpty()) "Every day"
                        else med.daysOfWeek.sortedBy { (it + 5) % 7 }.joinToString(" · ") { dayShort(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (med.notes.isNotBlank()) {
                        Text(
                            med.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onEdit(med) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                        TextButton(onClick = { deleteTarget = med }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { med ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${med.name}?") },
            text = { Text("Its reminders and history entries stay in the log, but no new alarms will fire.") },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteMed(med)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Keep") }
            }
        )
    }
}
