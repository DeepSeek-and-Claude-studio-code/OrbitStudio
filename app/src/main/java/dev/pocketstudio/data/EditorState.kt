package dev.pocketstudio.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.pocketstudio.core.AutoIndent
import java.io.File
import kotlin.math.abs

/** Один открытый файл: текст, курсор, история правок. */
class OpenFile(val file: File, initial: String) {
    var value by mutableStateOf(TextFieldValue(initial))
        private set
    var savedText by mutableStateOf(initial)
        private set
    var undoCount by mutableIntStateOf(0)
        private set
    var redoCount by mutableIntStateOf(0)
        private set

    val dirty: Boolean get() = value.text != savedText

    private val undoStack = ArrayList<TextFieldValue>()
    private val redoStack = ArrayList<TextFieldValue>()
    private var lastPush = 0L

    fun edit(new: TextFieldValue) {
        val old = value
        var result = new
        if (new.text != old.text) {
            val now = System.currentTimeMillis()
            // Быстрый набор склеиваем в один шаг отмены; вставки и удаления кусков — отдельный шаг.
            if (now - lastPush > 800 || abs(new.text.length - old.text.length) > 1) {
                undoStack.add(old)
                if (undoStack.size > 200) undoStack.removeAt(0)
                lastPush = now
            }
            redoStack.clear()
            val cursor = new.selection.start
            if (new.text.length == old.text.length + 1 && new.selection.collapsed &&
                cursor >= 1 && new.text[cursor - 1] == '\n'
            ) {
                AutoIndent.afterNewline(new.text, cursor)?.let { (text, pos) ->
                    result = TextFieldValue(text, TextRange(pos))
                }
            }
        }
        value = result
        syncCounts()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(value)
        value = undoStack.removeAt(undoStack.lastIndex)
        lastPush = 0L
        syncCounts()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(value)
        value = redoStack.removeAt(redoStack.lastIndex)
        lastPush = 0L
        syncCounts()
    }

    /** Вставка текста в позицию курсора (или поверх выделения). */
    fun insert(text: String) {
        val v = value
        val s = v.selection.min
        val e = v.selection.max
        edit(TextFieldValue(v.text.replaceRange(s, e, text), TextRange(s + text.length)))
    }

    /** Возвращает текст ошибки или null. */
    fun save(): String? = try {
        file.writeText(value.text)
        savedText = value.text
        null
    } catch (e: Exception) {
        e.message ?: "Ошибка записи"
    }

    private fun syncCounts() {
        undoCount = undoStack.size
        redoCount = redoStack.size
    }
}

class EditorState {
    val tabs = mutableStateListOf<OpenFile>()
    var active by mutableIntStateOf(0)

    val current: OpenFile? get() = tabs.getOrNull(active)

    /** Возвращает текст ошибки или null. */
    fun open(file: File): String? {
        val existing = tabs.indexOfFirst { it.file == file }
        if (existing >= 0) {
            active = existing
            return null
        }
        if (!file.isFile) return "Это не файл"
        if (file.length() > 1_500_000) return "Файл больше 1,5 МБ — не открываю"
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return "Не удалось прочитать файл: ${e.message}"
        }
        if (bytes.take(4096).any { it == 0.toByte() }) return "Это двоичный файл"
        tabs.add(OpenFile(file, String(bytes, Charsets.UTF_8)))
        active = tabs.lastIndex
        return null
    }

    fun close(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        if (tab.dirty) tab.save()
        tabs.removeAt(index)
        active = when {
            tabs.isEmpty() -> 0
            index < active -> active - 1
            else -> active.coerceAtMost(tabs.lastIndex)
        }
    }

    fun closeAll() {
        tabs.clear()
        active = 0
    }

    /** Закрывает вкладки файла или всех файлов внутри папки (после переименования/удаления). */
    fun closeUnder(path: File) {
        val prefix = path.path
        var i = tabs.lastIndex
        while (i >= 0) {
            val p = tabs[i].file.path
            if (p == prefix || p.startsWith(prefix + File.separator)) close(i)
            i--
        }
    }

    /** Сохраняет все изменённые файлы. Возвращает первую ошибку или null. */
    fun saveAll(): String? {
        var error: String? = null
        for (t in tabs) if (t.dirty) t.save()?.let { if (error == null) error = it }
        return error
    }
}
