package com.curio.app.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curio.app.data.AppPreferences
import com.curio.app.data.PersonalNoteEntity
import com.curio.app.data.PersonalRepositoryHolder
import com.curio.app.data.wordCount
import com.curio.app.features.personal.PersonalShelfSnapshot
import com.curio.app.features.personal.shiftDay
import com.curio.app.features.personal.startOfToday
import com.curio.app.features.personal.toLocalDate
import com.curio.app.ui.theme.CurioIcon
import com.curio.app.ui.theme.CurioIcons
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * THE JOURNAL'S HOME — A SHEET OF PAPER, OPENED TO THE DAY (v535).
 *
 * This screen used to be the app's Home language with the journal's colours on
 * it: a feed of pills and cards. The member asked for the opposite — *"another
 * home screen redesign with paper and real journal feel … a whole redesign"*, and
 * for the journal to stand on its own theme. So the page is now built the way a
 * paper journal is:
 *
 *  · a MASTHEAD — the book's name in small caps, a double rule, the day set in
 *    the display serif with the year in brass beside it, and the greeting as a
 *    note under it;
 *  · the WEEK as INDEX TABS hanging from the rule, today's tab ox blood;
 *  · the day's writing as LEAVES laid on the sheet — the same pages, on the
 *    journal's own paper;
 *  · and the doors as a DESK BAR along the foot of the page: labelled drawers
 *    (Pages, Calendar) beside a wide ink nib that starts the page — replacing the
 *    three floating discs, which said nothing until they were pressed.
 *
 * The data is exactly what the screen read before (the journal store, the day's
 * pages, the goal and streak), so nothing was lost to the redesign.
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

    // Seeded from the last look, so returning to Home never flashes an empty page
    // before Room answers (the same snapshot the rest of the journal reads).
    val pages by produceState(initialValue = PersonalShelfSnapshot.journals) {
        runCatching {
            PersonalRepositoryHolder.repo.observeJournals().collect {
                PersonalShelfSnapshot.journals = it
                value = it
            }
        }
    }
    // The journal's list is DAYS only — a to-do list and a topic note live in
    // Notes, exactly as they do in the collection screen.
    val journals = remember(pages) { pages.filter { !it.isTodo && !it.hasTopic } }
    val today = remember { startOfToday() }
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
    val wordsToday = remember(todayPages) { todayPages.sumOf { it.doc.wordCount() } }
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

    // The nib's own door: today's page, or the last page opened (the member's
    // choice in Settings), falling back to today when that page is gone.
    val doorId = if (AppPreferences.isJournalOpenToday(context)) {
        todayPages.firstOrNull()?.id
    } else {
        AppPreferences.getLastJournalId(context).takeIf { id ->
            journals.any { it.id == id }
        } ?: todayPages.firstOrNull()?.id
    }
    val writeToday: () -> Unit = {
        if (doorId != null) {
            val page = journals.firstOrNull { it.id == doorId }
            onOpenDay(page?.dateMillis ?: today, doorId)
        } else {
            onOpenDay(today, null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JournalLook.paper())
    ) {
        LazyColumn(
            // The page stays a page on a tablet or a landscape phone: one column,
            // centred, instead of a diary stretched across the whole window.
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .widthIn(max = 680.dp)
                .statusBarsPadding(),
            // Clears the desk bar (2 + 12 + 50 + 12) plus the navigation inset
            // it carries, with a little air under the last leaf.
            contentPadding = PaddingValues(bottom = 136.dp)
        ) {
            item("masthead") {
                JournalMasthead(
                    name = name,
                    today = today,
                    locale = locale,
                    goal = goal,
                    words = wordsToday,
                    streak = streak,
                    onOpenSettings = onOpenSettings
                )
            }
            item("week") {
                JournalWeekTabs(
                    today = today,
                    writtenDays = writtenDays,
                    locale = locale,
                    onOpenDay = { millis ->
                        onOpenDay(
                            millis,
                            journals.firstOrNull { it.dateMillis == millis }?.id
                        )
                    }
                )
            }
            item("today") {
                JournalTodaySection(
                    today = today,
                    pages = todayPages,
                    onOpenDay = onOpenDay
                )
            }
        }

        JournalDeskBar(
            alreadyWritten = todayPages.isNotEmpty(),
            onOpenCollection = onOpenCollection,
            onOpenCalendar = onOpenCalendar,
            onWrite = writeToday,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ── THE MASTHEAD ────────────────────────────────────────────────────────────

/**
 * The book's name, its double rule, the day, and the greeting — the page's own
 * header, in the order a journal leads with them.
 *
 * The day is one formatted string plus the year in brass, so the two can never
 * drift apart on a baseline (the member's earlier note about the date and year
 * sharing a row). The greeting is a note *under* the date, not a title above it:
 * the day is what the page is about.
 */
@Composable
private fun JournalMasthead(
    name: String,
    today: Long,
    locale: Locale,
    goal: Int,
    words: Int,
    streak: Int,
    onOpenSettings: () -> Unit
) {
    val ink = JournalLook.ink()
    val accentInk = JournalLook.accentInk()
    val brass = JournalLook.brass()
    val date = remember(today) { today.toLocalDate() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 18.dp, top = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JournalOverline("CURIO JOURNAL", accentInk.copy(alpha = 0.82f))
            Spacer(Modifier.weight(1f))
            // The queue's one control: the journal's own settings, above the year
            // (the member's own placement).
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                CurioIcon(
                    CurioIcons.Settings,
                    "Journal settings",
                    tint = ink.copy(alpha = 0.42f),
                    size = 18.dp
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        JournalDoubleRule()
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                date.format(DateTimeFormatter.ofPattern("d MMMM", locale)),
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = JournalLook.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 46.sp,
                    lineHeight = 50.sp,
                    letterSpacing = (-0.6).sp
                ),
                color = ink
            )
            Spacer(Modifier.width(10.dp))
            Text(
                date.year.toString(),
                modifier = Modifier.padding(bottom = 9.dp),
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = JournalLook.Display,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    letterSpacing = 0.6.sp
                ),
                color = brass.copy(alpha = 0.9f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (name.isBlank()) "Hello." else "Hello, $name.",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = JournalLook.Display,
                fontWeight = FontWeight.Medium,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                fontStyle = FontStyle.Italic
            ),
            color = ink.copy(alpha = 0.74f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Anything exciting today?",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = JournalLook.Display,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 21.sp
            ),
            color = accentInk.copy(alpha = 0.82f)
        )
        // The margin's stamped line: the goal and the streak, said quietly, in
        // brass — never a badge ahead of the words.
        val habit = buildList {
            if (goal > 0) add("$words OF $goal WORDS")
            if (streak > 0) add(if (streak == 1) "1-DAY STREAK" else "$streak-DAY STREAK")
        }.joinToString("   ·   ")
        if (habit.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                habit,
                style = JournalLook.stampStyle(),
                color = brass.copy(alpha = 0.72f)
            )
        }
    }
}

// ── THE WEEK, AS INDEX TABS ─────────────────────────────────────────────────

/**
 * This week as seven index tabs hanging from the page's rule, today's tab ox
 * blood and a written day carrying an ink dot.
 *
 * A tab, not a chip: a journal's week is marked in its margin, so the numerals
 * hang off a single rule and the days read as one strip rather than seven
 * separate buttons.
 */
@Composable
private fun JournalWeekTabs(
    today: Long,
    writtenDays: Set<Long>,
    locale: Locale,
    onOpenDay: (Long) -> Unit
) {
    val ink = JournalLook.ink()
    val accent = JournalLook.accent()
    val onAccent = JournalLook.onAccent()
    val todayDate = remember(today) { today.toLocalDate() }
    val monday = remember(todayDate) {
        shiftDay(today, -(todayDate.dayOfWeek.value - 1).toLong())
    }
    val days = remember(monday) { (0L..6L).map { shiftDay(monday, it) } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        // The rule the tabs hang from — the week's own line in the margin.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .height(1.dp)
                .background(JournalLook.rule())
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            days.forEach { millis ->
                val isToday = millis == today
                val written = writtenDays.contains(millis)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 10.dp,
                                topEnd = 10.dp,
                                bottomStart = 3.dp,
                                bottomEnd = 3.dp
                            )
                        )
                        .background(if (isToday) accent else Color.Transparent)
                        .clickable { onOpenDay(millis) }
                        .padding(top = 9.dp, bottom = if (isToday) 13.dp else 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        millis.toLocalDate()
                            .format(DateTimeFormatter.ofPattern("EEEEE", locale)),
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = JournalLook.Display,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            letterSpacing = 1.2.sp
                        ),
                        color = if (isToday) onAccent.copy(alpha = 0.68f)
                        else ink.copy(alpha = 0.34f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        millis.toLocalDate().dayOfMonth.toString(),
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = JournalLook.Display,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        ),
                        color = if (isToday) onAccent else ink.copy(alpha = 0.78f)
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    written && isToday -> onAccent.copy(alpha = 0.85f)
                                    written -> JournalLook.accent().copy(alpha = 0.55f)
                                    else -> Color.Transparent
                                }
                            )
                    )
                }
            }
        }
    }
}

// ── THE DAY'S WRITING ───────────────────────────────────────────────────────

/**
 * Today's pages, laid as leaves on the sheet.
 *
 * One page is a full leaf; more than one is a shelf of short leaves (the
 * member's own rule). A day with nothing on it gets NO card — the page is simply
 * still blank, which is what a real journal looks like before it is written on.
 */
@Composable
private fun JournalTodaySection(
    today: Long,
    pages: List<PersonalNoteEntity>,
    onOpenDay: (millis: Long, entryId: String?) -> Unit
) {
    val ink = JournalLook.ink()
    val accentInk = JournalLook.accentInk()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JournalOverline("TODAY'S PAGE", ink.copy(alpha = 0.40f))
            Spacer(Modifier.weight(1f))
            Text(
                if (pages.isEmpty()) "Write" else "New page",
                style = JournalLook.overlineStyle(),
                color = accentInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onOpenDay(today, null) }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            pages.isEmpty() -> {
                // Nothing written: the page's first rule, and the truth.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .height(1.dp)
                        .background(JournalLook.rule().copy(alpha = 0.6f))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "The page is still blank.",
                    modifier = Modifier.padding(horizontal = 22.dp),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = JournalLook.Display,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = ink.copy(alpha = 0.34f)
                )
            }

            pages.size == 1 -> {
                JournalPageCard(
                    page = pages.first(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                    onClick = { onOpenDay(today, pages.first().id) }
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
                            modifier = Modifier.width(240.dp),
                            onClick = { onOpenDay(today, page.id) }
                        )
                    }
                }
            }
        }
    }
}

// ── THE DESK BAR (the journal's toolbar) ────────────────────────────────────

/**
 * THE DESK BAR — the journal's new toolbar (v535).
 *
 * It replaces three floating discs stacked over the page. The member asked for a
 * whole redesign, not a reskin, so the structure changed: the foot of the sheet
 * is now a DESK — a paper strip with a brass edge, two labelled drawers (Pages,
 * Calendar) and a wide INKED NIB that starts the day's page. The pen is no longer
 * a circle to find; it is the page's one filled control, named.
 */
@Composable
private fun JournalDeskBar(
    alreadyWritten: Boolean,
    onOpenCollection: () -> Unit,
    onOpenCalendar: () -> Unit,
    onWrite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = JournalLook.ink()
    val accent = JournalLook.accent()
    val onAccent = JournalLook.onAccent()
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = JournalLook.paperRaised(),
        shadowElevation = JournalLook.DeskLift,
        modifier = modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
    ) {
        Column {
            // The desk's own edge: a brass rule, the way a writing box is edged.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(JournalLook.brass().copy(alpha = 0.32f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeskDrawer(
                    glyph = CurioIcons.MenuBook,
                    label = "Pages",
                    ink = ink,
                    onClick = onOpenCollection
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(JournalLook.rule())
                )
                Spacer(Modifier.width(6.dp))
                DeskDrawer(
                    glyph = CurioIcons.CalendarToday,
                    label = "Calendar",
                    ink = ink,
                    onClick = onOpenCalendar
                )
                Spacer(Modifier.width(14.dp))
                // The nib: the page's one filled control, and its name.
                Surface(
                    onClick = onWrite,
                    color = accent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CurioIcon(
                            CurioIcons.Note,
                            null,
                            tint = onAccent,
                            size = 18.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (alreadyWritten) "Write a page" else "Write today",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = JournalLook.Display,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = onAccent,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** One labelled drawer on the desk: a glyph, its name, and a press. */
@Composable
private fun DeskDrawer(
    glyph: String,
    label: String,
    ink: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CurioIcon(
            glyph,
            null,
            tint = JournalLook.accentInk().copy(alpha = 0.78f),
            size = 20.dp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = JournalLook.Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.5.sp,
                letterSpacing = 0.9.sp
            ),
            color = ink.copy(alpha = 0.52f)
        )
    }
}
