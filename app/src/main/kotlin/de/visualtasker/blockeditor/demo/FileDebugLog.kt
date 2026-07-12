package de.visualtasker.blockeditor.demo

import android.content.Context
import android.util.Log
import de.visualtasker.blockeditor.compose.debug.EditorDebugLog
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FileDebugLog {
    private const val TAG = "BlockEditor"
    private const val MAX_BYTES = 2_000_000L
    private val sessionStart = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .format(LocalDateTime.now())

    fun install(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        val file = File(dir, "blockeditor-$sessionStart.log")
        EditorDebugLog.install(file.absolutePath) { line ->
            when (line) {
                "__CLEAR__" -> file.writeText("")
                else -> appendLine(file, line)
            }
            Log.d(TAG, line)
        }
    }

    private fun appendLine(file: File, line: String) {
        if (file.length() > MAX_BYTES) {
            file.writeText("")
            file.appendText("${LocalDateTime.now()}: --- log rotated ---\n")
        }
        file.appendText("$line\n")
    }
}
