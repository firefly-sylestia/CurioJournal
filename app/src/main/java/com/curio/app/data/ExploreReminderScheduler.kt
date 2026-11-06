package com.curio.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.curio.app.infrastructure.ExploreReminderReceiver

/**
 * Schedules the "are you done exploring?" reminder that fires when the
 * recommended explore duration of an active session has elapsed.
 *
 * One alarm per session, re-armed on every session start (so a killed
 * process still nudges) and cancelled when the session ends.
 */
object ExploreReminderScheduler {
    private const val REQUEST_CODE = 4210

    /** Fires the reminder at startMillis + durationMinutes. */
    fun schedule(context: Context, startMillis: Long, durationMinutes: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context))
        val triggerAt = startMillis + durationMinutes.coerceAtLeast(1) * 60_000L
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ExploreReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
