package com.luastudio.ai.utils

private val BLOCK_OPENERS = Regex("""\b(function|then|do|repeat)\s*$""")
private val INCREASES_INDENT = setOf("function", "then", "do", "repeat", "{", "(", "[")
private val DECREASES_INDENT = setOf("end", "until", "else", "elseif", "}", ")", "]")

/**
 * Given the text up to (and including) the newline just typed, returns the
 * whitespace that should be inserted to continue/adjust indentation —
 * matches the current line's indent, plus one extra tab if the line ends
 * with a block opener (function/then/do/repeat/{/(/[).
 */
fun computeAutoIndent(textBeforeCursor: String, tabSize: Int): String {
    val lastLineBreak = textBeforeCursor.lastIndexOf('\n', textBeforeCursor.length - 2)
    val previousLine = textBeforeCursor.substring(
        (lastLineBreak + 1).coerceAtLeast(0),
        (textBeforeCursor.length - 1).coerceAtLeast(0)
    )
    val currentIndent = previousLine.takeWhile { it == ' ' }
    val trimmed = previousLine.trim()

    val opensBlock = BLOCK_OPENERS.containsMatchIn(trimmed) ||
        trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")

    return if (opensBlock) currentIndent + " ".repeat(tabSize) else currentIndent
}

private val OPEN_TO_CLOSE = mapOf('(' to ')', '[' to ']', '{' to '}')
private val CLOSE_TO_OPEN = mapOf(')' to '(', ']' to '[', '}' to '{')

/**
 * Finds the index of the bracket matching the one at [cursorIndex] (if the
 * character at or immediately before the cursor is a bracket). Returns null
 * when the cursor isn't next to a bracket or no match exists.
 */
fun findMatchingBracket(text: String, cursorIndex: Int): Int? {
    val candidates = listOfNotNull(
        cursorIndex.takeIf { it in text.indices },
        (cursorIndex - 1).takeIf { it in text.indices }
    )
    for (index in candidates) {
        val char = text[index]
        if (char in OPEN_TO_CLOSE.keys) {
            return scanForward(text, index, char, OPEN_TO_CLOSE.getValue(char))
        }
        if (char in CLOSE_TO_OPEN.keys) {
            return scanBackward(text, index, char, CLOSE_TO_OPEN.getValue(char))
        }
    }
    return null
}

private fun scanForward(text: String, from: Int, open: Char, close: Char): Int? {
    var depth = 0
    for (i in from until text.length) {
        when (text[i]) {
            open -> depth++
            close -> {
                depth--
                if (depth == 0) return i
            }
        }
    }
    return null
}

private fun scanBackward(text: String, from: Int, close: Char, open: Char): Int? {
    var depth = 0
    for (i in from downTo 0) {
        when (text[i]) {
            close -> depth++
            open -> {
                depth--
                if (depth == 0) return i
            }
        }
    }
    return null
}
