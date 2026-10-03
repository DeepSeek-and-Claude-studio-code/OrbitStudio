package dev.pocketstudio.termux

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pocketstudio.data.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

enum class RunState { IDLE, STARTING, RUNNING, DONE }

/**
 * Запускает задачи в Termux и следит за ними по двум файлам: logs/run.status и logs/run.log.
 * Задача живёт в Termux независимо от приложения, поэтому после возврата в приложение ход работы подхватывается снова.
 */
object Runner {
    var state by mutableStateOf(RunState.IDLE)
        private set
    var label by mutableStateOf("")
        private set
    var exitCode by mutableIntStateOf(0)
        private set
    var log by mutableStateOf("")
        private set
    var problem by mutableStateOf<String?>(null)
        private set

    /** Увеличивается, когда задача завершилась — экраны обновляют списки файлов. */
    var finishedCount by mutableIntStateOf(0)
        private set

    val busy: Boolean get() = state == RunState.STARTING || state == RunState.RUNNING

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poller: Job? = null
    private const val START_TIMEOUT_MS = 25_000L

    private val statusFile get() = File(Workspace.logs, "run.status")
    private val logFile get() = File(Workspace.logs, "run.log")

    fun start(ctx: Context, taskLabel: String, vararg args: String): Boolean {
        if (busy) return false
        problem = null
        try {
            Workspace.ensure(ctx)
            statusFile.writeText("STARTING")
            logFile.writeText("")
        } catch (e: Exception) {
            problem = "Нет доступа к папке PocketStudio: ${e.message}. Выдайте доступ ко всем файлам на вкладке «Настройка»."
            return false
        }
        label = taskLabel
        log = ""
        exitCode = 0
        state = RunState.STARTING
        val result = Termux.runScript(ctx, taskLabel, *args)
        if (result.isFailure) {
            state = RunState.IDLE
            problem = result.exceptionOrNull()?.message ?: "Не удалось запустить Termux"
            return false
        }
        watch(System.currentTimeMillis())
        return true
    }

    /** Подхватывает уже идущую задачу после перезапуска приложения. */
    fun attach() {
        if (busy) return
        try {
            val f = statusFile
            if (!f.exists()) return
            val s = f.readText().trim()
            val fresh = System.currentTimeMillis() - f.lastModified() < 60_000L
            if (s == "RUNNING" || (s == "STARTING" && fresh)) {
                label = "Задача в Termux"
                state = if (s == "RUNNING") RunState.RUNNING else RunState.STARTING
                watch(System.currentTimeMillis())
            }
        } catch (_: Exception) {
        }
    }

    /** Сбрасывает «зависшее» состояние (например, если Android убил Termux). */
    fun forceReset() {
        poller?.cancel()
        try {
            statusFile.writeText("DONE:-1")
        } catch (_: Exception) {
        }
        state = RunState.DONE
        exitCode = -1
        finishedCount++
    }

    fun stop(ctx: Context) {
        Termux.runScript(ctx, "Остановка", "stop")
    }

    private fun watch(startedAt: Long) {
        poller?.cancel()
        poller = scope.launch {
            while (isActive) {
                val status = try {
                    statusFile.takeIf { it.exists() }?.readText()?.trim() ?: ""
                } catch (_: Exception) {
                    ""
                }
                log = cleanLog(tail(logFile))
                when {
                    status.startsWith("DONE:") -> {
                        exitCode = status.removePrefix("DONE:").toIntOrNull() ?: -1
                        state = RunState.DONE
                        finishedCount++
                        return@launch
                    }
                    status == "RUNNING" -> state = RunState.RUNNING
                    else -> {
                        state = RunState.STARTING
                        if (System.currentTimeMillis() - startedAt > START_TIMEOUT_MS) {
                            state = RunState.IDLE
                            problem = "Termux не ответил. Проверьте: Termux установлен из F-Droid/GitHub, " +
                                "в нём выполнена команда настройки (вкладка «Настройка») и выдано разрешение."
                            return@launch
                        }
                    }
                }
                delay(700)
            }
        }
    }

    private fun tail(file: File, maxBytes: Int = 64 * 1024): String {
        if (!file.exists()) return ""
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val start = maxOf(0L, len - maxBytes)
                raf.seek(start)
                val buf = ByteArray((len - start).toInt())
                raf.readFully(buf)
                val text = String(buf, Charsets.UTF_8)
                if (start > 0) text.substringAfter('\n', text) else text
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** Строки с «\r» (прогресс-бары) схлопываем до последнего состояния. */
    private fun cleanLog(raw: String): String =
        raw.replace("\r\n", "\n").split('\n').joinToString("\n") { line ->
            if (line.contains('\r')) line.substringAfterLast('\r') else line
        }
}
