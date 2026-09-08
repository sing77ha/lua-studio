package com.luastudio.ai.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Color tokens for one editor theme. Only the palette changes between themes. */
data class SyntaxPalette(
    val keyword: Color,
    val stringLiteral: Color,
    val comment: Color,
    val number: Color,
    val builtin: Color,
    val plain: Color
)

object LuaSyntaxThemes {
    val DEFAULT_DARK = SyntaxPalette(
        keyword = Color(0xFFC792EA),
        stringLiteral = Color(0xFFA9D67F),
        comment = Color(0xFF6A737D),
        number = Color(0xFFF78C6C),
        builtin = Color(0xFF82AAFF),
        plain = Color(0xFFE6E6EA)
    )
    val MIDNIGHT = SyntaxPalette(
        keyword = Color(0xFF7FB0FF),
        stringLiteral = Color(0xFF8FD9A8),
        comment = Color(0xFF54607A),
        number = Color(0xFFFFB86B),
        builtin = Color(0xFF6EE7E7),
        plain = Color(0xFFD8DEE9)
    )
    val DRACULA = SyntaxPalette(
        keyword = Color(0xFFFF79C6),
        stringLiteral = Color(0xFFF1FA8C),
        comment = Color(0xFF6272A4),
        number = Color(0xFFBD93F9),
        builtin = Color(0xFF8BE9FD),
        plain = Color(0xFFF8F8F2)
    )
    val MONOKAI = SyntaxPalette(
        keyword = Color(0xFFF92672),
        stringLiteral = Color(0xFFE6DB74),
        comment = Color(0xFF75715E),
        number = Color(0xFFAE81FF),
        builtin = Color(0xFF66D9EF),
        plain = Color(0xFFF8F8F2)
    )
    val LIGHT = SyntaxPalette(
        keyword = Color(0xFF9C27B0),
        stringLiteral = Color(0xFF2E7D32),
        comment = Color(0xFF9E9E9E),
        number = Color(0xFFEF6C00),
        builtin = Color(0xFF1565C0),
        plain = Color(0xFF1B1B1F)
    )
}

private val LUA_KEYWORDS = setOf(
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
    "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return",
    "then", "true", "until", "while"
)

private val LUA_BUILTINS = setOf(
    "print", "pairs", "ipairs", "type", "tostring", "tonumber", "pcall", "xpcall",
    "require", "table", "string", "math", "os", "io", "select", "setmetatable",
    "getmetatable", "rawget", "rawset", "next", "assert", "error", "unpack", "coroutine"
)

private val TOKEN_REGEX = Regex(
    """(--\[\[.*?\]\])|(--.*)|("(?:[^"\\]|\\.)*")|('(?:[^'\\]|\\.)*')|(\b\d+\.?\d*\b)|([A-Za-z_][A-Za-z0-9_]*)""",
    setOf(RegexOption.DOT_MATCHES_ALL)
)

/**
 * Tokenizes Lua/Luau source and returns a styled AnnotatedString. This is a
 * lightweight regex-based highlighter (not a full parser) — good enough for
 * readability, not a substitute for a real Lua grammar.
 */
fun highlightLua(source: String, palette: SyntaxPalette): AnnotatedString {
    return AnnotatedString.Builder(source).apply {
        for (match in TOKEN_REGEX.findAll(source)) {
            val range = match.range
            val text = match.value
            val color = when {
                match.groups[1] != null || match.groups[2] != null -> palette.comment
                match.groups[3] != null || match.groups[4] != null -> palette.stringLiteral
                match.groups[5] != null -> palette.number
                match.groups[6] != null && text in LUA_KEYWORDS -> palette.keyword
                match.groups[6] != null && text in LUA_BUILTINS -> palette.builtin
                else -> null
            } ?: continue
            addStyle(SpanStyle(color = color), range.first, range.last + 1)
        }
    }.toAnnotatedString()
}

/**
 * VisualTransformation wrapper so BasicTextField can render highlighted text
 * while the underlying TextFieldValue stays a plain, editable String.
 * Offsets are 1:1 (no characters added/removed), so mapping is identity.
 */
class LuaVisualTransformation(private val palette: SyntaxPalette) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = highlightLua(text.text, palette)
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

fun paletteFor(editorTheme: com.luastudio.ai.domain.model.EditorTheme): SyntaxPalette = when (editorTheme) {
    com.luastudio.ai.domain.model.EditorTheme.DEFAULT_DARK -> LuaSyntaxThemes.DEFAULT_DARK
    com.luastudio.ai.domain.model.EditorTheme.MIDNIGHT -> LuaSyntaxThemes.MIDNIGHT
    com.luastudio.ai.domain.model.EditorTheme.DRACULA -> LuaSyntaxThemes.DRACULA
    com.luastudio.ai.domain.model.EditorTheme.MONOKAI -> LuaSyntaxThemes.MONOKAI
    com.luastudio.ai.domain.model.EditorTheme.LIGHT -> LuaSyntaxThemes.LIGHT
}
