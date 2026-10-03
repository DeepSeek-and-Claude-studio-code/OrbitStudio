package dev.pocketstudio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pocketstudio.core.TemplateKind
import dev.pocketstudio.core.Templates
import dev.pocketstudio.data.AppState
import dev.pocketstudio.data.Tab
import dev.pocketstudio.data.Workspace
import java.io.File

@Composable
fun ProjectsScreen() {
    val ctx = LocalContext.current
    val tick = AppState.tick
    val granted = remember(tick) { Workspace.hasAccess() }
    var version by remember { mutableIntStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    val projects = remember(version, tick, granted) { if (granted) Workspace.listProjects() else emptyList() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Проекты", style = MaterialTheme.typography.headlineSmall)
        if (!granted) {
            Notice("Нужен доступ ко всем файлам. Откройте вкладку «Настройка» и выдайте его.", error = true)
            Button(onClick = { AppState.tab = Tab.Setup }) { Text("Открыть «Настройка»") }
        } else {
            MonoText(Workspace.projects.absolutePath, sizeSp = 11)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showNew = true }) { Text("Новый проект") }
                OutlinedButton(onClick = { version++ }) { Text("Обновить") }
            }
            if (projects.isEmpty()) {
                Text(
                    "Пока пусто. Создайте проект или скопируйте в папку выше готовую папку Android-проекта " +
                        "(например, из архива или из репозитория).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.path }) { dir ->
                    ProjectRow(
                        dir = dir,
                        onOpen = { AppState.openProject(dir) },
                        onDelete = { toDelete = dir },
                    )
                }
            }
        }
    }

    if (showNew) {
        NewProjectDialog(
            onDismiss = { showNew = false },
            onCreated = { dir ->
                showNew = false
                version++
                toast(ctx, "Проект создан")
                AppState.openProject(dir)
            },
        )
    }

    val del = toDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить проект?") },
            text = { Text("«${del.name}» будет удалён вместе со всеми файлами. Это нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    if (AppState.project == del) {
                        AppState.editor.closeAll()
                        AppState.project = null
                    }
                    del.deleteRecursively()
                    toDelete = null
                    version++
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ProjectRow(dir: File, onOpen: () -> Unit, onDelete: () -> Unit) {
    val isProject = Workspace.isAndroidProject(dir)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(dir.name, style = MaterialTheme.typography.titleMedium)
            Text(
                (if (isProject) "Gradle-проект Android" else "Папка без settings.gradle") +
                    " · изменён " + formatDate(dir.lastModified()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text("Открыть") }
                TextButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreated: (File) -> Unit) {
    var name by remember { mutableStateOf("MyApp") }
    var pkg by remember { mutableStateOf("com.example.myapp") }
    var kind by remember { mutableStateOf(TemplateKind.JAVA) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый проект") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(name, { name = it; error = null }, "Название (латиница)")
                LabeledField(pkg, { pkg = it; error = null }, "Пакет (applicationId)")
                for (k in TemplateKind.values()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == k, onClick = { kind = k })
                        Text(k.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                error?.let { Notice(it, error = true) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = Templates.validate(name.trim(), pkg.trim())
                if (problem != null) {
                    error = problem
                } else {
                    val result = Workspace.createProject(kind, name.trim(), pkg.trim())
                    val dir = result.getOrNull()
                    if (dir != null) onCreated(dir) else error = result.exceptionOrNull()?.message ?: "Не удалось создать проект"
                }
            }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
