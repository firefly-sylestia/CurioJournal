package com.curio.app.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curio.app.ui.theme.FrauncesFontFamily
import com.curio.app.ui.theme.isCurioDarkTheme

/**
 * THE JOURNAL'S OWN THEME (v535) — not the app's.
 *
 * The member asked for CurioJournal to stand on its own: *"we will not use curio
 * theme a new themeing for the journal"*. So this is not a named Curio theme and
 * not the app's `MaterialTheme` roles either — it is the paper the journal is
 * printed on, and it is read only by the journal's own folder (`app/src/journal`).
 *
 * The answer is a WRITING DESK, not a feed: a warm cream sheet (a deep warm
 * brown-black at night), ink that is brown rather than blue-black, an OX BLOOD
 * accent for the day that is today and the nib that starts a page, and a quiet
 * brass for the small stamped lines. Nothing here is a category colour; a journal
 * does not change its mind with the topic you last opened.
 *
 * All of it is a function rather than a `MaterialTheme` override on purpose: the
 * journal screens read these directly, so the themes beside them in Settings can
 * never tint a page.
 */
internal object JournalLook {

    // ── THE PAPER ──────────────────────────────────────────────────────

    /** The sheet itself. */
    @Composable
    fun paper(): Color =
        if (isCurioDarkTheme()) Color(0xFF12100C) else Color(0xFFF7F0E1)

    /** A leaf laid ON the sheet (a card, the desk bar, the size panel's cousin). */
    @Composable
    fun paperRaised(): Color =
        if (isCurioDarkTheme()) Color(0xFF1C1813) else Color(0xFFFDF8ED)

    /** The shade under a leaf — the spine and the fold, never a border. */
    @Composable
    fun paperShade(): Color =
        if (isCurioDarkTheme()) Color(0xFF0B0907) else Color(0xFFE9DEC7)

    // ── THE INK ────────────────────────────────────────────────────────

    /** The writing's own ink: a warm brown-black, never a screen black. */
    @Composable
    fun ink(): Color =
        if (isCurioDarkTheme()) Color(0xFFF1E8D7) else Color(0xFF241A12)

    /** The same ink, set back — a margin note, a stamp, a date not yet written. */
    @Composable
    fun inkSoft(): Color = ink().copy(alpha = 0.60f)

    /** The notebooks's hairline: the rules under a week, above a section. */
    @Composable
    fun rule(): Color =
        ink().copy(alpha = if (isCurioDarkTheme()) 0.26f else 0.16f)

    // ── THE INKS THAT ARE NOT BLACK ────────────────────────────────────

    /** Ox blood: today's tab, the nib, the one filled thing on the page. */
    @Composable
    fun accent(): Color =
        if (isCurioDarkTheme()) Color(0xFFC87F66) else Color(0xFF77322C)

    /** The accent as small TEXT — a section's overline, a link. */
    @Composable
    fun accentInk(): Color =
        if (isCurioDarkTheme()) Color(0xFFDFA089) else Color(0xFF662823)

    /** Brass: the stamped line — words written, a streak, the desk's edge. */
    @Composable
    fun brass(): Color =
        if (isCurioDarkTheme()) Color(0xFFC9A66B) else Color(0xFF9A7738)

    /** The ink that reads ON [accent]. */
    @Composable
    fun onAccent(): Color =
        if (isCurioDarkTheme()) Color(0xFF221410) else Color(0xFFFBF4E5)

    // ── TYPE ───────────────────────────────────────────────────────────

    /** The journal's display face — a serif, because a journal is set in one. */
    val Display: FontFamily = FrauncesFontFamily

    /** The masthead/overline face: a serif, small, widely letterspaced. */
    @Composable
    fun overlineStyle(): TextStyle = TextStyle(
        fontFamily = FrauncesFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 2.4.sp
    )

    /** A stamped line of small print (words, streak, a page's time). */
    @Composable
    fun stampStyle(): TextStyle = TextStyle(
        fontFamily = FrauncesFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.3.sp
    )

    /** The desk bar's own lift, and the paper leaves' — one scale, two steps. */
    val LeafLift: Dp = 3.dp
    val DeskLift: Dp = 16.dp
}

/**
 * THE DOUBLE RULE — the journal's one piece of ornament.
 *
 * A masthead is separated from the day by two hairlines, the second half the
 * weight of the first. It is the only decorative line in the theme: everything
 * else that draws a line is a real rule (a week's divider, a section's edge).
 */
@Composable
internal fun JournalDoubleRule(modifier: Modifier = Modifier) {
    val ink = JournalLook.ink()
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ink.copy(alpha = 0.34f))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 3.dp)
                .height(1.dp)
                .background(ink.copy(alpha = 0.13f))
        )
    }
}

/** The masthead's small caps, in one place so every section reads the same. */
@Composable
internal fun JournalOverline(
    text: String,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    Text(
        text,
        modifier = modifier,
        style = JournalLook.overlineStyle(),
        color = if (color == Color.Unspecified) JournalLook.inkSoft() else color
    )
}
