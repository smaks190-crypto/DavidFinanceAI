package com.example.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticRedactionTest {
    private val key = "AI" + "zaSyD9876543210abcdefghijklmnopQRSTU"
    private val token = "eyJhbGciOiJIUzI1NiJ9.testPayload.signature"
    private val secrets get() = listOf(key, token, "7391")
    private val payload get() = "key=$key Authorization: Bearer $token pin=7391"
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        GlobalConsoleLogger.clear()
        GlobalConsoleLogger.setPaused(false)
        context.getSharedPreferences("debug_console_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        GlobalConsoleLogger.clear()
        GlobalConsoleLogger.clearPersistedCrash()
    }

    private fun assertRedacted(text: String) {
        secrets.forEach { assertFalse("Secret remains in diagnostic output", text.contains(it)) }
    }

    @Test
    fun exceptionCausesAndSuppressedExceptionsAreRedactedInLogcatAndExports() {
        val error = IllegalStateException(payload, IllegalArgumentException(payload))
        error.addSuppressed(RuntimeException(payload))
        GlobalConsoleLogger.w("TEST", payload, throwable = error)
        GlobalConsoleLogger.e("TEST", payload, error)
        GlobalConsoleLogger.f("TEST", payload, error)
        val logs = ShadowLog.getLogsForTag("TEST")
        assertEquals(3, logs.size)
        logs.forEach {
            assertRedacted(it.msg)
            assertNull(it.throwable)
            assertTrue(it.msg.contains("IllegalArgumentException"))
            assertTrue(it.msg.contains("Suppressed"))
        }
        assertRedacted(GlobalConsoleLogger.logs.value.toString())
        assertRedacted(GlobalConsoleLogger.buildDiagnosticReport())
        assertRedacted(GlobalConsoleLogger.buildAiDiagnosticPrompt())
    }

    @Test
    fun restoredCrashFieldsAreRedactedBeforeDisplayAndRewrittenOnDisk() {
        val prefs = context.getSharedPreferences("debug_console_prefs", Context.MODE_PRIVATE)
        val fields = listOf("last_crash_message", "last_crash_stack", "last_crash_time",
            "last_crash_thread", "last_crash_op_id")
        val editor = prefs.edit()
        fields.forEach { editor.putString(it, payload) }
        editor.commit()
        GlobalConsoleLogger.restorePreviousSessionCrashIfAny(context)
        assertTrue(GlobalConsoleLogger.errors.value.single().isPreviousSessionCrash)
        assertRedacted(GlobalConsoleLogger.errors.value.toString())
        assertRedacted(prefs.all.toString())
        assertRedacted(GlobalConsoleLogger.buildDiagnosticReport())
        assertRedacted(GlobalConsoleLogger.buildAiDiagnosticPrompt())
    }

    @Test
    fun crashThreadIsMaskedBeforePersistenceAndOriginalExceptionIsDelegated() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        try {
            GlobalConsoleLogger.setupUncaughtExceptionHandler(context)
            val error = RuntimeException(payload)
            var delegated: Throwable? = null
            GlobalConsoleLogger.handleUncaughtException(
                Thread(payload), error, Thread.UncaughtExceptionHandler { _, t -> delegated = t }
            )
            assertSame(error, delegated)
            assertRedacted(context.getSharedPreferences("debug_console_prefs", Context.MODE_PRIVATE).all.toString())
            assertRedacted(GlobalConsoleLogger.errors.value.toString())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun metadataIsMaskedBeforeConsoleConsumption() {
        GlobalConsoleLogger.e("TEST", payload, operationId = payload)
        GlobalConsoleLogger.recordNetworkCall(payload, payload, 1L, NetworkCallStatus.ERROR,
            errorType = payload, errorMessage = payload, operationId = payload)
        GlobalConsoleLogger.recordDbOperation(payload, payload, 1L, false, payload, payload, payload)
        GlobalConsoleLogger.updateSystemState(activeBudgetId = payload, activeModalName = payload)
        assertRedacted(GlobalConsoleLogger.logs.value.toString())
        assertRedacted(GlobalConsoleLogger.networkTraces.value.toString())
        assertRedacted(GlobalConsoleLogger.dbOperations.value.toString())
        assertRedacted(GlobalConsoleLogger.systemState.value.toString())
        assertRedacted(GlobalConsoleLogger.buildDiagnosticReport())
    }
}
