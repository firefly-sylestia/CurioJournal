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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curio.app.data.PersonalNoteEntity
import com.curio.app.data.PersonalRepositoryHolder
import com.curio.app.features.personal.PersonalShelfSnapshot
import com.curio.app.features.personal.journalPaper
import com.curio.app.features.personal.personalAccent
import com.curio.app.features.personal.personalAccentInk
import com.curio.app.features.personal.personalOnAccent
import com.curio.app.features.personal.shiftDay
import com.curio.app.features.personal.startOfToday
import com.curio.app.features.personal.toLocalDate
import com.curio.app.ui.theme.CurioIcon
import com.curio.app.ui.theme.CurioIcons
import com.curio.app.ui.theme.FrauncesFontFamily
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * THE MONTH, AS A PAGE OF DOORS.
 *
 * Home's strip is the last four weeks; this is the whole month, and every month
 * before it. A day with a page carries a filled dot in the journal's accent, a
 * day with more than one carries the count, today wears the ring, and a tap
 * opens that day's page (or a blank, already dated one).
 *
 * It is the calendar the journal door asked for: the collection is a list you
 * read, this is the one you scan.
 */
@Composable
fun JournalCalendarScreen(
    onBack: () -> Unit,
    onOpenDay: (millis: Long, entryId: String?) -> Unit
) {
    val accent = personalAccent()
    val accentInk = personalAccentInk()
    val ink = MaterialTheme.colorScheme.onBackground
    val locale = LocalConfiguration.current.locales[0]

    val pages by produceState(initialValue = PersonalShelfSnapshot.journals) {
        runCatching {
            PersonalRepositoryHolder.repo.observeJournals().collect {
                PersonalShelfSnapshot.journals = it
                value = it
            }
        }
    }
    val journals = remember(pages) { pages.filter { !it.isTodo && !it.hasTopic } }
    val byDay = remember(journals) { journals.groupBy { it.dateMillis } }
    val today = remember { startOfToday() }
    val todayDate = remember(today) { today.toLocalDate() }
    var month by remember { mutableStateOf(YearMonth.from(todayDate)) }

    val lead = remember(month) { month.atDay(1).dayOfWeek.value - 1 } // Monday first
    val length = remember(month) { month.lengthOfMonth() }
    val cells = remember(lead, length) { ((lead + length + 6) / 7) * 7 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 4.dp),
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
            Spacer(Modifier.width(2.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM", locale)),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    month.atDay(1).format(DateTimeFormatter.ofPattern("yyyy", locale)) +
                        " · " + monthCountLabel(byDay, month),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = accentInk.copy(alpha = 0.7f)
                )
            }
            MonthArrow(
                description = "Previous month",
                glyph = CurioIcons.ChevronLeft,
                tint = accentInk.copy(alpha = 0.8f)
            ) { month = month.minusMonths(1) }
            MonthArrow(
                description = "Next month",
                glyph = CurioIcons.ChevronRight,
                tint = ink.copy(alpha = 0.55f)
            ) { month = month.plusMonths(1) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp)
            ) {
                for (day in DayOfWeek.entries) {
                    Text(
                        day.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = ink.copy(alpha = 0.38f),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            for (week in 0 until cells / 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (slot in 0 until 7) {
                        val index = week * 7 + slot
                        val dayOfMonth = index - lead + 1
                        when {
                            dayOfMonth < 1 || dayOfMonth > length -> {
                                Box(Modifier.weight(1f).height(46.dp))
                            }

                            else -> {
                                val date = month.atDay(dayOfMonth)
                                val millis = millisFor(date, today, todayDate)
                                DayCell(
                                    date = date,
                                    count = byDay[millis]?.size ?: 0,
                                    isToday = date == todayDate,
                                    accent = accent,
                                    accentInk = accentInk,
                                    ink = ink,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onOpenDay(
                                            millis,
                                            byDay[millis]?.firstOrNull()?.id
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (month != YearMonth.from(todayDate)) {
                Surface(
                    onClick = { month = YearMonth.from(todayDate) },
                    shape = CircleShape,
                    color = accent,
                    contentColor = personalOnAccent(),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        CurioIcon(
                            CurioIcons.CalendarToday,
                            null,
                            tint = personalOnAccent(),
                            size = 15.dp
                        )
                        Text(
                            "Back to today",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = personalOnAccent()
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** One day in the month: its numeral, its pages, and today's ring. */
@Composable
private fun DayCell(
    date: LocalDate,
    count: Int,
    isToday: Boolean,
    accent: Color,
    accentInk: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val paper = journalPaper()
    Box(
        modifier = modifier
            .padding(2.dp)
            .height(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = if (count > 0) accent.copy(alpha = 0.10f) else Color.Transparent,
            border = if (isToday) {
                androidx.compose.foundation.BorderStroke(1.5.dp, accent)
            } else {
                null
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (count > 0 || isToday) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            }
                        ),
                        color = when {
                            isToday -> accentInk
                            count > 0 -> ink
                            else -> ink.copy(alpha = 0.55f)
                        }
                    )
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .size(if (count > 1) 12.dp else 4.dp)
                                .clip(CircleShape)
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (count > 1) {
                                Text(
                                    count.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp
                                    ),
                                    color = paper
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(
    description: String,
    glyph: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        CurioIcon(glyph, description, tint = tint, size = 20.dp)
    }
}

/** "8 days written" for the month in the header. */
private fun monthCountLabel(
    byDay: Map<Long, List<PersonalNoteEntity>>,
    month: YearMonth
): String {
    // A day's own dateMillis is its local midnight; reading it back in the same
    // zone is what puts it on the calendar day it was written on.
    val written = byDay.keys.count { millis ->
        val date = java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        date.year == month.year && date.monthValue == month.monthValue
    }
    return when (written) {
        0 -> "no pages yet"
        1 -> "1 day written"
        else -> "$written days written"
    }
}

/**
 * The epoch millis of a calendar day, derived from the store's own "today" by
 * whole days so the zone arithmetic is the one the pages were written with.
 */
private fun millisFor(date: LocalDate, today: Long, todayDate: LocalDate): Long =
    shiftDay(today, ChronoUnit.DAYS.between(todayDate, date))
