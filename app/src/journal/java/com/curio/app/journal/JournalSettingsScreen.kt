package com.curio.app.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curio.app.BuildConfig
import com.curio.app.data.AppPreferences
import com.curio.app.features.personal.journalInk
import com.curio.app.features.personal.journalPaper
import com.curio.app.features.personal.journalPaperRaised
import com.curio.app.features.personal.personalAccent
import com.curio.app.features.personal.personalAccentInk
import com.curio.app.features.personal.personalOnAccent
import com.curio.app.ui.theme.CurioIcon
import com.curio.app.ui.theme.CurioIcons
import com.curio.app.ui.theme.FrauncesFontFamily

/**
 * THE JOURNAL'S SETTINGS — only the things a journal has.
 *
 * The discovery app's settings hub is nine sections deep (lanes, quests, the
 * online layer, experiments, the pet designer, reader voices…) because that app
 * has nine families. This build has ONE, so its settings are three short cards:
 * how the page looks, how the writing habit is kept, and where the pages live.
 *
 * Every row here is wired to the preference the journal already uses — nothing
 * is a decorative switch, and nothing new is stored:
 *
 *  · theme          → `AppPreferences.setThemeMode` (the same light/dark/system
 *                     the whole app reads, so the editor is painted the instant
 *                     it changes)
 *  · journal door   → `setJournalOpenToday` ("today's page" vs "last opened")
 *  · word goal      → `setJournalGoal` (0 = no goal)
 *  · goal reminder  → `setJournalGoalReminderEnabled` (arms the evening nudge)
 */
@Composable
fun JournalSettingsScreen(
    onBack: () -> Unit,
    onOpenCollection: () -> Unit
) {
    val context = LocalContext.current
    val accent = personalAccent()
    val accentInk = personalAccentInk()
    val ink = MaterialTheme.colorScheme.onBackground

    var themeMode by remember { mutableStateOf(AppPreferences.themeModeState) }
    var goal by remember { mutableIntStateOf(AppPreferences.getJournalGoal(context)) }
    var openToday by remember { mutableStateOf(AppPreferences.isJournalOpenToday(context)) }
    var reminder by remember {
        mutableStateOf(AppPreferences.isJournalGoalReminderEnabled(context))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── HEAD ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                CurioIcon(
                    CurioIcons.ArrowBack,
                    "Back",
                    tint = ink.copy(alpha = 0.75f),
                    size = 21.dp
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ink
                )
                Text(
                    "Curio Journal",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    color = accentInk.copy(alpha = 0.7f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── THE PAGE ────────────────────────────────────────────────
            SettingsGroup(title = "The page") {
                SettingsSegment(
                    label = "Theme",
                    detail = "Light, dark, or the phone's own choice.",
                    options = listOf(
                        Triple("Light", AppPreferences.THEME_LIGHT, CurioIcons.LightMode),
                        Triple("Dark", AppPreferences.THEME_DARK, CurioIcons.DarkMode),
                        Triple("System", AppPreferences.THEME_SYSTEM, CurioIcons.Settings)
                    ),
                    selected = themeMode,
                    accent = accent,
                    onSelect = { mode ->
                        themeMode = mode
                        AppPreferences.setThemeMode(context, mode)
                    }
                )
                Divider()
                SettingsSegment(
                    label = "Opening the journal",
                    detail = "Where the pen lands when you come back.",
                    options = listOf(
                        Triple("Today", true, CurioIcons.CalendarToday),
                        Triple("Last page", false, CurioIcons.Note)
                    ),
                    selected = openToday,
                    accent = accent,
                    onSelect = { today ->
                        openToday = today
                        AppPreferences.setJournalOpenToday(context, today)
                    }
                )
            }

            // ── THE HABIT ───────────────────────────────────────────────
            SettingsGroup(title = "The habit") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Daily word goal",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = journalInk()
                            )
                            Text(
                                if (goal == 0) {
                                    "No goal — the page is whatever you write."
                                } else {
                                    "$goal words a day."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = journalInk().copy(alpha = 0.55f)
                            )
                        }
                        Stepper(
                            value = if (goal == 0) "Off" else goal.toString(),
                            accent = accent,
                            canDecrease = goal > 0,
                            canIncrease = goal < 5_000,
                            onDecrease = {
                                val next = (goal - 250).coerceAtLeast(0)
                                goal = next
                                AppPreferences.setJournalGoal(context, next)
                                if (next == 0) {
                                    reminder = false
                                    AppPreferences.setJournalGoalReminderEnabled(context, false)
                                }
                            },
                            onIncrease = {
                                val next = (goal + 250).coerceAtMost(5_000)
                                goal = next
                                AppPreferences.setJournalGoal(context, next)
                            }
                        )
                    }
                    // The bar says where a goal of this size sits relative to a
                    // page — a goal is a target, and a number alone is abstract.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(journalInk().copy(alpha = 0.07f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((goal.toFloat() / 5_000f).coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(accent)
                        )
                    }
                }
                Divider()
                SettingsSwitch(
                    label = "Evening reminder",
                    detail = if (goal == 0) {
                        "Set a goal to turn the nudge on."
                    } else {
                        "A nudge at the end of a day you haven't written."
                    },
                    checked = reminder,
                    enabled = goal > 0,
                    accent = accent,
                    onChange = { on ->
                        reminder = on
                        AppPreferences.setJournalGoalReminderEnabled(context, on)
                    }
                )
            }

            // ── THE PAGES ───────────────────────────────────────────────
            SettingsGroup(title = "The pages") {
                SettingsDoor(
                    glyph = CurioIcons.MenuBook,
                    label = "All pages",
                    detail = "Search, filter by mood, sort by day.",
                    accent = accent,
                    onClick = onOpenCollection
                )
                Divider()
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "Kept on this device",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = journalInk()
                    )
                    Text(
                        "Pages, photos and voice notes are stored on the phone. " +
                            "Nothing is uploaded, and there is no account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = journalInk().copy(alpha = 0.55f)
                    )
                }
            }

            // ── FOOT ────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "Curio Journal",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = ink.copy(alpha = 0.45f)
                )
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.35f)
                )
            }
        }
    }
}

// ── BUILDING BLOCKS ─────────────────────────────────────────────────────────

/** One card of settings, with its own heading above it. */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            ),
            color = ink.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = journalPaper(),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

/** The hairline inside a card — never a full-width divider to the edge. */
@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

/**
 * A labelled choice, as capsules: the selected one is the accent FILL with the
 * ink that reads on it, the others are the page's raised paper. The glyph is
 * part of the label, so a row of three reads at a glance.
 */
@Composable
private fun <T> SettingsSegment(
    label: String,
    detail: String,
    options: List<Triple<String, T, String>>,
    selected: T,
    accent: Color,
    onSelect: (T) -> Unit
) {
    val ink = journalInk()
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = ink
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.55f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEach { (name, value, glyph) ->
                val isOn = value == selected
                Surface(
                    onClick = { onSelect(value) },
                    shape = RoundedCornerShape(50),
                    color = if (isOn) accent else journalPaperRaised(),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CurioIcon(
                            glyph,
                            null,
                            tint = if (isOn) personalOnAccent() else ink.copy(alpha = 0.55f),
                            size = 14.dp
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isOn) personalOnAccent() else ink.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/** A switch row that says what it does in one line. */
@Composable
private fun SettingsSwitch(
    label: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit
) {
    val ink = journalInk()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (enabled) ink else ink.copy(alpha = 0.45f)
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = if (enabled) 0.55f else 0.35f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = personalOnAccent(),
                checkedTrackColor = accent,
                uncheckedThumbColor = ink.copy(alpha = 0.4f),
                uncheckedTrackColor = ink.copy(alpha = 0.08f),
                uncheckedBorderColor = ink.copy(alpha = 0.12f)
            )
        )
    }
}

/** A door: glyph on an accent plate, a label, a line, a chevron. */
@Composable
private fun SettingsDoor(
    glyph: String,
    label: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit
) {
    val ink = journalInk()
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(22.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = lerp(journalPaper(), accent, 0.22f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    CurioIcon(glyph, null, tint = personalAccentInk(), size = 18.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ink
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = 0.55f)
                )
            }
            CurioIcon(
                CurioIcons.ChevronRight,
                null,
                tint = ink.copy(alpha = 0.35f),
                size = 18.dp
            )
        }
    }
}

/** The word goal's own − / + control, in the page's accent. */
@Composable
private fun Stepper(
    value: String,
    accent: Color,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    val ink = journalInk()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        StepButton(
            glyph = CurioIcons.Close,
            description = "Lower the goal",
            enabled = canDecrease,
            accent = accent,
            onClick = onDecrease
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = ink,
            modifier = Modifier
                .width(46.dp)
                .padding(horizontal = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        StepButton(
            glyph = CurioIcons.Add,
            description = "Raise the goal",
            enabled = canIncrease,
            accent = accent,
            onClick = onIncrease
        )
    }
}

@Composable
private fun StepButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val ink = journalInk()
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) lerp(journalPaper(), accent, 0.18f) else journalPaperRaised(),
        modifier = Modifier.size(32.dp)
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            CurioIcon(
                glyph,
                description,
                tint = if (enabled) personalAccentInk() else ink.copy(alpha = 0.25f),
                size = 15.dp
            )
        }
    }
}
