package ca.pillreminder.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * All data lives on-device in SharedPreferences as JSON. No account, no network.
 */
class MedStore(ctx: Context) {

    private val prefs: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("pillreminder", Context.MODE_PRIVATE)

    // ---------- Medications ----------

    fun loadMeds(): List<Medication> {
        val out = mutableListOf<Medication>()
        val arr = JSONArray(prefs.getString(KEY_MEDS, "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val days = mutableSetOf<Int>()
            val daysArr = o.optJSONArray("days")
            if (daysArr != null) for (j in 0 until daysArr.length()) days.add(daysArr.optInt(j))
            val times = mutableListOf<Int>()
            val timesArr = o.optJSONArray("times")
            if (timesArr != null) for (j in 0 until timesArr.length()) times.add(timesArr.optInt(j))
            out.add(
                Medication(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    dose = o.optString("dose"),
                    timesMinutes = times.sorted(),
                    daysOfWeek = days,
                    active = o.optBoolean("active", true),
                    notes = o.optString("notes")
                )
            )
        }
        return out
    }

    fun saveMeds(meds: List<Medication>) {
        val arr = JSONArray()
        for (m in meds) {
            val daysArr = JSONArray()
            for (d in m.daysOfWeek) daysArr.put(d)
            val timesArr = JSONArray()
            for (t in m.timesMinutes) timesArr.put(t)
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("dose", m.dose)
                    .put("times", timesArr)
                    .put("days", daysArr)
                    .put("active", m.active)
                    .put("notes", m.notes)
            )
        }
        prefs.edit().putString(KEY_MEDS, arr.toString()).apply()
    }

    fun getMed(id: String): Medication? = loadMeds().find { it.id == id }

    fun upsertMed(med: Medication) {
        val meds = loadMeds().toMutableList()
        val i = meds.indexOfFirst { it.id == med.id }
        if (i >= 0) meds[i] = med else meds.add(med)
        saveMeds(meds)
    }

    fun deleteMed(id: String) {
        saveMeds(loadMeds().filterNot { it.id == id })
    }

    // ---------- Dose log ----------

    fun logDose(event: DoseEvent) {
        val arr = JSONArray(prefs.getString(KEY_LOG, "[]"))
        arr.put(
            JSONObject()
                .put("id", event.id)
                .put("medId", event.medId)
                .put("medName", event.medName)
                .put("dose", event.dose)
                .put("scheduledAt", event.scheduledAt)
                .put("status", event.status)
                .put("actedAt", event.actedAt)
        )
        while (arr.length() > MAX_LOG) arr.remove(0)
        prefs.edit().putString(KEY_LOG, arr.toString()).apply()
    }

    fun loadLog(limit: Int = 200): List<DoseEvent> {
        val out = mutableListOf<DoseEvent>()
        val arr = JSONArray(prefs.getString(KEY_LOG, "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                DoseEvent(
                    id = o.optString("id"),
                    medId = o.optString("medId"),
                    medName = o.optString("medName"),
                    dose = o.optString("dose"),
                    scheduledAt = o.optLong("scheduledAt"),
                    status = o.optString("status"),
                    actedAt = o.optLong("actedAt")
                )
            )
        }
        return out.sortedByDescending { it.actedAt }.take(limit)
    }

    fun findLog(medId: String, scheduledAt: Long): DoseEvent? =
        loadLog(MAX_LOG).find { it.medId == medId && it.scheduledAt == scheduledAt }

    // ---------- Pending (unacknowledged) dose ----------

    fun setPending(p: PendingDose) {
        prefs.edit().putString(
            KEY_PENDING,
            JSONObject()
                .put("medId", p.medId)
                .put("timeIndex", p.timeIndex)
                .put("scheduledAt", p.scheduledAt)
                .put("nagCount", p.nagCount)
                .toString()
        ).apply()
    }

    fun getPending(): PendingDose? {
        val raw = prefs.getString(KEY_PENDING, null) ?: return null
        val o = JSONObject(raw)
        return PendingDose(
            medId = o.optString("medId"),
            timeIndex = o.optInt("timeIndex"),
            scheduledAt = o.optLong("scheduledAt"),
            nagCount = o.optInt("nagCount")
        )
    }

    fun clearPending() {
        prefs.edit().remove(KEY_PENDING).apply()
    }

    companion object {
        private const val KEY_MEDS = "meds_v1"
        private const val KEY_LOG = "log_v1"
        private const val KEY_PENDING = "pending_v1"
        private const val MAX_LOG = 500
    }
}
