package com.luastudio.ai.utils

private val INVALID_FILENAME_CHARS = Regex("""[/\\:*?"<>|]""")
private val EXTENSION_SUFFIX = Regex("""\.(lua|luau)$""", RegexOption.IGNORE_CASE)

/**
 * Strips characters that are illegal in a filename and any existing
 * .lua/.luau extension the user may have typed, leaving just the base name
 * so the caller can append the extension for the chosen language.
 */
fun sanitizeFileBaseName(rawName: String): String {
    val withoutExtension = rawName.trim().replace(EXTENSION_SUFFIX, "")
    return withoutExtension.replace(INVALID_FILENAME_CHARS, "").trim()
}
