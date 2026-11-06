package com.curio.app.ui.components

import com.curio.app.data.TextSpan

/** Shared layout guardrails for hand-placed quote cards. */
object QuoteLimits {
    const val MAX_CHARACTERS = 280
    const val MAX_LINES = 5
}

/**
 * Caps quote content before it reaches either the editor or a saved renderer.
 * The span list uses half-open offsets into the resulting text, so truncating
 * it here keeps bold/italic/highlight ranges valid for legacy and new data.
 */
fun limitQuoteContent(
    text: String,
    spans: List<TextSpan> = emptyList()
): Pair<String, List<TextSpan>> {
    val characterLimited = text.take(QuoteLimits.MAX_CHARACTERS)
    val lineLimited = characterLimited
        .split('\n')
        .take(QuoteLimits.MAX_LINES)
        .joinToString("\n")
    val limitedSpans = spans.mapNotNull { span ->
        val start = span.start.coerceIn(0, lineLimited.length)
        val end = span.end.coerceIn(start, lineLimited.length)
        if (end <= start) null else span.copy(start = start, end = end)
    }
    return lineLimited to limitedSpans
}
