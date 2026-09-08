package com.luastudio.ai.services

import kotlinx.coroutines.suspendCancellableCoroutine
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.OutputStream
import java.io.PrintStream

data class LuaExecutionResult(
    val success: Boolean,
    val errorMessage: String? = null
)

private const val ROBLOX_MESSAGE = "Roblox APIs are not available in LUA STUDIO."

private val ROBLOX_GLOBAL_NAMES = listOf(
    "game", "workspace", "Instance", "Players", "LocalPlayer",
    "ReplicatedStorage", "RemoteEvent", "RemoteFunction"
)

/**
 * Stands in for Roblox-only globals so referencing them fails with a clear,
 * honest message instead of silently returning nil or pretending this is a
 * Roblox/Luau environment. This is a standalone Lua runtime, not Luau.
 */
private class RobloxStubValue : LuaTable() {
    override fun tojstring(): String = ROBLOX_MESSAGE
    override fun get(key: LuaValue): LuaValue = throw LuaError(ROBLOX_MESSAGE)
}

/**
 * Installs a per-instruction hook so a run can be stopped cooperatively —
 * either because the user pressed Stop or because the deadline passed —
 * without needing to force-kill the interpreter thread.
 */
private class LimitedDebugLib(
    private val deadlineMillis: Long,
    private val isCancelled: () -> Boolean
) : DebugLib() {
    override fun onInstruction(pc: Int, v: Varargs, top: Int) {
        if (isCancelled()) throw LuaError("Execution stopped by user.")
        if (System.currentTimeMillis() > deadlineMillis) throw LuaError("Execution timed out.")
        super.onInstruction(pc, v, top)
    }
}

/** Buffers bytes into lines and forwards each completed line to [onLine], enforcing [limitChars]. */
private class LineBufferedOutputStream(
    private val limitChars: Int,
    private val onLine: (String) -> Unit
) : OutputStream() {
    private val buffer = StringBuilder()
    private var totalChars = 0
    private var limitHit = false

    override fun write(b: Int) {
        if (limitHit) return
        when (val c = b.toChar()) {
            '\n' -> flush()
            '\r' -> Unit
            else -> {
                buffer.append(c)
                totalChars++
                if (totalChars > limitChars) {
                    limitHit = true
                    buffer.setLength(0)
                    onLine("Output limit exceeded.")
                }
            }
        }
    }

    override fun flush() {
        if (buffer.isNotEmpty()) {
            onLine(buffer.toString())
            buffer.setLength(0)
        }
    }
}

/**
 * Offline Lua runtime backed by LuaJ — a pure-JVM Lua interpreter with no
 * native code and no network access. This executes standard Lua, not Roblox
 * Luau: Roblox-only globals are stubbed to fail clearly (see
 * [RobloxStubValue]) rather than silently doing nothing or faking Roblox
 * behavior. Nothing here ever leaves the device.
 */
class LuaRuntimeService {

    @Volatile private var cancelRequested = false

    suspend fun execute(
        code: String,
        timeoutMillis: Long,
        outputLimitChars: Int,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): LuaExecutionResult = suspendCancellableCoroutine { continuation ->
        cancelRequested = false
        val deadline = System.currentTimeMillis() + timeoutMillis

        val thread = Thread({
            val outputStream = LineBufferedOutputStream(outputLimitChars, onOutput)
            val globals: Globals = JsePlatform.standardGlobals()
            globals.STDOUT = PrintStream(outputStream, true)
            globals.load(LimitedDebugLib(deadline) { cancelRequested })

            for (name in ROBLOX_GLOBAL_NAMES) {
                globals.set(name, RobloxStubValue())
            }

            val result: LuaExecutionResult = try {
                val chunk = globals.load(code, "script")
                chunk.call()
                outputStream.flush()
                LuaExecutionResult(success = true)
            } catch (e: LuaError) {
                outputStream.flush()
                val message = e.message ?: "Runtime error"
                onError(message)
                LuaExecutionResult(success = false, errorMessage = message)
            } catch (e: Exception) {
                outputStream.flush()
                val message = e.message ?: e.javaClass.simpleName
                onError(message)
                LuaExecutionResult(success = false, errorMessage = message)
            }

            if (continuation.isActive) continuation.resumeWith(Result.success(result))
        }, "lua-runtime")
        thread.isDaemon = true

        continuation.invokeOnCancellation { cancelRequested = true }
        thread.start()
    }

    fun stop() {
        cancelRequested = true
    }
}
