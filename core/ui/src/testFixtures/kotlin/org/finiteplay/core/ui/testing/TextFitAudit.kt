package org.finiteplay.core.ui.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import android.util.Log
import java.io.File

/**
 * Finds labels that do not fit where they are drawn, on whatever is on screen, in the locale and
 * font scale the caller has set.
 *
 * Two failures are reported, both read from the text layout the label was really drawn with:
 *  - [Kind.CLIPPED]: the layout reports visual overflow, so letters are cut off or replaced by an
 *    ellipsis (a label that is too wide for a one-line slot, or too tall for its box);
 *  - [Kind.BROKEN_WORD]: a line ends inside a word, which is what a button label that is too
 *    narrow for its longest word looks like ("Réinitialiser" becoming "Réinitialis" / "er").
 *    Scripts that wrap anywhere (Han, kana, Hangul, Thai) are not flagged for this, and their line widths are not held to the box to the pixel.
 */
class TextFitAudit(private val locale: String, private val fontScale: Float) {
    enum class Kind { ELLIPSIZED, CLIPPED, BROKEN_WORD }

    data class Finding(
        val locale: String,
        val fontScale: Float,
        val screen: String,
        val kind: Kind,
        val tag: String,
        val text: String,
        val widthPx: Int,
        val heightPx: Int,
        val lines: Int,
        val needWidthPx: Int,
        val needHeightPx: Int,
    )

    val findings = mutableListOf<Finding>()

    /** Examines [root] and everything under it, filing what does not fit under [screen]. */
    fun scan(root: SemanticsNode, screen: String) {
        visit(root, screen)
    }

    private fun visit(node: SemanticsNode, screen: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        runCatching { node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
        val layout = layouts.firstOrNull()
        if (layout != null) examine(node, layout, screen)
        for (child in node.children) visit(child, screen)
    }

    private fun examine(node: SemanticsNode, layout: TextLayoutResult, screen: String) {
        val text = layout.layoutInput.text.text
        if (text.isBlank()) return
        val tag = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        fun file(kind: Kind) {
            val entry = Finding(locale, fontScale, screen, kind, tag, text.replace('\n', ' '), node.size.width, node.size.height, layout.lineCount, Math.ceil(widestLine(layout).toDouble()).toInt(), Math.ceil(layout.multiParagraph.height.toDouble()).toInt())
            if (findings.none { it.screen == screen && it.kind == kind && it.text == entry.text }) findings += entry
        }
        if (isEllipsized(layout)) file(Kind.ELLIPSIZED)
        if (isCut(layout, text)) file(Kind.CLIPPED)
        if (breaksAWord(layout, text)) file(Kind.BROKEN_WORD)
    }

    private fun widestLine(layout: TextLayoutResult): Float = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }

    private fun isEllipsized(layout: TextLayoutResult): Boolean = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }

    /**
     * Text the box could not hold and did not mark: lines dropped past `maxLines`, a line wider than
     * the box, lines taller than it. A pixel or two of difference is rounding, not clipping.
     */
    private fun isCut(layout: TextLayoutResult, text: String): Boolean {
        if (layout.multiParagraph.didExceedMaxLines) return true
        val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
        val slack = if (text.any { isUnspacedScript(it) }) UNSPACED_SLACK_PX else TOLERANCE_PX
        if (widest > layout.size.width + slack) return true
        // A single line taller than its box is line-height padding around glyphs that fit; only wrapped text loses lines.
        return layout.lineCount > 1 && layout.multiParagraph.height > layout.size.height + TOLERANCE_PX
    }

    private fun breaksAWord(layout: TextLayoutResult, text: String): Boolean {
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line, visibleEnd = false)
            if (end <= 0 || end >= text.length) continue
            val before = text[end - 1]
            val after = text[end]
            if (before.isLetterOrDigit() && after.isLetterOrDigit() && !isUnspacedScript(before) && !isUnspacedScript(after)) return true
        }
        return false
    }

    private fun isUnspacedScript(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL, Character.UnicodeScript.THAI -> true
        else -> false
    }

    companion object {
        private const val TOLERANCE_PX = 2f
        private const val UNSPACED_SLACK_PX = 40f
        private const val HEADER = "locale\tfontScale\tscreen\tkind\ttag\tw\th\tlines\ttext"

        /** Writes [audits]' findings as one tab-separated report, header first; returns the number of findings. */
        fun write(file: File, audits: List<TextFitAudit>): Int {
            val rows = audits.flatMap { it.findings }
            for (row in rows) Log.i("TEXTFIT", "${row.locale}	${row.fontScale}	${row.screen}	${row.kind}	${row.tag}	${row.widthPx}	${row.heightPx}	${row.lines}	${row.needWidthPx}	${row.needHeightPx}	${row.text}")
            file.parentFile?.mkdirs()
            file.writeText(
                (listOf(HEADER) + rows.map { "${it.locale}\t${it.fontScale}\t${it.screen}\t${it.kind}\t${it.tag}\t${it.widthPx}\t${it.heightPx}\t${it.lines}\t${it.text}" }).joinToString("\n") + "\n",
            )
            return rows.size
        }
    }
}
