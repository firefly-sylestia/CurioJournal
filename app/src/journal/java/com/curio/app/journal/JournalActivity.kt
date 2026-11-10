package com.curio.app.journal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.curio.app.data.AppPreferences
import com.curio.app.data.CurioDatabase
import com.curio.app.data.CurioRepositoryHolder
import com.curio.app.data.PersonalRepositoryHolder
import com.curio.app.features.personal.JournalEditorScreen
import com.curio.app.navigation.CurioRoutes
import com.curio.app.ui.CurioLayout
import com.curio.app.ui.theme.CurioTheme
import com.curio.app.ui.theme.CurioThemeTransitionHost

/** The journal build's own front door — today, and the day's page. */
const val JOURNAL_HOME = "journal-home"

/** The first open, where the member's name is asked for (see [JournalIntroScreen]). */
const val JOURNAL_INTRO = "journal-intro"

/** The journal build's own settings page (see [JournalSettingsScreen]). */
const val JOURNAL_SETTINGS = "journal-settings"

/** The month, as a page of doors (see [JournalCalendarScreen]). */
const val JOURNAL_CALENDAR = "journal-calendar"

/**
 * The journal edition's single Activity.
 *
 * It exists because the journal is a BUILD, not a screen: the discovery app's
 * [com.curio.app.MainActivity] boots a spinner, a topic catalog, quests, a pet,
 * an explore-session store and a social layer, none of which a journal needs.
 * This one starts the writing store and nothing else, and hosts four routes:
 *
 *  · [JOURNAL_HOME] — [JournalHomeScreen], the day-focused home
 *  · [CurioRoutes.JOURNALS] — the existing collection screen, untouched
 *  · [CurioRoutes.JOURNAL_EDITOR] — the existing page editor, untouched
 *  · [JOURNAL_SETTINGS] — [JournalSettingsScreen], the redesigned settings
 *
 * The reading density block is copied from MainActivity on purpose: it is the
 * app's own rule (90% type in a short window, 97% elsewhere, multiplied by the
 * member's system text size), and a journal that read one step larger than the
 * app it came from would not be the same app.
 */
class JournalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The writing store. The capture repository is started too — the page's
        // host reads it for the member's own export/format helpers, and it is
        // the same one-time setup MainActivity does.
        val db = CurioDatabase.getInstance(this)
        CurioRepositoryHolder.init(db.captureDao(), db.cachedTopicDao())
        PersonalRepositoryHolder.init(db.personalDao())

        // The theme the page is painted with (light / dark / system).
        AppPreferences.initThemeMode(this)

        setContent {
            val windowDensity = LocalDensity.current
            val compactWindow = CurioLayout.isCompact()
            val readingDensity = remember(windowDensity, compactWindow) {
                val step = if (compactWindow) 0.90f else 0.97f
                Density(windowDensity.density, windowDensity.fontScale * step)
            }
            CompositionLocalProvider(LocalDensity provides readingDensity) {
                CurioTheme {
                    CurioThemeTransitionHost {
                        JournalApp()
                    }
                }
            }
        }
    }
}

/** The journal's own graph. Deliberately five destinations, no tabs. */
@Composable
private fun JournalApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    // v513 — the first open asks for a name, once. A member who skipped keeps the
    // plain "Hello", and the intro is never shown again because its answer (even
    // a blank one) is stored as soon as the door is pressed.
    val start = if (AppPreferences.getJournalName(context).isBlank()) {
        JOURNAL_INTRO
    } else {
        JOURNAL_HOME
    }
    NavHost(navController = navController, startDestination = start) {

        composable(JOURNAL_INTRO) {
            JournalIntroScreen(
                onDone = {
                    navController.navigate(JOURNAL_HOME) {
                        popUpTo(JOURNAL_INTRO) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(JOURNAL_HOME) {
            JournalHomeScreen(
                onOpenDay = { millis, entryId ->
                    if (entryId != null) {
                        navController.navigate(CurioRoutes.journalEditor(entryId)) {
                            launchSingleTop = true
                        }
                    } else {
                        // A day with no page yet: the editor seeds itself from
                        // this stash (the same handoff Home's empty row uses).
                        com.curio.app.navigation.PendingJournalDay.request(millis)
                        navController.navigate(
                            CurioRoutes.journalEditor(CurioRoutes.PERSONAL_NEW)
                        ) { launchSingleTop = true }
                    }
                },
                onOpenCollection = {
                    navController.navigate(CurioRoutes.JOURNALS) { launchSingleTop = true }
                },
                onOpenCalendar = {
                    navController.navigate(JOURNAL_CALENDAR) { launchSingleTop = true }
                },
                onOpenSettings = {
                    navController.navigate(JOURNAL_SETTINGS) { launchSingleTop = true }
                }
            )
        }

        composable(JOURNAL_CALENDAR) {
            JournalCalendarScreen(
                onBack = { navController.popBackStack() },
                onOpenDay = { millis, entryId ->
                    if (entryId != null) {
                        navController.navigate(CurioRoutes.journalEditor(entryId)) {
                            launchSingleTop = true
                        }
                    } else {
                        com.curio.app.navigation.PendingJournalDay.request(millis)
                        navController.navigate(
                            CurioRoutes.journalEditor(CurioRoutes.PERSONAL_NEW)
                        ) { launchSingleTop = true }
                    }
                }
            )
        }

        composable(CurioRoutes.JOURNALS) {
            // v536 — the journal's OWN collection (see JournalCollectionScreen):
            // the shared `features/personal/JournalListScreen` wears the app's
            // tokens and is not compiled against JournalLook.
            JournalCollectionScreen(navController = navController)
        }

        composable(
            route = CurioRoutes.JOURNAL_EDITOR,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { entry ->
            JournalEditorScreen(
                navController = navController,
                entryIdArg = entry.arguments?.getString("entryId")
                    ?: CurioRoutes.PERSONAL_NEW
            )
        }

        composable(JOURNAL_SETTINGS) {
            JournalSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenCollection = {
                    navController.navigate(CurioRoutes.JOURNALS) { launchSingleTop = true }
                },
                onOpenCalendar = {
                    navController.navigate(JOURNAL_CALENDAR) { launchSingleTop = true }
                }
            )
        }
    }
}
