package com.luastudio.ai.data.files

import com.luastudio.ai.domain.model.LuaFile

/**
 * Home needs to tell the Editor screen *which* file to open or create when
 * the user taps "New Lua File" or a recent-file row. A full LuaFile object
 * doesn't fit cleanly into a Navigation Compose route string, and this is a
 * single-activity app, so a tiny synchronous holder is enough — consumed
 * once, immediately, in the Editor screen's launch effect.
 */
object PendingFileOpenHolder {
    private var pending: LuaFile? = null

    fun post(file: LuaFile) {
        pending = file
    }

    fun consume(): LuaFile? {
        val file = pending
        pending = null
        return file
    }
}
