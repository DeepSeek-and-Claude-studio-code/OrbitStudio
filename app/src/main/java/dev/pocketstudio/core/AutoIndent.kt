package dev.pocketstudio.core

/** Автоотступ после Enter: повторяет отступ предыдущей строки, а после `{`, `(`, `[` добавляет ещё 4 пробела. */
object AutoIndent {
    /**
     * [cursor] — позиция сразу после только что вставленного '\n'.
     * Возвращает новый текст и позицию курсора либо null, если менять нечего.
     */
    fun afterNewline(text: String, cursor: Int): Pair<String, Int>? {
        if (cursor < 1 || cursor > text.length || text[cursor - 1] != '\n') return null
        val prevStart = if (cursor < 2) 0 else text.lastIndexOf('\n', cursor - 2) + 1
        val prevLine = text.substring(prevStart, cursor - 1)
        val indent = prevLine.takeWhile { it == ' ' || it == '\t' }
        val last = prevLine.trimEnd().lastOrNull()
        val extra = if (last == '{' || last == '(' || last == '[') "    " else ""
        val insert = indent + extra
        if (insert.isEmpty()) return null
        return (text.substring(0, cursor) + insert + text.substring(cursor)) to (cursor + insert.length)
    }
}
