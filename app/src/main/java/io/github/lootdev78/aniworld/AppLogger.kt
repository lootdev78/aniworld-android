package io.github.lootdev78.aniworld

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Zentrale, UI-lesbare Diagnose. Normale Einträge bleiben bewusst nur in der
 * aktuellen Sitzung; ein unbehandelter Absturz wird separat gespeichert und
 * beim nächsten Start als Diagnoseeintrag angeboten.
 */
object AppLogger {
    private const val TAG = "AniWorld"
    private const val MAX_ENTRIES = 250
    private const val CRASH_FILE = "last_crash.log"

    private val _entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<DiagnosticEntry>> = _entries.asStateFlow()

    @Volatile private var appContext: Context? = null
    @Volatile private var installed = false
    @Volatile private var loggingEnabled = true

    @Synchronized
    fun initialize(context: Context) {
        if (installed) return
        installed = true
        appContext = context.applicationContext

        val crashFile = File(context.filesDir, CRASH_FILE)
        if (crashFile.exists()) {
            val previousCrash = runCatching { crashFile.readText() }.getOrDefault("")
            if (previousCrash.isNotBlank()) {
                add(
                    level = LogLevel.ERROR,
                    area = "Vorheriger Absturz",
                    message = "Die letzte App-Sitzung wurde unerwartet beendet.",
                    details = previousCrash
                )
            }
            runCatching { crashFile.delete() }
        }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                if (!loggingEnabled) return@runCatching
                val details = buildString {
                    append("Thread: ").append(thread.name).append('\n')
                    append(throwable.stackTraceToString())
                }
                File(context.filesDir, CRASH_FILE).writeText(details)
                Log.e(TAG, "Unbehandelter Fehler", throwable)
            }
            previous?.uncaughtException(thread, throwable)
                ?: run {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
        }
    }

    fun setEnabled(enabled: Boolean) {
        loggingEnabled = enabled
        if (!enabled) _entries.value = emptyList()
    }

    fun isEnabled(): Boolean = loggingEnabled

    fun info(area: String, message: String, details: String = "") =
        add(LogLevel.INFO, area, message, details)

    fun warn(area: String, message: String, details: String = "") =
        add(LogLevel.WARNING, area, message, details)

    fun error(area: String, message: String, throwable: Throwable? = null, details: String = "") {
        val stack = throwable?.stackTraceToString().orEmpty()
        add(
            LogLevel.ERROR,
            area,
            message,
            listOf(details, stack).filter(String::isNotBlank).joinToString("\n")
        )
    }

    fun clear() {
        _entries.value = emptyList()
        appContext?.let { context -> runCatching { File(context.filesDir, CRASH_FILE).delete() } }
    }

    fun export(): String = _entries.value.joinToString("\n\n") { it.asText() }

    private fun add(level: LogLevel, area: String, message: String, details: String) {
        if (!loggingEnabled) return
        val entry = DiagnosticEntry(level = level, area = area, message = message, details = details)
        when (level) {
            LogLevel.INFO -> Log.i(TAG, entry.asText())
            LogLevel.WARNING -> Log.w(TAG, entry.asText())
            LogLevel.ERROR -> Log.e(TAG, entry.asText())
        }
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }
}
