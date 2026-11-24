package com.pablojimenez.financetracker.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Asserts that every character composed under the node tagged [textTag] is laid out on one line,
 * whole, and inside the bounds of the node tagged [containerTag].
 */
fun SemanticsNodeInteractionsProvider.assertMoneyTextFits(textTag: String, containerTag: String) {
    val container = onNodeWithTag(containerTag, useUnmergedTree = true).fetchSemanticsNode()
        .boundsInRoot
    val root = onNodeWithTag(textTag, useUnmergedTree = true).fetchSemanticsNode()
    fun SemanticsNode.textNodes(): List<SemanticsNode> =
        (if (config.getOrNull(SemanticsProperties.Text) != null) listOf(this) else emptyList()) +
            children.flatMap { it.textNodes() }

    val texts = root.textNodes()
    assertTrue("no text composed", texts.isNotEmpty())
    texts.forEach { node ->
        val text = node.config[SemanticsProperties.Text].joinToString("")
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.single()
        assertEquals("'$text' is on one line", 1, layout.lineCount)
        assertEquals("'$text' is shown up to its last character", text.length, layout.getLineEnd(0))
        val naturalWidth = layout.multiParagraph.intrinsics.maxIntrinsicWidth
        assertTrue(
            "'$text' needs $naturalWidth px but has ${layout.size.width}",
            naturalWidth <= layout.size.width + 0.5f,
        )
        val bounds = node.boundsInRoot
        assertTrue(
            "'$text' at $bounds is inside $container",
            bounds.left >= container.left - 0.5f && bounds.right <= container.right + 0.5f,
        )
    }
}
