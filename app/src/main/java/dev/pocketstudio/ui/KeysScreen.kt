package dev.pocketstudio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pocketstudio.core.CertInfo
import dev.pocketstudio.core.JksParser
import dev.pocketstudio.core.KeystoreTools
import dev.pocketstudio.data.ApkTools
import dev.pocketstudio.data.AppState
import dev.pocketstudio.data.Workspace
import dev.pocketstudio.termux.Runner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun looksLikeJks(file: File): Boolean = try {
    file.inputStream().use {
        val head = ByteArray(4)
        it.read(head) == 4 && JksParser.isJks(head)
    }
} catch (e: Exception) {
    false
}

@Composable
fun KeysScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = AppState.tick
    val finished = Runner.finishedCount
    var refresh by remember { mutableIntStateOf(0) }
    val granted = remember(tick) { Workspace.hasAccess() }
    val files = remember(tick, finished, refresh, granted) { if (granted) Workspace.listKeystores() else emptyList() }

    var askFor by remember { mutableStateOf<File?>(null) }
    var askPass by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf<Pair<String, List<CertInfo>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<File?>(null) }

    var fileName by remember { mutableStateOf("release.p12") }
    var alias by remember { mutableStateOf("release") }
    var pass by remember { mutableStateOf("") }
    var cn by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("RU") }
    var years by remember { mutableStateOf("25") }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf<CertInfo?>(null) }

    fun showSha(file: File, password: String?) {
        try {
            shown = file.name to KeystoreTools.read(file, password?.toCharArray())
            error = null
        } catch (e: KeystoreTools.KeystoreException) {
            shown = null
            error = e.message
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val tmp = File(ctx.cacheDir, "picked.apk")
                        val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Не удалось открыть файл")
                        input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }
                        try {
                            ApkTools.certificates(ctx, tmp)
                        } finally {
                            tmp.delete()
                        }
                    }
                }
                result.onSuccess {
                    shown = "APK" to it
                    error = null
                }.onFailure {
                    shown = null
                    error = it.message ?: "Не удалось прочитать подпись APK"
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ключи и SHA", style = MaterialTheme.typography.headlineSmall)
        if (!granted) Notice("Нужен доступ ко всем файлам — вкладка «Настройка».", error = true)

        error?.let { Notice(it, error = true) }
        shown?.let { (title, certs) ->
            Section("SHA: $title") {
                certs.forEach { CertCard(it) }
                TextButton(onClick = { shown = null }) { Text("Скрыть") }
            }
        }

        Section("Keystore в папке keystores") {
            MonoText(Workspace.keystores.path, sizeSp = 11)
            if (files.isEmpty()) {
                Text(
                    "Пока пусто. Создайте ключ ниже или скопируйте сюда свой .jks / .keystore / .p12.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            files.forEach { f ->
                Column {
                    Text(f.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        formatSize(f.length()) + " · " + formatDate(f.lastModified()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            if (looksLikeJks(f)) {
                                showSha(f, null)
                            } else {
                                askPass = if (f.name == "debug.keystore") "android" else ""
                                askFor = f
                            }
                        }) { Text("Показать SHA") }
                        TextButton(onClick = { toDelete = f }) { Text("Удалить") }
                    }
                }
            }
        }

        Section("Debug-ключ Gradle") {
            Text(
                "Тот самый ~/.android/debug.keystore, которым Gradle подписывает debug-сборки. " +
                    "Копия появится в списке выше (пароль android, alias androiddebugkey). Нужен установленный JDK.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { Runner.start(ctx, "Debug-ключ", "debug-key") },
                enabled = !Runner.busy,
            ) { Text("Получить debug.keystore") }
        }

        Section("Новый keystore") {
            LabeledField(fileName, { fileName = it }, "Имя файла")
            LabeledField(alias, { alias = it }, "Alias")
            PasswordField(pass, { pass = it }, "Пароль (не короче 6 символов)")
            LabeledField(cn, { cn = it }, "Имя владельца (CN)")
            LabeledField(org, { org = it }, "Организация (необязательно)")
            LabeledField(country, { country = it }, "Страна, 2 буквы (необязательно)")
            LabeledField(years, { years = it }, "Срок действия, лет", keyboardType = KeyboardType.Number)
            Button(
                enabled = !creating,
                onClick = {
                    val name = fileName.trim()
                    if (name.isEmpty() || name.contains('/') || name.contains('\\')) {
                        createError = "Некорректное имя файла"
                    } else {
                        val withExt = if (name.substringAfterLast('.', "").lowercase() in setOf("p12", "pfx", "jks", "keystore")) name else "$name.p12"
                        scope.launch {
                            creating = true
                            createError = null
                            created = null
                            val result = withContext(Dispatchers.Default) {
                                runCatching {
                                    Workspace.keystores.mkdirs()
                                    KeystoreTools.generate(File(Workspace.keystores, withExt), alias.trim(), pass.toCharArray(), cn, org, country, years.toIntOrNull() ?: 0)
                                }
                            }
                            creating = false
                            result.onSuccess {
                                created = it
                                refresh++
                            }.onFailure { createError = it.message ?: "Не удалось создать keystore" }
                        }
                    }
                },
            ) { Text(if (creating) "Создаю…" else "Создать") }
            createError?.let { Notice(it, error = true) }
            created?.let {
                Notice("Готово. Сохраните файл и пароль в надёжном месте: потеряв ключ, вы не сможете обновлять приложение в Google Play.")
                CertCard(it)
            }
        }

        Section("SHA из APK") {
            Text(
                "Покажет отпечатки сертификата, которым подписан любой APK на телефоне (в том числе чужой).",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Выбрать APK…") }
            Text(
                "Готовые сборки проекта можно проверить на вкладке «Сборка».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LogPanel()
    }

    val ask = askFor
    if (ask != null) {
        AlertDialog(
            onDismissRequest = { askFor = null },
            title = { Text("Пароль для ${ask.name}") },
            text = { PasswordField(askPass, { askPass = it }, "Пароль keystore") },
            confirmButton = {
                TextButton(onClick = {
                    showSha(ask, askPass)
                    askFor = null
                }) { Text("Показать") }
            },
            dismissButton = { TextButton(onClick = { askFor = null }) { Text("Отмена") } },
        )
    }

    val del = toDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить ${del.name}?") },
            text = { Text("Если это единственная копия ключа, подписывать обновления приложения будет нечем.") },
            confirmButton = {
                TextButton(onClick = {
                    del.delete()
                    if (AppState.signing.keystore == del) AppState.signing.keystore = null
                    toDelete = null
                    refresh++
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}
