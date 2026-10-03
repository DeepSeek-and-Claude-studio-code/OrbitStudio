package dev.pocketstudio.core

enum class Tok { KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION, TAG, ATTRIBUTE }

data class Span(val start: Int, val end: Int, val tok: Tok)

/** Простая подсветка синтаксиса на регулярных выражениях: Kotlin, Java, Gradle, XML, JSON, properties и т.п. */
object Highlighter {
    const val MAX_CHARS = 250_000

    private val keywords = (
        "abstract as assert boolean break by byte case catch char class companion const constructor continue " +
            "crossinline data default do double else enum extends external false final finally float for fun goto " +
            "if implements import in infix init inline inner instanceof int interface internal is lateinit long " +
            "native new noinline null object open operator out override package private protected public return " +
            "sealed short static strictfp super suspend switch synchronized this throw throws transient true try " +
            "typealias val var vararg void volatile when while"
        ).split(' ')

    // 1 — блочный комментарий, 2 — строчный, 3 — строка, 4 — аннотация, 5 — число, 6 — ключевое слово
    private val codeRegex = Regex(
        "(/\\*[\\s\\S]*?(?:\\*/|\\z))" +
            "|(//[^\\n]*)" +
            "|(\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)|\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?)" +
            "|(@[A-Za-z_][A-Za-z0-9_.]*)" +
            "|(\\b(?:0[xX][0-9a-fA-F_]+|[0-9][0-9_]*(?:\\.[0-9_]+)?(?:[eE][+-]?[0-9]+)?[fFLdD]?)\\b)" +
            "|\\b(" + keywords.joinToString("|") + ")\\b"
    )

    // 1 — комментарий, 2 — имя тега, 3 — строка, 4 — имя атрибута
    private val xmlRegex = Regex(
        "(<!--[\\s\\S]*?(?:-->|\\z))" +
            "|(</?[A-Za-z_][\\w:.\\-]*)" +
            "|(\"[^\"\\n]*\"?|'[^'\\n]*'?)" +
            "|([A-Za-z_][\\w:.\\-]*)(?==)"
    )

    // 1 — строка, 2 — число, 3 — true/false/null
    private val jsonRegex = Regex(
        "(\"(?:\\\\.|[^\"\\\\\\n])*\"?)" +
            "|(-?\\b[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?\\b)" +
            "|\\b(true|false|null)\\b"
    )

    // 1 — комментарий #, 2 — строка, 3 — число
    private val hashRegex = Regex(
        "(#[^\\n]*)" +
            "|(\"(?:\\\\.|[^\"\\\\\\n])*\"?|'[^'\\n]*'?)" +
            "|(\\b[0-9]+(?:\\.[0-9]+)?\\b)"
    )

    fun spans(text: String, fileName: String): List<Span> {
        if (text.length > MAX_CHARS) return emptyList()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts", "java", "gradle", "groovy", "js", "ts", "c", "h", "cpp", "hpp", "cs", "go", "swift", "dart", "scala", "aidl" ->
                run(codeRegex, text, listOf(Tok.COMMENT, Tok.COMMENT, Tok.STRING, Tok.ANNOTATION, Tok.NUMBER, Tok.KEYWORD))
            "xml", "html", "svg", "xhtml" ->
                run(xmlRegex, text, listOf(Tok.COMMENT, Tok.TAG, Tok.STRING, Tok.ATTRIBUTE))
            "json" ->
                run(jsonRegex, text, listOf(Tok.STRING, Tok.NUMBER, Tok.KEYWORD))
            "properties", "toml", "yml", "yaml", "ini", "conf", "pro", "sh", "cfg", "gitignore" ->
                run(hashRegex, text, listOf(Tok.COMMENT, Tok.STRING, Tok.NUMBER))
            else -> emptyList()
        }
    }

    private fun run(regex: Regex, text: String, groups: List<Tok>): List<Span> {
        val out = ArrayList<Span>()
        for (m in regex.findAll(text)) {
            val g = m.groups
            for (i in groups.indices) {
                val grp = g[i + 1]
                if (grp != null) {
                    out += Span(grp.range.first, grp.range.last + 1, groups[i])
                    break
                }
            }
        }
        return out
    }
}
