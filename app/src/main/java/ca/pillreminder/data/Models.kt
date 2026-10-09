package ca.pillreminder.data

import java.util.UUID

/** One medication with one or more daily dose times. */
data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dose: String = "",
    /** Minutes since midnight, sorted ascending. */
    val timesMinutes: List<Int> = emptyList(),
    /** Calendar.DAY_OF_WEEK values; empty = every day. */
    val daysOfWeek: Set<Int> = emptySet(),
    val active: Boolean = true,
    val notes: String = ""
)

/** A logged dose outcome. */
data class DoseEvent(
    val id: String = UUID.randomUUID().toString(),
    val medId: String,
    val medName: String,
    val dose: String,
    val scheduledAt: Long,
    val status: String,
    val actedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TAKEN = "taken"
        const val SKIPPED = "skipped"
        const val MISSED = "missed"
    }
}

/** A dose whose alarm fired and hasn't been acknowledged yet. */
data class PendingDose(
    val medId: String,
    val timeIndex: Int,
    val scheduledAt: Long,
    val nagCount: Int = 0
)
