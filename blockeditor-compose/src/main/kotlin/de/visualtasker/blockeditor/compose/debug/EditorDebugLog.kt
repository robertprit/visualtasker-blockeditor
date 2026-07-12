package de.visualtasker.blockeditor.compose.debug

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Einfaches Datei-Logging für Debug-Sessions.
 * In der App per [install] mit Dateischreiber verbinden; in Tests bleibt es stumm.
 */
object EditorDebugLog {
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    var logFilePath: String? = null
        private set

    private var writer: ((String) -> Unit)? = null

    fun install(logPath: String, writeLine: (String) -> Unit) {
        logFilePath = logPath
        writer = writeLine
        i("EditorDebugLog", "Logfile aktiv: $logPath")
    }

    fun clear() {
        i("EditorDebugLog", "Log geleert")
        writer?.invoke("__CLEAR__")
    }

    fun d(tag: String, message: String) = log("D", tag, message)

    fun i(tag: String, message: String) = log("I", tag, message)

    fun w(tag: String, message: String) = log("W", tag, message)

    private fun log(level: String, tag: String, message: String) {
        val line = "${LocalDateTime.now().format(timestampFormat)} $level/$tag: $message"
        writer?.invoke(line)
    }
}
