package com.curio.app.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curio.app.data.AppPreferences
import com.curio.app.data.PersonalNoteEntity
import com.curio.app.data.PersonalRepositoryHolder
import com.curio.app.data.wordCount
import com.curio.app.features.personal.JournalCapsule
import com.curio.app.features.personal.PersonalShelfSnapshot
import com.curio.app.features.personal.journalInk
import com.curio.app.features.personal.journalPaper
import com.curio.app.features.personal.journalPaperRaised
import com.curio.app.features.personal.journalRule
import com.curio.app.features.personal.personalAccent
import com.curio.app.features.personal.personalAccentInk
import com.curio.app.features.personal.personalMoodGlyph
import com.curio.app.features.personal.personalOnAccent
import com.curio.app.features.personal.shiftDay
import com.curio.app.features.personal.startOfToday
import com.curio.app.features.personal.toLocalDate
import com.curio.app.ui.theme.CurioIcon
import com.curio.app.ui.theme.CurioIcons
import com.curio.app.ui.theme.FrauncesFontFamily
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * THE JOURNAL'S HOME — A DAY, NOT A FEED.
 *
 * The discovery app's Home answers "what shall I explore"; this one answers
 * "what day is it, and what did I write on it". The whole page is built around
 * today: the date is the largest thing on screen, the week sits under it as
 * seven doors, and today's page is a sheet of paper laid on the desk. Everything
 * else — the goal, the streak, the days before today — is a way back into
 * writing, never a way into another feature.
 *
 * The writing itself is NOT reimplemented here: a tap opens the existing
 * `JournalEditorScreen`/`JournalListScreen`, untouched, with the existing store
 * behind them.
 */
@Composable
fun JournalHomeScreen(
    /** Opens a day: a saved page's id, or null for a day with no page yet. */
    onOpenDay: (millis: Long, entryId: String?) -> Unit,
    onOpenCollection: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val accent = personalAccent()
    val accentInk = personalAccentInk()
    val ink = MaterialTheme.colorScheme.onBackground
    val paper = journalPaper()
    val rule = journalRule()

    // Seeded from the last look, so returning to Home never flashes an empty
    // page before Room answers (the same snapshot the app's own rows read).
    val pages by produceState(initialValue = PersonalShelfSnapshot.journals) {
        runCatching {
            PersonalRepositoryHolder.repo.observeJournals().collect {
                PersonalShelfSnapshot.journals = it
                value = it
            }
        }
    }
    // The journal's list is DAYS only — a to-do list and a note about a topic
    // live in Notes, exactly as they do in the collection screen.
    val journals = remember(pages) { pages.filter { !it.isTodo && !it.hasTopic } }
    val today = remember { startOfToday() }
    val todayPage = journals.firstOrNull { it.dateMillis == today }
    val writtenDays = remember(journals) { journals.map { it.dateMillis }.toHashSet() }
    // Read on every composition, not remembered: the goal and the door are set
    // on the Settings page, and coming back from it has to show the new answer.
    // Both are in-memory SharedPreferences reads, so this is free.
    val goal = AppPreferences.getJournalGoal(context)
    val wordsToday = remember(todayPage) { todayPage?.doc?.wordCount() ?: 0 }
    val streak = remember(journals, writtenDays, today) {
        // Today counts the moment it is written; a day that has not been
        // written yet must not break yesterday's run, so the probe starts at
        // yesterday when today is still blank.
        var probe = if (writtenDays.contains(today)) today else shiftDay(today, -1)
        var run = 0
        while (writtenDays.contains(probe)) {
            run++
            probe = shiftDay(probe, -1)
        }
        run
    }
    val recent = remember(journals, today) {
        journals
            .filter { it.dateMillis != today }
            .sortedWith(compareBy({ it.dateMillis }, { it.createdAtMillis }))
            .reversed()
            .take(6)
    }

    // The floating pen: the member's own choice of where a journal door lands
    // ("today's page" is the default, "the last one you opened" the other half).
    val doorId = if (AppPreferences.isJournalOpenToday(context)) {
        todayPage?.id
    } else {
        AppPreferences.getLastJournalId(context).takeIf { id ->
            journals.any { it.id == id }
        } ?: todayPage?.id
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("head") {
                JournalHead(
                    today = today,
                    accentInk = accentInk,
                    ink = ink,
                    onOpenSettings = onOpenSettings
                )
            }

            item("week") {
                WeekStrip(
                    today = today,
                    writtenDays = writtenDays,
                    accent = accent,
                    accentInk = accentInk,
                    onOpen = { millis ->
                        onOpenDay(millis, journals.firstOrNull { it.dateMillis == millis }?.id)
                    }
                )
            }

            item("today-page") {
                val page = todayPage
                if (page == null) {
                    EmptyPageSheet(
                        dateLabel = today.fullLabel(),
                        accent = accent,
                        accentInk = accentInk,
                        paper = paper,
                        rule = rule,
                        onClick = { onOpenDay(today, null) }
                    )
                } else {
                    TodaySheet(
                        page = page,
                        accentInk = accentInk,
                        paper = paper,
                        rule = rule,
                        onClick = { onOpenDay(today, page.id) }
                    )
                }
            }

            if (goal > 0) {
                item("goal") {
                    GoalRow(
                        words = wordsToday,
                        goal = goal,
                        streak = streak,
                        accent = accent,
                        accentInk = accentInk
                    )
                }
            } else {
                item("streak") {
                    StreakRow(streak = streak, accent = accent, accentInk = accentInk)
                }
            }

            if (recent.isNotEmpty()) {
                item("recent-head") {
                    SectionHead(
                        title = "Before today",
                        trailing = if (journals.size > 1) "All pages" else null,
                        ink = ink,
                        accentInk = accentInk,
                        onTrailing = onOpenCollection
                    )
                }
                items(items = recent, key = { "recent-${it.id}" }) { page ->
                    RecentDayRow(
                        page = page,
                        accentInk = accentInk,
                        onClick = { onOpenDay(page.dateMillis, page.id) }
                    )
                }
            } else {
                item("first-page") {
                    FirstPageNote(ink = ink, accentInk = accentInk)
                }
            }

            item("collection-door") {
                CollectionDoor(
                    count = journals.size,
                    accent = accent,
                    accentInk = accentInk,
                    onClick = onOpenCollection
                )
            }
        }

        // The pen: fixed, above the scroll, out of the writing's way.
        Surface(
            onClick = {
                if (doorId != null) {
                    val page = journals.firstOrNull { it.id == doorId }
                    onOpenDay(page?.dateMillis ?: today, doorId)
                } else {
                    onOpenDay(today, null)
                }
            },
            shape = CircleShape,
            color = accent,
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 22.dp, bottom = 26.dp)
                .size(58.dp)
        ) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                CurioIcon(
                    CurioIcons.Note,
                    "Write today's page",
                    tint = personalOnAccent(),
                    size = 25.dp
                )
            }
        }
    }
}

// ── THE HEAD ────────────────────────────────────────────────────────────────

/**
 * The wordmark and the day. The date is the page's subject here, so it is set
 * in the display serif at a size nothing else on Home uses, with the weekday
 * and the month beside it in the accent's ink.
 */
@Composable
private fun JournalHead(
    today: Long,
    accentInk: Color,
    ink: Color,
    onOpenSettings: () -> Unit
) {
    val date = today.toLocalDate()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 10.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CURIO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.6.sp
                ),
                color = accentInk
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                CurioIcon(
                    CurioIcons.Settings,
                    "Settings",
                    tint = ink.copy(alpha = 0.62f),
                    size = 21.dp
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                greeting(),
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = 0.55f)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 68.sp,
                        lineHeight = 72.sp
                    ),
                    color = ink
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.padding(bottom = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = accentInk
                    )
                    Text(
                        date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                        style = MaterialTheme.typography.labelMedium,
                        color = ink.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/** A time-of-day greeting — the one warm line on a page of dates. */
private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 5 -> "Still awake"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 22 -> "Good evening"
        else -> "Good night"
    }
}

// ── THE WEEK ────────────────────────────────────────────────────────────────

/**
 * Seven days, today at the right edge. A day with a page carries a filled dot
 * in its own colour; the day itself wears the accent. It is a LazyRow with
 * fixed-width cells so the strip never reflows as the week changes.
 */
@Composable
private fun WeekStrip(
    today: Long,
    writtenDays: Set<Long>,
    accent: Color,
    accentInk: Color,
    onOpen: (Long) -> Unit
) {
    val days = remember(today) { (6 downTo 0).map { shiftDay(today, -it.toLong()) } }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = days, key = { "day-$it" }) { millis ->
            val isToday = millis == today
            val written = writtenDays.contains(millis)
            Surface(
                onClick = { onOpen(millis) },
                shape = RoundedCornerShape(18.dp),
                color = if (isToday) accent else journalPaper(),
                shadowElevation = if (isToday) 0.dp else 1.dp,
                modifier = Modifier
                    .width(52.dp)
                    .height(74.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        millis.toLocalDate()
                            .format(DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isToday) {
                            personalOnAccent().copy(alpha = 0.72f)
                        } else {
                            accentInk.copy(alpha = 0.62f)
                        }
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        millis.toLocalDate().dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isToday) personalOnAccent() else accentInk
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    written && isToday -> personalOnAccent().copy(alpha = 0.85f)
                                    written -> accentInk.copy(alpha = 0.55f)
                                    else -> Color.Transparent
                                }
                            )
                    )
                }
            }
        }
    }
}

// ── TODAY'S PAGE ────────────────────────────────────────────────────────────

/**
 * A day that exists, as the sheet of paper it is: the mood it was written
 * under, its title in the display serif, the opening of the writing, and how
 * much of it there is. Ruled, because a journal page is ruled.
 */
@Composable
private fun TodaySheet(
    page: PersonalNoteEntity,
    accentInk: Color,
    paper: Color,
    rule: Color,
    onClick: () -> Unit
) {
    val ink = journalInk()
    val own = if (page.hasOwnAccent) Color(page.accentArgb) else null
    val spine = own ?: accentInk
    val mood = page.moodEnum
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = paper,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val barWidth = 3.5.dp.toPx()
                    drawRoundRect(
                        color = spine,
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                    )
                    // The rules: one hairline every 30dp, never over the title.
                    val step = 30.dp.toPx()
                    var y = 96.dp.toPx()
                    while (y < size.height - 8.dp.toPx()) {
                        drawLine(
                            color = rule.copy(alpha = 0.55f),
                            start = Offset(18.dp.toPx(), y),
                            end = Offset(size.width - 14.dp.toPx(), y),
                            strokeWidth = 1.dp.toPx()
                        )
                        y += step
                    }
                }
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mood != null) {
                    Surface(
                        shape = JournalCapsule.Shape,
                        color = journalPaperRaised(),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            CurioIcon(
                                personalMoodGlyph(mood),
                                null,
                                tint = accentInk,
                                size = 14.dp
                            )
                            Text(
                                mood.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = ink.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    page.dateMillis.prettyTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.4f)
                )
            }
            if (page.title.isNotBlank()) {
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                page.preview.ifBlank { "This page is still blank." },
                style = MaterialTheme.typography.bodyMedium,
                color = ink.copy(alpha = 0.68f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    words(page),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.42f)
                )
                Spacer(Modifier.weight(1f))
                CurioIcon(
                    CurioIcons.ChevronRight,
                    null,
                    tint = accentInk.copy(alpha = 0.55f),
                    size = 17.dp
                )
            }
        }
    }
}

/**
 * Today, before a word of it exists: the same sheet, ruled and empty, with the
 * one action on it. The page is the button — there is no second control to
 * explain what to do with a blank page.
 */
@Composable
private fun EmptyPageSheet(
    dateLabel: String,
    accent: Color,
    accentInk: Color,
    paper: Color,
    rule: Color,
    onClick: () -> Unit
) {
    val ink = journalInk()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = paper,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val barWidth = 3.5.dp.toPx()
                    drawRoundRect(
                        color = accent,
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                    )
                    val step = 30.dp.toPx()
                    var y = 78.dp.toPx()
                    while (y < size.height - 8.dp.toPx()) {
                        drawLine(
                            color = rule.copy(alpha = 0.55f),
                            start = Offset(18.dp.toPx(), y),
                            end = Offset(size.width - 14.dp.toPx(), y),
                            strokeWidth = 1.dp.toPx()
                        )
                        y += step
                    }
                }
                .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "Today's page",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = ink
            )
            Text(
                "Nothing written for $dateLabel yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = ink.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accent, modifier = Modifier.size(30.dp)) {
                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        CurioIcon(
                            CurioIcons.Note,
                            null,
                            tint = personalOnAccent(),
                            size = 16.dp
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    "Start writing",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = accentInk
                )
            }
        }
    }
}

// ── GOAL, STREAK, RECENT ────────────────────────────────────────────────────

/**
 * The daily goal, with the streak beside it — two facts about the habit, one
 * card, no encouragement copy. The bar is the only graphic.
 */
@Composable
private fun GoalRow(
    words: Int,
    goal: Int,
    streak: Int,
    accent: Color,
    accentInk: Color
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val progress = (words.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = journalPaperRaised(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$words of $goal words today",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ink
                )
                Spacer(Modifier.weight(1f))
                if (streak > 0) {
                    Text(
                        "$streak-day streak",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentInk.copy(alpha = 0.8f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ink.copy(alpha = 0.07f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                )
            }
        }
    }
}

/** No goal set: the streak alone, said once. */
@Composable
private fun StreakRow(streak: Int, accent: Color, accentInk: Color) {
    val ink = MaterialTheme.colorScheme.onBackground
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = lerp(journalPaperRaised(), accent, 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CurioIcon(
                CurioIcons.CalendarToday,
                null,
                tint = accentInk.copy(alpha = 0.8f),
                size = 17.dp
            )
            Text(
                when (streak) {
                    0 -> "A page a day, whenever you like"
                    1 -> "One day in. Keep it going"
                    else -> "$streak days in a row"
                },
                style = MaterialTheme.typography.labelLarge,
                color = ink.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun SectionHead(
    title: String,
    trailing: String?,
    ink: Color,
    accentInk: Color,
    onTrailing: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = ink
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Surface(
                onClick = onTrailing,
                shape = RoundedCornerShape(50),
                color = Color.Transparent
            ) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = accentInk,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * One day before today: the date block on the left, the writing beside it, its
 * own colour as the spine. Tapping opens the page on that day.
 */
@Composable
private fun RecentDayRow(
    page: PersonalNoteEntity,
    accentInk: Color,
    onClick: () -> Unit
) {
    val ink = journalInk()
    val own = if (page.hasOwnAccent) Color(page.accentArgb) else null
    val mood = page.moodEnum
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = journalPaper(),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val barWidth = 3.dp.toPx()
                    drawRoundRect(
                        color = own ?: accentInk.copy(alpha = 0.28f),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                    )
                }
                .padding(start = 16.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    page.dateMillis.toLocalDate().dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ink
                )
                Text(
                    page.dateMillis.toLocalDate()
                        .format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.42f)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    page.title.ifBlank { page.preview.ifBlank { "Untitled day" } },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    page.preview.ifBlank { words(page) },
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (mood != null) {
                Spacer(Modifier.width(8.dp))
                CurioIcon(
                    personalMoodGlyph(mood),
                    mood.label,
                    tint = ink.copy(alpha = 0.38f),
                    size = 16.dp
                )
            }
        }
    }
}

/** A library of one: the only page is today's, so say what comes next. */
@Composable
private fun FirstPageNote(ink: Color, accentInk: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            "Every page you write is kept here, newest day first.",
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = 0.55f)
        )
        Text(
            "Your words never leave this device.",
            style = MaterialTheme.typography.labelSmall,
            color = accentInk.copy(alpha = 0.65f)
        )
    }
}

/** The way into the full collection, and the count that says why it exists. */
@Composable
private fun CollectionDoor(
    count: Int,
    accent: Color,
    accentInk: Color,
    onClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = lerp(journalPaper(), accent, 0.10f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = accent, modifier = Modifier.size(34.dp)) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    CurioIcon(
                        CurioIcons.MenuBook,
                        null,
                        tint = personalOnAccent(),
                        size = 17.dp
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "All pages",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ink
                )
                Text(
                    when (count) {
                        0 -> "Your journal starts today"
                        1 -> "1 day written"
                        else -> "$count days written"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.5f)
                )
            }
            CurioIcon(
                CurioIcons.ChevronRight,
                null,
                tint = accentInk.copy(alpha = 0.55f),
                size = 18.dp
            )
        }
    }
}

// ── SMALL SHARED BITS ───────────────────────────────────────────────────────

/** "12 words" — a page's length, said plainly. */
private fun words(page: PersonalNoteEntity): String {
    val count = page.doc.wordCount()
    return when (count) {
        0 -> "Empty"
        1 -> "1 word"
        else -> "$count words"
    }
}

/** A day, spelled out for a sentence: "Wednesday, 5 November". */
private fun Long.fullLabel(): String =
    toLocalDate().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))

/** The time a page was last written, as the page's own header says it. */
private fun Long.prettyTime(): String =
    java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(this))
