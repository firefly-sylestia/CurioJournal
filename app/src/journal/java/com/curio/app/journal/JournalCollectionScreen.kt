package com.curio.app.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.curio.app.data.AppPreferences
import com.curio.app.data.PersonalMood
import com.curio.app.data.PersonalNoteEntity
import com.curio.app.data.PersonalRepositoryHolder
import com.curio.app.data.wordCount
import com.curio.app.features.personal.PersonalShelfSnapshot
import com.curio.app.features.personal.personalMoodGlyph
import com.curio.app.features.personal.prettyDate
import com.curio.app.features.personal.rememberJournalDoor
import com.curio.app.features.personal.startOfToday
import com.curio.app.features.personal.toLocalDate
import com.curio.app.navigation.CurioRoutes
import com.curio.app.ui.theme.CurioIcon
import com.curio.app.ui.theme.CurioIcons
import com.curio.app.ui.theme.CurioMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * THE JOURNAL'S OWN COLLECTION (v536) — every page, bound the way the home page is.
 *
 * The journal variant used to open `features/personal/JournalListScreen.kt` for
 * "All pages": a 1300-line screen in the MAIN source set that the app's Personal
 * side opens too, wearing the app's personal-family tokens (its header, its
 * surface containers, its rose accent). It could not wear `JournalLook` without
 * moving the journal's theme into `src/main` and repainting an app surface, so the
 * member's call was to build the collection inside the journal instead.
 *
 * So this is the same collection — search, felt/length filters, the newest-first
 * order, grouped by month, long-press to remove, today's goal against a rail — on
 * the journal's paper:
 *
 *  · the month is an overline above a hairline, with its own page count in brass;
 *  · a page is a LEAF (`JournalPageCard`), the same one home and the calendar show;
 *  · the find row is a paper field and one round filter door, and the filters
 *    under it are hairline chips that fill ox blood when they are live;
 *  · the write door is the same ox-blood nib the desk bar carries.
 *
 * Nothing about the data is new: the same snapshot, the same query, the same
 * preferences, and the same delete.
 */
@Composable
fun JournalCollectionScreen(navController: NavController) {
    // Seeded from the last look, so coming back from a page never flashes an
    // empty collection before Room answers (see PersonalShelfSnapshot).
    val pages by produceState(initialValue = PersonalShelfSnapshot.journals) {
        runCatching {
            PersonalRepositoryHolder.repo.observeJournals().collect {
                PersonalShelfSnapshot.journals = it
                value = it
            }
        }
    }
    // DAYS ONLY: a to-do list and a note on a topic live in Notes.
    val journals = remember(pages) { pages.filter { !it.isTodo && !it.hasTopic } }

    val context = LocalContext.current
    val locale = Locale.getDefault()
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var moodFilter by remember { mutableStateOf<PersonalMood?>(null) }
    var lengthFilter by remember { mutableStateOf(JournalLength.ANY) }
    var filtersOpen by remember { mutableStateOf(false) }
    var newestFirst by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<PersonalNoteEntity?>(null) }

    // Today against the goal — the one number a writing goal has, and only while a
    // goal is set (the count means decoding the day's document, so it is asked of
    // one day, never of the collection).
    val goal = remember { AppPreferences.getJournalGoal(context) }
    val todayWords by produceState(0, journals, goal) {
        value = if (goal <= 0) {
            0
        } else {
            val today = startOfToday()
            withContext(Dispatchers.Default) {
                journals.filter { it.dateMillis == today }.sumOf { it.doc.wordCount() }
            }
        }
    }

    // The nib's door, resolved once for the empty state and the floating nib.
    val todayEntryId = remember(journals) {
        val today = startOfToday()
        journals.firstOrNull { it.dateMillis == today }?.id
    }
    val journalDoor = rememberJournalDoor(navController, todayEntryId)

    val needle = query.trim().lowercase()
    val filtersOn = moodFilter != null || lengthFilter != JournalLength.ANY
    // A length only exists inside a page's document, so the counts are taken ONCE
    // off the main thread and only while a length is being filtered for.
    val lengths by produceState<Map<String, Int>>(emptyMap(), journals, lengthFilter) {
        value = if (lengthFilter == JournalLength.ANY) {
            emptyMap()
        } else {
            withContext(Dispatchers.Default) {
                journals.associate { page -> page.id to page.doc.wordCount() }
            }
        }
    }
    val matched = remember(journals, needle, moodFilter, lengthFilter, lengths) {
        journals.filter { page ->
            (needle.isEmpty() || page.answers(needle)) &&
                (moodFilter == null || page.moodEnum == moodFilter) &&
                (lengthFilter == JournalLength.ANY ||
                    lengthFilter.holds(lengths[page.id] ?: 0))
        }
    }
    // `writtenAtMillis` is the tiebreaker, NOT `updatedAtMillis`: a page edited
    // late does not climb its own day.
    val ordered = remember(matched, newestFirst) {
        val by = compareBy<PersonalNoteEntity> { it.dateMillis }
            .thenBy { it.writtenAtMillis() }
        matched.sortedWith(if (newestFirst) by.reversed() else by)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JournalLook.paper())
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            CollectionHead(
                subtitle = when {
                    needle.isNotEmpty() || filtersOn -> "${matched.size} of ${journals.size} pages"
                    journals.isEmpty() -> "A page for a day"
                    journals.size == 1 -> "1 page"
                    else -> "${journals.size} pages"
                },
                newestFirst = newestFirst,
                onSort = { newestFirst = !newestFirst },
                onBack = { navController.popBackStack() }
            )
            FindRow(
                query = query,
                onQuery = { query = it },
                filtersOpen = filtersOpen,
                filtersActive = filtersOn,
                onToggleFilters = { filtersOpen = !filtersOpen }
            )
            if (goal > 0) {
                GoalLine(words = todayWords, goal = goal)
            }
            AnimatedVisibility(
                visible = filtersOpen,
                enter = CurioMotion.pillArrive(fromTop = true),
                exit = CurioMotion.pillLeave(fromTop = true)
            ) {
                FilterPanel(
                    mood = moodFilter,
                    length = lengthFilter,
                    onMood = { moodFilter = it },
                    onLength = { lengthFilter = it }
                )
            }

            when {
                journals.isEmpty() -> EmptyCollection(onWrite = journalDoor)

                ordered.isEmpty() -> NoMatches(needle = needle, query = query.trim())

                else -> {
                    // Grouped by month — a journal is a run of days, so a month is
                    // the chapter the list is read in. The grouping follows the
                    // order above, so reversing the list reverses the months too.
                    val months = remember(ordered) {
                        ordered.groupBy { page -> page.dateMillis.toLocalDate().withDayOfMonth(1) }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            start = 22.dp,
                            end = 22.dp,
                            top = 6.dp,
                            bottom = 120.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        months.forEach { (month, monthPages) ->
                            item(key = "month-$month") {
                                MonthRule(
                                    month = month,
                                    count = monthPages.size,
                                    locale = locale
                                )
                            }
                            items(items = monthPages, key = { it.id }) { page ->
                                JournalPageCard(
                                    page = page,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        navController.navigate(CurioRoutes.journalEditor(page.id)) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onLongClick = { pendingDelete = page }
                                )
                            }
                        }
                    }
                }
            }
        }

        // The same nib the desk bar carries, for a collection being browsed rather
        // than written: it starts the day the member came here from.
        Surface(
            onClick = journalDoor,
            shape = RoundedCornerShape(18.dp),
            color = JournalLook.accent(),
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CurioIcon(CurioIcons.Note, null, tint = JournalLook.onAccent(), size = 18.dp)
                Text(
                    "Write today",
                    style = JournalLook.titleStyle(14.sp),
                    color = JournalLook.onAccent()
                )
            }
        }
    }

    pendingDelete?.let { page ->
        RemovePageDialog(
            page = page,
            onDismiss = { pendingDelete = null },
            onRemove = {
                val id = page.id
                pendingDelete = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { PersonalRepositoryHolder.repo.deleteNote(id) }
                    }
                }
            }
        )
    }
}

// ── THE HEAD ────────────────────────────────────────────────────────────────

/**
 * The collection's masthead: the book's name, the screen's title, its count, and
 * the order as a stamped pill on the right.
 *
 * The order used to be the head's date pill; on paper it is a word — "Newest
 * first" / "Oldest first" — because a glyph for "which end am I reading from" is
 * a glyph nobody can read.
 */
@Composable
private fun CollectionHead(
    subtitle: String,
    newestFirst: Boolean,
    onSort: () -> Unit,
    onBack: () -> Unit
) {
    val ink = JournalLook.ink()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 20.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            CurioIcon(
                CurioIcons.ArrowBack,
                "Back",
                tint = ink.copy(alpha = 0.68f),
                size = 20.dp
            )
        }
        Spacer(Modifier.width(2.dp))
        Column(Modifier.weight(1f)) {
            JournalOverline("CURIO JOURNAL", JournalLook.accentInk().copy(alpha = 0.8f))
            Spacer(Modifier.height(5.dp))
            Text("All pages", style = JournalLook.titleStyle(30.sp), color = ink)
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = JournalLook.stampStyle(),
                color = JournalLook.brass().copy(alpha = 0.85f)
            )
        }
        Surface(
            onClick = onSort,
            shape = RoundedCornerShape(50),
            color = lerp(JournalLook.paper(), ink, 0.05f),
            border = BorderStroke(1.dp, JournalLook.rule())
        ) {
            Text(
                if (newestFirst) "Newest first" else "Oldest first",
                style = JournalLook.stampStyle().copy(letterSpacing = 0.6.sp),
                color = ink.copy(alpha = 0.62f),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
    }
}

// ── FINDING A PAGE ──────────────────────────────────────────────────────────

/** The find row: one paper field, one round door for the filters. */
@Composable
private fun FindRow(
    query: String,
    onQuery: (String) -> Unit,
    filtersOpen: Boolean,
    filtersActive: Boolean,
    onToggleFilters: () -> Unit
) {
    val ink = JournalLook.ink()
    val accent = JournalLook.accent()
    val accentInk = JournalLook.accentInk()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 20.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            shape = RoundedCornerShape(50),
            placeholder = {
                Text(
                    "Search your pages",
                    style = JournalLook.stampStyle().copy(letterSpacing = 0.6.sp),
                    color = ink.copy(alpha = 0.4f)
                )
            },
            textStyle = JournalLook.titleStyle(15.sp),
            leadingIcon = {
                CurioIcon(
                    CurioIcons.Search,
                    null,
                    tint = accentInk.copy(alpha = 0.7f),
                    size = 16.dp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = JournalLook.rule(),
                focusedContainerColor = JournalLook.paperRaised(),
                unfocusedContainerColor = JournalLook.paperRaised(),
                cursorColor = accent,
                focusedTextColor = ink,
                unfocusedTextColor = ink
            ),
            modifier = Modifier.weight(1f)
        )
        // The door wears the accent while anything is actually filtering, so a
        // member who filtered a week ago can see that this is not the whole book.
        Surface(
            onClick = onToggleFilters,
            shape = CircleShape,
            color = if (filtersActive || filtersOpen) {
                lerp(JournalLook.paper(), accent, 0.18f)
            } else {
                JournalLook.paperRaised()
            },
            border = BorderStroke(1.dp, JournalLook.rule())
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CurioIcon(
                    CurioIcons.Tune,
                    if (filtersOpen) "Hide the filters" else "Filter your pages",
                    tint = if (filtersActive || filtersOpen) accentInk
                    else ink.copy(alpha = 0.6f),
                    size = 19.dp
                )
            }
        }
    }
}

/** Today against the goal: the count in brass, then a rail that fills. */
@Composable
private fun GoalLine(words: Int, goal: Int) {
    val met = words >= goal
    val accent = JournalLook.accent()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            if (met) "TODAY: $words WORDS · GOAL MET" else "TODAY: $words OF $goal WORDS",
            style = JournalLook.stampStyle(),
            color = if (met) JournalLook.accentInk()
            else JournalLook.brass().copy(alpha = 0.85f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(JournalLook.ink().copy(alpha = 0.09f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((words.toFloat() / goal.toFloat()).coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
        }
    }
}

/** Two labelled rows of chips — how it felt, and how much was written. */
@Composable
private fun FilterPanel(
    mood: PersonalMood?,
    length: JournalLength,
    onMood: (PersonalMood?) -> Unit,
    onLength: (JournalLength) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterWord("FELT")
            JournalChip(
                label = "Any",
                live = mood == null,
                glyph = null,
                onClick = { onMood(null) }
            )
            PersonalMood.entries.forEach { option ->
                JournalChip(
                    label = option.label,
                    live = mood == option,
                    glyph = personalMoodGlyph(option),
                    onClick = { onMood(option) }
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterWord("LENGTH")
            JournalLength.entries.forEach { option ->
                JournalChip(
                    label = option.label,
                    live = length == option,
                    glyph = null,
                    onClick = { onLength(option) }
                )
            }
        }
    }
}

@Composable
private fun FilterWord(text: String) {
    Text(
        text,
        style = JournalLook.stampStyle().copy(fontSize = 9.5.sp),
        color = JournalLook.ink().copy(alpha = 0.38f),
        maxLines = 1,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp)
    )
}

/** One choice: a hairline at rest, an ox-blood wash and ink when it is live. */
@Composable
private fun JournalChip(
    label: String,
    live: Boolean,
    glyph: String?,
    onClick: () -> Unit
) {
    val ink = JournalLook.ink()
    val accentInk = JournalLook.accentInk()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (live) {
            lerp(JournalLook.paper(), JournalLook.accent(), 0.14f)
        } else {
            JournalLook.paperRaised()
        },
        border = BorderStroke(1.dp, if (live) accentInk.copy(alpha = 0.3f) else JournalLook.rule())
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (glyph != null) {
                CurioIcon(
                    glyph,
                    null,
                    tint = if (live) accentInk else ink.copy(alpha = 0.55f),
                    size = 14.dp
                )
            }
            Text(
                label,
                style = JournalLook.stampStyle().copy(letterSpacing = 0.5.sp),
                color = if (live) accentInk else ink.copy(alpha = 0.66f),
                maxLines = 1
            )
        }
    }
}

// ── THE LIST ────────────────────────────────────────────────────────────────

/** A month's chapter head: its name in small caps, its count in brass, a hairline. */
@Composable
private fun MonthRule(month: java.time.LocalDate, count: Int, locale: Locale) {
    val ink = JournalLook.ink()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).uppercase(),
                style = JournalLook.overlineStyle(),
                color = ink.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (count == 1) "1 PAGE" else "$count PAGES",
                style = JournalLook.stampStyle().copy(fontSize = 9.5.sp),
                color = JournalLook.brass().copy(alpha = 0.72f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(JournalLook.rule())
        )
    }
}

/** Nothing written yet: the invitation, with the one door out of it. */
@Composable
private fun EmptyCollection(onWrite: () -> Unit) {
    val ink = JournalLook.ink()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        JournalOverline("A PAGE FOR A DAY", JournalLook.accentInk().copy(alpha = 0.8f))
        Text(
            "Nothing written yet",
            style = JournalLook.titleStyle(26.sp),
            color = ink
        )
        Text(
            "A journal page is a day you decided to keep: how it felt, what " +
                "happened, what you want to remember.",
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            onClick = onWrite,
            shape = RoundedCornerShape(14.dp),
            color = JournalLook.accent()
        ) {
            Text(
                "Write today's page",
                style = JournalLook.titleStyle(14.sp),
                color = JournalLook.onAccent(),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)
            )
        }
    }
}

/** A find that came up empty — and which of the two things to change. */
@Composable
private fun NoMatches(needle: String, query: String) {
    val ink = JournalLook.ink()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Nothing matches", style = JournalLook.titleStyle(20.sp), color = ink)
        Text(
            when {
                needle.isNotEmpty() ->
                    "No page in your journals has \u201C$query\u201D in it."
                else -> "No page in your journals answers every filter at once."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = 0.55f)
        )
    }
}

// ── REMOVING A PAGE ─────────────────────────────────────────────────────────

/** The remove question, on the journal's paper. */
@Composable
private fun RemovePageDialog(
    page: PersonalNoteEntity,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    val ink = JournalLook.ink()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JournalLook.paperRaised(),
        title = {
            Text(
                "Remove this page?",
                style = JournalLook.titleStyle(20.sp),
                color = ink
            )
        },
        text = {
            Text(
                "The ${page.dateMillis.prettyDate()} entry and everything written in it " +
                    "leaves your journals. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = ink.copy(alpha = 0.6f)
            )
        },
        confirmButton = {
            TextButton(onClick = onRemove) {
                Text(
                    "Remove",
                    style = JournalLook.stampStyle().copy(letterSpacing = 0.8.sp),
                    color = JournalLook.accentInk()
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Keep",
                    style = JournalLook.stampStyle().copy(letterSpacing = 0.8.sp),
                    color = ink.copy(alpha = 0.62f)
                )
            }
        }
    )
}

// ── WHAT THE COLLECTION ASKS OF A PAGE ──────────────────────────────────────

/**
 * Whether a page answers a search. It reads what a LEAF already shows — the
 * title, the stored opening line, the topic, the mood and the date line — and
 * never decodes a document, so a keystroke cannot cost a JSON parse per page.
 */
private fun PersonalNoteEntity.answers(needle: String): Boolean =
    title.lowercase().contains(needle) ||
        preview.lowercase().contains(needle) ||
        topicName.lowercase().contains(needle) ||
        (moodEnum?.label.orEmpty()).lowercase().contains(needle) ||
        dateMillis.prettyDate().lowercase().contains(needle)

/**
 * When a page was WRITTEN, not when it was last touched — a page edited late in
 * the evening does not climb its own day. Rows written before v389 stamped a
 * creation time carry only `updatedAtMillis`, which is better than no stamp.
 */
private fun PersonalNoteEntity.writtenAtMillis(): Long =
    if (createdAtMillis > 0L) createdAtMillis else updatedAtMillis

/**
 * The lengths a journal page is filed under: three buckets, not a slider — the
 * question asked of a collection of days is "where are the long ones" / "where
 * did I only manage a line", and a range would be a second thing to set first.
 */
private enum class JournalLength(val label: String) {
    ANY("Any length"),
    SHORT("A few lines"),
    PAGE("About a page"),
    LONG("Longer");

    fun holds(words: Int): Boolean = when (this) {
        ANY -> true
        SHORT -> words < 120
        PAGE -> words in 120..399
        LONG -> words >= 400
    }
}
