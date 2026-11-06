package com.curio.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One autosaved in-progress capture draft.
 *
 * @property categorySlug The category route slug the capture page belongs to
 *   (the key alongside [topicName] — a draft only offers to resume on the
 *   SAME topic's save page, never a different one).
 * @property topicName The topic being captured.
 * @property dataJson Gson-serialized [CaptureData] snapshot (the combined
 *   take data the format editors last emitted).
 * @property savedAtMillis When the draft was last written — surfaced in the
 *   resume prompt so the user can tell a fresh draft from a stale one.
 */
data class CaptureDraft(
    val categorySlug: String,
    val topicName: String,
    val dataJson: String,
    val savedAtMillis: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("categorySlug", categorySlug)
        .put("topicName", topicName)
        .put("dataJson", dataJson)
        .put("savedAtMillis", savedAtMillis)

    companion object {
        fun fromJson(obj: JSONObject): CaptureDraft? = runCatching {
            CaptureDraft(
                categorySlug = obj.optString("categorySlug"),
                topicName = obj.optString("topicName"),
                dataJson = obj.optString("dataJson"),
                savedAtMillis = obj.optLong("savedAtMillis", System.currentTimeMillis())
            )
        }.getOrNull()
    }
}

/**
 * Persists autosaved capture drafts (v7.17).
 *
 * While the user is on a save page, the current capture data is written here
 * (debounced) so a stray back, an app kill, or a rotation never silently
 * loses drafted content — reopening the SAME topic's save page offers to
 * resume the draft. A successful save clears it.
 *
 * Stored as a JSON array (one entry per category+topic) so topic names with
 * arbitrary characters survive round-trips, same pattern as the pinned
 * topics / saved quotes stores. Only a handful of drafts exist at once —
 * capped so stale topics don't accumulate forever.
 */
object CaptureDraftStore {

    private const val KEY_DRAFTS = "capture_drafts"
    private const val MAX_DRAFTS = 8

    private fun prefs(context: Context) =
        context.getSharedPreferences("curio_prefs", Context.MODE_PRIVATE)

    // ── Reads ──────────────────────────────────────────────────────────

    /** All stored drafts, newest first. */
    fun all(context: Context): List<CaptureDraft> {
        val raw = prefs(context).getString(KEY_DRAFTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> CaptureDraft.fromJson(arr.getJSONObject(i)) }
                .filterNotNull()
                .sortedByDescending { it.savedAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** The stored draft for a specific category + topic, or null. */
    fun get(context: Context, categorySlug: String, topicName: String): CaptureDraft? =
        all(context).firstOrNull {
            it.categorySlug == categorySlug && it.topicName == topicName
        }

    // ── Writes ─────────────────────────────────────────────────────────

    /**
     * Upserts the draft for [categorySlug] + [topicName] (deduped, newest
     * first, capped at [MAX_DRAFTS]).
     */
    fun save(context: Context, categorySlug: String, topicName: String, dataJson: String) {
        if (categorySlug.isBlank() || topicName.isBlank() || dataJson.isBlank()) return
        val current = all(context).filterNot {
            it.categorySlug == categorySlug && it.topicName == topicName
        }
        val updated = listOf(
            CaptureDraft(categorySlug, topicName, dataJson, System.currentTimeMillis())
        ) + current
        write(context, updated.take(MAX_DRAFTS))
    }

    /** Removes the draft for this category + topic (saved / explicitly discarded). */
    fun clear(context: Context, categorySlug: String, topicName: String) {
        val updated = all(context).filterNot {
            it.categorySlug == categorySlug && it.topicName == topicName
        }
        write(context, updated)
    }

    private fun write(context: Context, drafts: List<CaptureDraft>) {
        val arr = JSONArray()
        drafts.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_DRAFTS, arr.toString()).apply()
    }
}
