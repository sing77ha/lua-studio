package com.luastudio.ai.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real LuaJ-backed runtime (no mocking) against the sample programs
 * called out in the project spec. These are plain JVM unit tests — no
 * Android framework or emulator needed — so `./gradlew test` runs them.
 */
class LuaRuntimeServiceTest {

    private fun runLua(code: String, timeoutMillis: Long = 5000L): Pair<LuaExecutionResult, List<String>> {
        val service = LuaRuntimeService()
        val output = mutableListOf<String>()
        var result: LuaExecutionResult? = null
        runBlocking {
            result = service.execute(
                code = code,
                timeoutMillis = timeoutMillis,
                outputLimitChars = 20000,
                onOutput = { output.add(it) },
                onError = { output.add("ERROR: $it") }
            )
        }
        return result!! to output
    }

    @Test
    fun `print writes to output`() {
        val (result, output) = runLua("""print("Hello World")""")
        assertTrue(result.success)
        assertEquals(listOf("Hello World"), output)
    }

    @Test
    fun `variables and print of a variable`() {
        val (result, output) = runLua(
            """
            local x = 10
            print(x)
            """.trimIndent()
        )
        assertTrue(result.success)
        assertEquals(listOf("10"), output)
    }

    @Test
    fun `arithmetic addition`() {
        val (result, output) = runLua(
            """
            local a = 10
            local b = 20
            print(a + b)
            """.trimIndent()
        )
        assertTrue(result.success)
        assertEquals(listOf("30"), output)
    }

    @Test
    fun `multiple print arguments are space separated`() {
        val (result, output) = runLua("""print("X =", 10)""")
        assertTrue(result.success)
        assertEquals(listOf("X =\t10"), output)
    }

    @Test
    fun `if elseif else branches correctly`() {
        val (result, output) = runLua(
            """
            local x = 5
            if x > 10 then
                print("big")
            elseif x > 0 then
                print("small positive")
            else
                print("non-positive")
            end
            """.trimIndent()
        )
        assertTrue(result.success)
        assertEquals(listOf("small positive"), output)
    }

    @Test
    fun `for loop counts correctly`() {
        val (result, output) = runLua(
            """
            for i = 1, 5 do
                print("Count:", i)
            end
            """.trimIndent()
        )
        assertTrue(result.success)
        assertEquals(5, output.size)
        assertEquals("Count:\t1", output.first())
        assertEquals("Count:\t5", output.last())
    }

    @Test
    fun `function definition and call`() {
        val (result, output) = runLua(
            """
            function hello()
                print("Hello")
            end
            hello()
            """.trimIndent()
        )
        assertTrue(result.success)
        assertEquals(listOf("Hello"), output)
    }

    @Test
    fun `table basic field access`() {
        val (result, output) = runLua(
            """
            local t = { name = "Lua", value = 42 }
            print(t.name, t.value)
            """.trimIndent()
        )
        assertTrue(result.success)
        assertEquals(listOf("Lua\t42"), output)
    }

    @Test
    fun `syntax error does not crash and is reported`() {
        val (result, _) = runLua("local x =")
        assertFalse(result.success)
        assertTrue(result.errorMessage != null)
    }

    @Test
    fun `runtime error calling a nil value does not crash`() {
        // Calling a nil value is a genuine Lua runtime error (unlike simply
        // reading an undefined global, which evaluates to nil silently).
        val (result, _) = runLua(
            """
            local x = 10
            y()
            """.trimIndent()
        )
        assertFalse(result.success)
        assertTrue(result.errorMessage != null)
    }

    @Test
    fun `infinite loop is stopped by the timeout rather than hanging`() {
        val (result, output) = runLua("while true do end", timeoutMillis = 500L)
        assertFalse(result.success)
        assertTrue(output.any { it.contains("timed out", ignoreCase = true) })
    }

    @Test
    fun `stop requests cancellation of a running script`() {
        val service = LuaRuntimeService()
        val output = mutableListOf<String>()
        runBlocking {
            val job = launch {
                service.execute(
                    code = "while true do end",
                    timeoutMillis = 10000L,
                    outputLimitChars = 20000,
                    onOutput = { output.add(it) },
                    onError = { output.add("ERROR: $it") }
                )
            }
            delay(200)
            service.stop()
            job.join()
        }
        assertTrue(output.any { it.contains("stopped", ignoreCase = true) })
    }

    @Test
    fun `referencing a Roblox-only global fails with a clear message instead of pretending to be Roblox`() {
        val (result, output) = runLua("""print(game)""")
        assertTrue(
            output.any { it.contains("Roblox APIs are not available") } ||
                result.errorMessage?.contains("Roblox") == true
        )
    }

    @Test
    fun `output beyond the limit is truncated with a clear message`() {
        val (_, output) = runLua(
            """
            for i = 1, 100000 do
                print("x")
            end
            """.trimIndent(),
            timeoutMillis = 10000L
        )
        assertTrue(output.any { it == "Output limit exceeded." })
    }
}
