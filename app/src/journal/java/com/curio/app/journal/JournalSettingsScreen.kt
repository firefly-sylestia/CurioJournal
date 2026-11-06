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
 * Four groups, and every row is wired to the preference the journal already
 * uses; nothing here is a decorative switch and nothing new is stored:
 *
 *  · theme          → `AppPreferences.setThemeMode`
 *  · journal door   → `setJournalOpenToday` ("today's page" vs "last opened")
 *  · word goal      → `setJournalGoal` (0 = no goal)
 *  · goal reminder  → `setJournalGoalReminderEnabled` (arms the evening nudge)
 *
 * The page is a list, not a dashboard: a glyph on every row, the value on the
 * right where there is one, and one quiet line at the foot saying where the
 * writing is kept.
 */
@Composable
fun JournalSettingsScreen(
    onBack: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenCalendar: () -> Unit
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
                .padding(top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {

            // ── THE PAGE ────────────────────────────────────────────────
            SettingsGroup(title = "The page") {
                SegmentRow(
                    glyph = CurioIcons.LightMode,
                    label = "Theme",
                    detail = "Light, dark, or the phone's own choice.",
                    options = listOf(
                        SegmentOption("Light", AppPreferences.THEME_LIGHT, CurioIcons.LightMode),
                        SegmentOption("Dark", AppPreferences.THEME_DARK, CurioIcons.DarkMode),
                        SegmentOption("System", AppPreferences.THEME_SYSTEM, CurioIcons.Settings)
                    ),
                    selected = themeMode,
                    accent = accent,
                    onSelect = { mode ->
                        themeMode = mode
                        AppPreferences.setThemeMode(context, mode)
                    }
                )
                Divider()
                SegmentRow(
                    glyph = CurioIcons.Note,
                    label = "Opening the journal",
                    detail = "Where the pen lands when you come back.",
                    options = listOf(
                        SegmentOption("Today", true, CurioIcons.CalendarToday),
                        SegmentOption("Last page", false, CurioIcons.Note)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Daily word goal",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = journalInk()
                        )
                        Text(
                            when (goal) {
                                0 -> "No goal — the page is whatever you write."
                                else -> "A target, not a fence. Steps of 250."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = journalInk().copy(alpha = 0.55f)
                        )
                    }
                    GoalStepper(
                        goal = goal,
                        accent = accent,
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
                // A quiet bar that says where this goal sits on the scale a page
                // can reach — a number alone is abstract.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 14.dp)
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
                Divider()
                SwitchRow(
                    glyph = CurioIcons.CalendarToday,
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
                DoorRow(
                    glyph = CurioIcons.MenuBook,
                    label = "All pages",
                    detail = "Search, filter by mood, sort by day.",
                    accent = accent,
                    onClick = onOpenCollection
                )
                Divider()
                DoorRow(
                    glyph = CurioIcons.CalendarToday,
                    label = "Calendar",
                    detail = "The month as a grid, a dot on every day written.",
                    accent = accent,
                    onClick = onOpenCalendar
                )
                Divider()
                NoteRow(
                    label = "Kept on this device",
                    detail = "Pages, photos and voice notes are stored on the phone. " +
                        "Nothing is uploaded, and there is no account."
                )
            }

            // ── ABOUT ───────────────────────────────────────────────────
            SettingsGroup(title = "About") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = accent, modifier = Modifier.size(34.dp)) {
                        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                            CurioIcon(
                                CurioIcons.Note,
                                null,
                                tint = personalOnAccent(),
                                size = 17.dp
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Curio Journal",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = journalInk()
                        )
                        Text(
                            "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = journalInk().copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // ── FOOT ────────────────────────────────────────────────────
            Text(
                "The journal from Curio — one page a day, and the days before it.",
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.35f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// ── BUILDING BLOCKS ─────────────────────────────────────────────────────────

/** One group of settings, with its own quiet heading above the card. */
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
                letterSpacing = 1.2.sp
            ),
            color = ink.copy(alpha = 0.42f),
            modifier = Modifier.padding(start = 6.dp)
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

/** The hairline inside a card — never a full-bleed divider. */
@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    )
}

/** The glyph every row leads with, on its own quiet disc. */
@Composable
private fun RowGlyph(glyph: String, accent: Color) {
    Surface(
        shape = CircleShape,
        color = lerp(journalPaper(), accent, 0.18f),
        modifier = Modifier.size(34.dp)
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            CurioIcon(glyph, null, tint = personalAccentInk(), size = 17.dp)
        }
    }
}

private data class SegmentOption<T>(
    val label: String,
    val value: T,
    val glyph: String
)

/**
 * A labelled choice, as capsules: the selected one is the accent fill with the
 * ink that reads on it, the others are the page's raised paper.
 */
@Composable
private fun <T> SegmentRow(
    glyph: String,
    label: String,
    detail: String,
    options: List<SegmentOption<T>>,
    selected: T,
    accent: Color,
    onSelect: (T) -> Unit
) {
    val ink = journalInk()
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RowGlyph(glyph, accent)
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
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEach { option ->
                val isOn = option.value == selected
                Surface(
                    onClick = { onSelect(option.value) },
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
                            option.glyph,
                            null,
                            tint = if (isOn) personalOnAccent() else ink.copy(alpha = 0.55f),
                            size = 14.dp
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            option.label,
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
private fun SwitchRow(
    glyph: String,
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
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RowGlyph(glyph, accent)
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
        Spacer(Modifier.width(6.dp))
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

/** A door: glyph, label, line, chevron. */
@Composable
private fun DoorRow(
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
                .padding(start = 16.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RowGlyph(glyph, accent)
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
                tint = ink.copy(alpha = 0.32f),
                size = 18.dp
            )
        }
    }
}

/** A row that is only a fact: no door, no switch. */
@Composable
private fun NoteRow(label: String, detail: String) {
    val ink = journalInk()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = ink
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = ink.copy(alpha = 0.55f)
        )
    }
}

/** The word goal's own − / + control, with the value between them. */
@Composable
private fun GoalStepper(
    goal: Int,
    accent: Color,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    val ink = journalInk()
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(
            glyph = "−",
            enabled = goal > 0,
            accent = accent,
            ink = ink,
            onClick = onDecrease
        )
        Text(
            if (goal == 0) "Off" else goal.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (goal == 0) ink.copy(alpha = 0.45f) else ink,
            modifier = Modifier
                .width(58.dp)
                .padding(horizontal = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        StepperButton(
            glyph = "+",
            enabled = goal < 5_000,
            accent = accent,
            ink = ink,
            onClick = onIncrease
        )
    }
}

@Composable
private fun StepperButton(
    glyph: String,
    enabled: Boolean,
    accent: Color,
    ink: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) accent else ink.copy(alpha = 0.06f),
        modifier = Modifier.size(32.dp)
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Text(
                glyph,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (enabled) personalOnAccent() else ink.copy(alpha = 0.3f)
            )
        }
    }
}
