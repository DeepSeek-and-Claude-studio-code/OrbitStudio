package dev.pocketstudio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketstudio.core.Highlighter
import dev.pocketstudio.data.AppState
import dev.pocketstudio.data.EditorState
import dev.pocketstudio.data.OpenFile
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun EditorScreen() {
    val project = AppState.project
    if (project == null || !project.isDirectory) {
        EmptyState("Откройте или создайте проект на вкладке «Проекты».")
        return
    }
    val ed = AppState.editor
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 320.dp)) {
                FileTreePanel(
                    project = project,
                    onOpen = { file ->
                        ed.open(file)?.let { toast(ctx, it) }
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            EditorBar(onMenu = { scope.launch { drawerState.open() } })
            val cur = ed.current
            if (cur == null) {
                EmptyState("Нет открытых файлов.\nНажмите ☰ и выберите файл.")
            } else {
                CodeEditor(cur, Modifier.weight(1f).fillMaxWidth())
                SymbolBar(cur)
            }
        }
    }
}

@Composable
private fun EditorBar(onMenu: () -> Unit) {
    val ed = AppState.editor
    val ctx = LocalContext.current
    val cur = ed.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Файлы проекта") }
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState())) {
            ed.tabs.forEachIndexed { i, tab ->
                TabChip(tab, selected = i == ed.active, onClick = { ed.active = i }, onClose = { ed.close(i) })
            }
        }
        TextButton(onClick = { cur?.undo() }, enabled = (cur?.undoCount ?: 0) > 0, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("↶") }
        TextButton(onClick = { cur?.redo() }, enabled = (cur?.redoCount ?: 0) > 0, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("↷") }
        TextButton(
            onClick = {
                val err = ed.saveAll()
                toast(ctx, err ?: "Сохранено")
            },
            enabled = ed.tabs.any { it.dirty },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { Text("Сохр.") }
    }
}

@Composable
private fun TabChip(tab: OpenFile, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    drawRect(c.primary, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
                }
            }
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tab.file.name + if (tab.dirty) " •" else "",
            color = if (selected) c.onSurface else c.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        Text(
            "×",
            Modifier.clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 12.dp),
            color = c.onSurfaceVariant,
        )
    }
}

/** Подсветка синтаксиса через VisualTransformation; результат кэшируется до следующего изменения текста. */
private class HighlightTransformation(
    private val fileName: String,
    private val colors: EditorColors,
) : VisualTransformation {
    private var lastText: String? = null
    private var lastResult: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        var result = lastResult
        if (result == null || src != lastText) {
            result = buildAnnotatedString {
                append(src)
                for (s in Highlighter.spans(src, fileName)) {
                    addStyle(SpanStyle(color = colors.forToken(s.tok)), s.start, s.end)
                }
            }
            lastText = src
            lastResult = result
        }
        return TransformedText(result, OffsetMapping.Identity)
    }
}

@Composable
private fun CodeEditor(file: OpenFile, modifier: Modifier) {
    val colors = LocalEditorColors.current
    val textStyle = remember(colors) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp, color = colors.text)
    }
    val gutterStyle = remember(colors) { textStyle.copy(color = colors.gutterText, textAlign = TextAlign.End) }
    val transformation = remember(file.file.name, colors) { HighlightTransformation(file.file.name, colors) }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val value = file.value
    val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val gutter = remember(lineCount) { (1..lineCount).joinToString("\n") }
    var gutterPx by remember { mutableIntStateOf(0) }
    val gutterDp = with(LocalDensity.current) { gutterPx.toDp() }

    BoxWithConstraints(modifier.background(colors.bg)) {
        val minH = maxHeight
        val minW = (maxWidth - gutterDp).coerceAtLeast(0.dp)
        Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            Text(
                gutter,
                style = gutterStyle,
                modifier = Modifier
                    .defaultMinSize(minHeight = minH)
                    .background(colors.gutterBg)
                    .onSizeChanged { gutterPx = it.width }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                BasicTextField(
                    value = value,
                    onValueChange = { file.edit(it) },
                    textStyle = textStyle,
                    cursorBrush = SolidColor(colors.caret),
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.defaultMinSize(minWidth = minW, minHeight = minH).padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun SymbolBar(file: OpenFile) {
    val symbols = listOf(
        "\t" to "Tab", "{" to "{", "}" to "}", "(" to "(", ")" to ")", "[" to "[", "]" to "]",
        ";" to ";", "\"" to "\"", "'" to "'", "=" to "=", "<" to "<", ">" to ">", "/" to "/",
        ":" to ":", "!" to "!", "&" to "&", "|" to "|", "@" to "@", "\$" to "\$", "#" to "#",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .horizontalScroll(rememberScrollState()),
    ) {
        for ((insert, label) in symbols) {
            TextButton(
                onClick = { file.insert(if (insert == "\t") "    " else insert) },
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.defaultMinSize(minWidth = 36.dp),
            ) { Text(label, fontFamily = FontFamily.Monospace) }
        }
    }
}

// ───────────────────────── дерево файлов ─────────────────────────

private sealed class FileOp {
    class NewFile(val dir: File) : FileOp()
    class NewFolder(val dir: File) : FileOp()
    class Rename(val file: File) : FileOp()
    class Delete(val file: File) : FileOp()
}

private fun flatten(root: File, expanded: Map<String, Boolean>): List<Pair<File, Int>> {
    val out = ArrayList<Pair<File, Int>>()
    fun walk(dir: File, depth: Int) {
        val children = dir.listFiles()
            ?.filter { it.name != ".gradle" && it.name != ".git" }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?: return
        for (c in children) {
            out += c to depth
            if (c.isDirectory && expanded[c.path] == true) walk(c, depth + 1)
        }
    }
    walk(root, 0)
    return out
}

/** Выполняет создание/переименование. Возвращает текст ошибки или null. */
private fun runFileOp(op: FileOp, rawName: String, ed: EditorState): String? {
    val name = rawName.trim()
    if (name.isEmpty()) return "Введите имя"
    if (name.contains('/') || name.contains('\\')) return "Имя не должно содержать / или \\"
    val target = when (op) {
        is FileOp.NewFile -> File(op.dir, name)
        is FileOp.NewFolder -> File(op.dir, name)
        is FileOp.Rename -> File(op.file.parentFile, name)
        is FileOp.Delete -> return null
    }
    if (target.exists()) return "Такое имя уже есть"
    val ok = try {
        when (op) {
            is FileOp.NewFile -> target.createNewFile()
            is FileOp.NewFolder -> target.mkdirs()
            is FileOp.Rename -> {
                ed.closeUnder(op.file)
                op.file.renameTo(target)
            }
            is FileOp.Delete -> true
        }
    } catch (e: Exception) {
        false
    }
    if (!ok) return "Не получилось"
    if (op is FileOp.NewFile) ed.open(target)
    return null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreePanel(project: File, onOpen: (File) -> Unit) {
    val expanded = remember(project) { mutableStateMapOf<String, Boolean>() }
    var version by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<File?>(null) }
    var op by remember { mutableStateOf<FileOp?>(null) }
    val snapshot = expanded.toMap()
    val rows = remember(project, version, snapshot) { flatten(project, snapshot) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(project.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { op = FileOp.NewFile(project) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+ файл") }
            TextButton(onClick = { op = FileOp.NewFolder(project) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+ папка") }
        }
        Text(
            "Долгое нажатие — меню файла",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(top = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.first.path }) { (file, depth) ->
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (file.isDirectory) expanded[file.path] = expanded[file.path] != true else onOpen(file)
                                },
                                onLongClick = { menuFor = file },
                            )
                            .padding(start = (12 + depth * 16).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (file.isDirectory) (if (expanded[file.path] == true) "▾ " else "▸ ") else "   ",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (file.isDirectory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuFor == file, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(text = { Text("Новый файл") }, onClick = {
                            menuFor = null
                            op = FileOp.NewFile(if (file.isDirectory) file else (file.parentFile ?: project))
                        })
                        DropdownMenuItem(text = { Text("Новая папка") }, onClick = {
                            menuFor = null
                            op = FileOp.NewFolder(if (file.isDirectory) file else (file.parentFile ?: project))
                        })
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = {
                            menuFor = null
                            op = FileOp.Rename(file)
                        })
                        DropdownMenuItem(text = { Text("Удалить") }, onClick = {
                            menuFor = null
                            op = FileOp.Delete(file)
                        })
                    }
                }
            }
        }
    }

    val current = op
    if (current != null) {
        FileOpDialog(
            op = current,
            onDismiss = { op = null },
            onDone = {
                op = null
                version++
            },
        )
    }
}

@Composable
private fun FileOpDialog(op: FileOp, onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val ed = AppState.editor
    if (op is FileOp.Delete) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Удалить?") },
            text = { Text(op.file.name + if (op.file.isDirectory) " (папка со всем содержимым)" else "") },
            confirmButton = {
                TextButton(onClick = {
                    ed.closeUnder(op.file)
                    op.file.deleteRecursively()
                    onDone()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        )
        return
    }
    var name by remember { mutableStateOf(if (op is FileOp.Rename) op.file.name else "") }
    val title = when (op) {
        is FileOp.NewFile -> "Новый файл"
        is FileOp.NewFolder -> "Новая папка"
        else -> "Переименовать"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Имя") }) },
        confirmButton = {
            TextButton(onClick = {
                val error = runFileOp(op, name, ed)
                if (error != null) toast(ctx, error) else onDone()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
