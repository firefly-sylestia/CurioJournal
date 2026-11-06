package com.curio.app.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curio.app.data.AppPreferences
import com.curio.app.data.PersonalNoteEntity
import com.curio.app.data.PersonalRepositoryHolder
import com.curio.app.data.wordCount
import com.curio.app.features.personal.PersonalShelfSnapshot
import com.curio.app.features.personal.journalPaperRaised
import com.curio.app.features.personal.personalAccent
import com.curio.app.features.personal.personalAccentInk
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
 * THE JOURNAL'S HOME — THE DAY, GREETED.
 *
 * The page answers three questions in the order a writer asks them: who is
 * writing ([greeting]), what day it is (the day block), and what has already been
 * written on it (the cards under it). Nothing above the writing is a card: the
 * greeting is type, the date is type, and the only surfaces are the pills that
 * carry a number and the pages themselves.
 *
 * The member's own layout, kept to: the year large on the right, the day large
 * on the left, the month under the day a step bigger than the dates below it —
 * and no "N of M days" line anywhere (that measured the month; a journal measures
 * the day). The old "before today" list is gone: the days before today live
 * behind the calendar button, and the collection behind the pages button.
 */
@Composable
fun JournalHomeScreen(
    /** Opens a day: a saved page's id, or null for a day with no page yet. */
    onOpenDay: (millis: Long, entryId: String?) -> Unit,
    onOpenCollection: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val accent = personalAccent()
    val accentInk = personalAccentInk()
    val ink = MaterialTheme.colorScheme.onBackground

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
    // A day can carry more than one page; today's are the ones this screen shows.
    val todayPages = remember(journals, today) {
        journals
            .filter { it.dateMillis == today }
            .sortedWith(compareBy({ it.createdAtMillis }, { it.updatedAtMillis }))
            .reversed()
    }
    val writtenDays = remember(journals) { journals.map { it.dateMillis }.toHashSet() }
    // Read on every composition, not remembered: the goal and the name are set on
    // other pages, and coming back has to show the new answer.
    val goal = AppPreferences.getJournalGoal(context)
    val name = AppPreferences.getJournalName(context)
    val wordsToday = remember(todayPages) {
        todayPages.sumOf { it.doc.wordCount() }
    }
    val streak = remember(journals, writtenDays, today) {
        // Today counts the moment it is written; a day that has not been written
        // yet must not break yesterday's run, so the probe starts at yesterday
        // when today is still blank.
        var probe = if (writtenDays.contains(today)) today else shiftDay(today, -1)
        var run = 0
        while (writtenDays.contains(probe)) {
            run++
            probe = shiftDay(probe, -1)
        }
        run
    }

    // The floating pen: the member's own choice of where a journal door lands
    // ("today's page" is the default, "the last one you opened" the other half).
    val doorId = if (AppPreferences.isJournalOpenToday(context)) {
        todayPages.firstOrNull()?.id
    } else {
        AppPreferences.getLastJournalId(context).takeIf { id ->
            journals.any { it.id == id }
        } ?: todayPages.firstOrNull()?.id
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            // The page stays a page on a tablet or a landscape phone: one column,
            // centred, instead of a diary stretched across the whole window.
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .widthIn(max = 680.dp)
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item("greeting") {
                GreetingHead(
                    name = name,
                    year = today.toLocalDate().year.toString(),
                    accentInk = accentInk,
                    ink = ink,
                    onOpenSettings = onOpenSettings
                )
            }

            item("day") {
                DayBlock(
                    today = today,
                    writtenDays = writtenDays,
                    locale = locale,
                    accent = accent,
                    accentInk = accentInk,
                    ink = ink,
                    goal = goal,
                    words = wordsToday,
                    streak = streak,
                    onOpenDay = { millis ->
                        onOpenDay(
                            millis,
                            journals.firstOrNull { it.dateMillis == millis }?.id
                        )
                    }
                )
            }

            item("written") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Today",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.4.sp
                            ),
                            color = accentInk.copy(alpha = 0.75f)
                        )
                        Spacer(Modifier.weight(1f))
                        if (todayPages.size > 1) {
                            Text(
                                "${todayPages.size} pages",
                                style = MaterialTheme.typography.labelSmall,
                                color = ink.copy(alpha = 0.4f)
                            )
                        }
                    }
                    TodayWriting(
                        pages = todayPages,
                        onOpen = { page ->
                            onOpenDay(today, page.id)
                        },
                        onStart = { onOpenDay(today, null) }
                    )
                }
            }
        }

        // ── THE FLOATING DOORS ──────────────────────────────────────────
        //
        // The "All pages" row that used to end Home is gone (the member asked for
        // it gone): the collection and the calendar are two quiet discs under the
        // pen, so the page ends on the writing rather than on a list of doors.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 22.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingDoor(
                glyph = CurioIcons.MenuBook,
                description = "All pages",
                accentInk = accentInk,
                onClick = onOpenCollection
            )
            FloatingDoor(
                glyph = CurioIcons.CalendarToday,
                description = "Calendar",
                accentInk = accentInk,
                onClick = onOpenCalendar
            )
            // The pen: the big one, and the page's one filled colour.
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
                modifier = Modifier.size(58.dp)
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
}

/** One quiet door under the pen — the collection, the calendar. */
@Composable
private fun FloatingDoor(
    glyph: String,
    description: String,
    accentInk: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = journalPaperRaised(),
        shadowElevation = 4.dp,
        modifier = Modifier.size(46.dp)
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            CurioIcon(glyph, description, tint = accentInk.copy(alpha = 0.85f), size = 21.dp)
        }
    }
}

// ── THE GREETING ────────────────────────────────────────────────────────────

/**
 * The member's name, the day's question, and the year — the only three words
 * above the date.
 *
 * The year is set large on the RIGHT because it is the long frame the day sits
 * in; the greeting is the display face at the size a name is said at, and the
 * question under it is the page asking what happened (the member's own copy,
 * *"hello name, and below that anything exciting today?"*).
 */
@Composable
private fun GreetingHead(
    name: String,
    year: String,
    accentInk: Color,
    ink: Color,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 14.dp, top = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (name.isBlank()) "Hello" else "Hello, $name",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 36.sp,
                    lineHeight = 40.sp
                ),
                color = ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Anything exciting today?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    lineHeight = 23.sp
                ),
                color = accentInk.copy(alpha = 0.82f)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                year,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 30.sp,
                    lineHeight = 34.sp
                ),
                color = ink.copy(alpha = 0.22f)
            )
            IconButton(onClick = onOpenSettings) {
                CurioIcon(
                    CurioIcons.Settings,
                    "Settings",
                    tint = ink.copy(alpha = 0.5f),
                    size = 20.dp
                )
            }
        }
    }
}

// ── THE DAY ─────────────────────────────────────────────────────────────────

/**
 * The day, as the page's subject: the numeral large, the month under it a step
 * larger than the dates, this week beneath the month, and the habit as two small
 * pills under the date — never a card, never a "N of M days" line.
 */
@Composable
private fun DayBlock(
    today: Long,
    writtenDays: Set<Long>,
    locale: Locale,
    accent: Color,
    accentInk: Color,
    ink: Color,
    goal: Int,
    words: Int,
    streak: Int,
    onOpenDay: (Long) -> Unit
) {
    val date = today.toLocalDate()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 76.sp,
                    lineHeight = 78.sp
                ),
                color = ink
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = accentInk.copy(alpha = 0.85f)
                )
                Text(
                    date.format(DateTimeFormatter.ofPattern("MMMM", locale)),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp
                    ),
                    color = ink
                )
            }
        }

        WeekDates(
            today = today,
            writtenDays = writtenDays,
            locale = locale,
            accent = accent,
            accentInk = accentInk,
            ink = ink,
            onOpenDay = onOpenDay
        )

        if (goal > 0 || streak > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (goal > 0) {
                    CountPill(
                        text = "$words of $goal words",
                        accent = accent,
                        accentInk = accentInk
                    )
                }
                if (streak > 0) {
                    CountPill(
                        text = if (streak == 1) "1-day streak" else "$streak-day streak",
                        accent = accent,
                        accentInk = accentInk,
                        glyph = CurioIcons.LocalFire
                    )
                }
            }
        }
    }
}

/**
 * THIS WEEK, under the month — seven dates, today filled.
 *
 * A week rather than the four-week strip it used to be: the member asked for the
 * dates below the month and *"just show today"*, and the days further back are
 * the calendar's business (one tap away, on a floating door).
 */
@Composable
private fun WeekDates(
    today: Long,
    writtenDays: Set<Long>,
    locale: Locale,
    accent: Color,
    accentInk: Color,
    ink: Color,
    onOpenDay: (Long) -> Unit
) {
    val todayDate = remember(today) { today.toLocalDate() }
    val monday = remember(todayDate) { shiftDay(today, -(todayDate.dayOfWeek.value - 1).toLong()) }
    val days = remember(monday) { (0L..6L).map { shiftDay(monday, it) } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { millis ->
            val isToday = millis == today
            val written = writtenDays.contains(millis)
            Surface(
                onClick = { onOpenDay(millis) },
                shape = RoundedCornerShape(14.dp),
                color = if (isToday) accent else Color.Transparent,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        millis.toLocalDate()
                            .format(DateTimeFormatter.ofPattern("EEEEE", locale)),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = when {
                            isToday -> personalOnAccent().copy(alpha = 0.72f)
                            else -> ink.copy(alpha = 0.38f)
                        }
                    )
                    Text(
                        millis.toLocalDate().dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isToday) personalOnAccent() else ink.copy(alpha = 0.8f)
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    written && isToday -> personalOnAccent().copy(alpha = 0.9f)
                                    written -> accentInk.copy(alpha = 0.5f)
                                    else -> Color.Transparent
                                }
                            )
                    )
                }
            }
        }
    }
}

/** A number about the habit, in a small pill — "0 of 500 words", "4-day streak". */
@Composable
private fun CountPill(
    text: String,
    accent: Color,
    accentInk: Color,
    glyph: String? = null
) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (glyph != null) {
                CurioIcon(glyph, null, tint = accentInk.copy(alpha = 0.8f), size = 13.dp)
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = accentInk.copy(alpha = 0.9f)
            )
        }
    }
}

// ── WHAT HAS BEEN WRITTEN ───────────────────────────────────────────────────

/**
 * Today's pages: one is a vertical card, more than one is a shelf of horizontal
 * rectangles (the member's own rule), and none is a piece of the page waiting to
 * be written on.
 */
@Composable
private fun TodayWriting(
    pages: List<PersonalNoteEntity>,
    onOpen: (PersonalNoteEntity) -> Unit,
    onStart: () -> Unit
) {
    when {
        pages.isEmpty() -> {
            JournalBlankDay(
                prompt = "Write something about today…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                onClick = onStart
            )
        }

        pages.size == 1 -> {
            JournalPageCard(
                page = pages.first(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                onClick = { onOpen(pages.first()) }
            )
        }

        else -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = pages, key = { "today-${it.id}" }) { page ->
                    JournalPageCard(
                        page = page,
                        compact = true,
                        modifier = Modifier.width(238.dp),
                        onClick = { onOpen(page) }
                    )
                }
            }
        }
    }
}
