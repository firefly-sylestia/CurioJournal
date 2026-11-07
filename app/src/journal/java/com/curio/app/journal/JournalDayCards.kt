package com.curio.app.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.curio.app.data.PersonalNoteEntity
import com.curio.app.data.wordCount
import com.curio.app.features.personal.journalInk
import com.curio.app.features.personal.journalPaper
import com.curio.app.features.personal.personalAccent
import com.curio.app.features.personal.personalAccentInk
import com.curio.app.features.personal.personalMoodGlyph
import com.curio.app.ui.theme.CurioIcon
import com.curio.app.ui.theme.FrauncesFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ONE DAY'S WRITING, AS A CARD — the shape Home and the calendar both show.
 *
 * The ruled sheet is gone from these surfaces (the member: *"dont use the row
 * style like the ruled style"*): a day is a plain card with the time it was
 * written at the top, its title in the display serif where it has one, the
 * opening of the writing, and its length. Nothing here draws a line the writing
 * never had, which is what makes two of them sit as a row without looking like
 * two entries of a ledger.
 *
 * [compact] is the horizontal rectangle: the same card at a fixed width, so a
 * day with more than one page reads as a short shelf of them rather than a
 * column to scroll.
 */
@Composable
fun JournalPageCard(
    page: PersonalNoteEntity,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val ink = journalInk()
    val accent = personalAccent()
    val accentInk = personalAccentInk()
    val locale = LocalConfiguration.current.locales[0]
    val mood = page.moodEnum
    val spine = if (page.hasOwnAccent) Color(page.accentArgb) else accent
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (compact) 18.dp else 20.dp),
        color = journalPaper(),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val bar = 3.dp.toPx()
                    drawRoundRect(
                        color = spine,
                        size = Size(bar, size.height),
                        cornerRadius = CornerRadius(bar / 2f)
                    )
                }
                .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    page.writtenTimeLabel(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.45f)
                )
                Spacer(Modifier.weight(1f))
                if (mood != null) {
                    CurioIcon(
                        personalMoodGlyph(mood),
                        mood.label,
                        tint = accentInk.copy(alpha = 0.6f),
                        size = 14.dp
                    )
                }
            }
            if (page.title.isNotBlank()) {
                Text(
                    page.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                page.preview.ifBlank { "A page with nothing on it yet." },
                style = MaterialTheme.typography.bodyMedium,
                color = ink.copy(alpha = 0.76f),
                maxLines = if (compact) 5 else 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                wordsLabel(page),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = ink.copy(alpha = 0.52f)
            )
        }
    }
}

/**
 * A DAY WITH NOTHING WRITTEN — the space the writing will fill.
 *
 * The member asked for *"empty space with text to write something"*, so this is a
 * real area of the page rather than a card with a button on it: a quiet surface
 * with the invitation in it and one press that starts the page.
 */
@Composable
fun JournalBlankDay(
    prompt: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val ink = journalInk()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = journalPaper().copy(alpha = 0.55f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = ink.copy(alpha = 0.34f)
            )
        }
    }
}

/** "12 words" — a page's length, said plainly. */
fun wordsLabel(page: PersonalNoteEntity): String {
    val count = page.doc.wordCount()
    return when (count) {
        0 -> "Empty"
        1 -> "1 word"
        else -> "$count words"
    }
}

/** The time a page was written, as "9:42 PM" — the card's own stamp. */
fun PersonalNoteEntity.writtenTimeLabel(locale: Locale): String {
    val millis = createdAtMillis.takeIf { it > 0L } ?: updatedAtMillis
    return SimpleDateFormat("h:mm a", locale).format(Date(millis))
}
