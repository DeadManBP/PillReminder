package ca.pillreminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.pillreminder.data.Medication
import ca.pillreminder.dayShort
import ca.pillreminder.minutesLabel
import java.util.Calendar

private val WEEK_DAYS = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedEditorDialog(
    initial: Medication,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var dose by remember { mutableStateOf(initial.dose) }
    var times by remember { mutableStateOf(initial.timesMinutes.sorted()) }
    var days by remember { mutableStateOf(initial.daysOfWeek.toSet()) }
    var notes by remember { mutableStateOf(initial.notes) }
    var active by remember { mutableStateOf(initial.active) }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (initial.name.isBlank()) "Add medication" else "Edit medication",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Medication name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dose,
                        onValueChange = { dose = it },
                        label = { Text("Dose (e.g. 1 tablet, 10 mg)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Dose times *", fontWeight = FontWeight.Bold)
                    times.forEach { t ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(minutesLabel(t), modifier = Modifier.weight(1f))
                            TextButton(onClick = { times = times - t }) { Text("Remove") }
                        }
                    }
                    OutlinedButton(onClick = { showPicker = true }) { Text("+ Add time") }

                    Text("Days (none selected = every day)", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WEEK_DAYS.forEach { d ->
                            FilterChip(
                                selected = d in days,
                                onClick = { days = if (d in days) days - d else days + d },
                                label = { Text(dayShort(d)) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reminders active", modifier = Modifier.weight(1f))
                        Switch(checked = active, onCheckedChange = { active = it })
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        when {
                            name.isBlank() -> error = "Give the medication a name."
                            times.isEmpty() -> error = "Add at least one dose time."
                            else -> onSave(
                                initial.copy(
                                    name = name.trim(),
                                    dose = dose.trim(),
                                    timesMinutes = times.sorted(),
                                    daysOfWeek = days,
                                    notes = notes.trim(),
                                    active = active
                                )
                            )
                        }
                    }) { Text("Save") }
                }
            }
        }
    }

    if (showPicker) {
        val tpState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Pick a time") },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    val m = tpState.hour * 60 + tpState.minute
                    if (m !in times) times = (times + m).sorted()
                    showPicker = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        )
    }
}
