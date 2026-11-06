package com.curio.app.infrastructure

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.curio.app.data.AppPreferences
import com.curio.app.data.ExploreReminderScheduler
import com.curio.app.data.ExploreSessionStore

/**
 * Rebuilds an active explore session after a reboot / app update / clock
 * change: re-arms the timer service (the chronometer keeps counting from
 * the original start time) and, inside the service, the reminder alarm.
 * Sessions intentionally "don't die" — state is persisted, so this simply
 * resumes it.
 */
class ExploreBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        // Crash-loop safe mode: don't re-arm anything in the background — the
        // user should first see the crash screen and restart cleanly.
        if (CurioCrashReporter.isSafeMode(context)) return
        if (!AppPreferences.isExploreSessionsEnabled(context)) return
        val session = ExploreSessionStore.getActiveSession(context) ?: return
        // Always re-arm the reminder after reboot/app-update/clock change —
        // it fires even when the service isn't running.
        ExploreReminderScheduler.schedule(context, session.startMillis, session.durationMinutes)
        if (AppPreferences.exploreServiceShouldRun(context)) {
            ExploreSessionService.start(context, session)
        }
    }
}
