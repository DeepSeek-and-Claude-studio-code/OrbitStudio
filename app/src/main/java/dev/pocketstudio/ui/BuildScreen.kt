package dev.pocketstudio.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import dev.pocketstudio.core.CertInfo
import dev.pocketstudio.core.KeystoreTools
import dev.pocketstudio.data.ApkTools
import dev.pocketstudio.data.AppState
import dev.pocketstudio.data.Workspace
import dev.pocketstudio.termux.Runner
import java.io.File

private const val NONE = "__none__"

private fun startGradle(ctx: Context, project: File, mode: String, keystore: File?, skipLint: Boolean) {
    val s = AppState.signing
    if (keystore != null && (s.alias.isBlank() || s.storePass.isEmpty())) {
        toast(ctx, "Укажите alias и пароль keystore")
        return
    }
    AppState.editor.saveAll()
    val useKs = keystore != null
    Runner.start(
        ctx, "$mode · ${project.name}", "build", project.name, mode,
        keystore?.absolutePath ?: NONE,
        if (useKs) s.alias.trim() else "-",
        if (useKs) s.storePass else "-",
        if (useKs && s.keyPass.isNotEmpty()) s.keyPass else NONE,
        if (skipLint) "1" else "0",
    )
}

@Composable
fun BuildScreen() {
    val ctx = LocalContext.current
    val project = AppState.project
    val tick = AppState.tick
    val finished = Runner.finishedCount
    val signing = AppState.signing
    var mode by remember { mutableIntStateOf(0) }
    var skipLint by remember { mutableStateOf(true) }
    var keyInfo by remember { mutableStateOf<List<CertInfo>>(emptyList()) }
    var keyError by remember { mutableStateOf<String?>(null) }
    var apkInfo by remember { mutableStateOf<Pair<String, List<CertInfo>>?>(null) }
    val granted = remember(tick) { Workspace.hasAccess() }
    val keystores = remember(tick, finished, granted) { if (granted) Workspace.listKeystores() else emptyList() }
    val outputs = remember(project, tick, finished, granted) {
        if (project != null && granted) Workspace.listOutputs(project.name) else emptyList()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Сборка", style = MaterialTheme.typography.headlineSmall)
        if (project == null) {
            Notice("Сначала откройте проект на вкладке «Проекты».")
        } else {
            Section("Проект") {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                MonoText(project.absolutePath, sizeSp = 11)
            }

            Section("Что собрать") {
                val modes = listOf(
                    "Debug APK" to "для проверки на телефоне, подписывается отладочным ключом",
                    "Release APK" to "готовый к установке файл, подписывается вашим ключом",
                    "Release AAB" to "пакет для Google Play, подписывается вашим ключом",
                )
                modes.forEachIndexed { i, (title, hint) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == i, onClick = { mode = i })
                        Column {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = skipLint, onCheckedChange = { skipLint = it })
                    Text("Пропустить lint в release (быстрее, меньше памяти)", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (mode > 0) {
                Section("Подпись") {
                    if (keystores.isEmpty()) {
                        Notice("Нет ни одного keystore. Создайте его на вкладке «Ключи» или скопируйте файл в ${Workspace.keystores.path}. Без ключа release-файл будет неподписанным.")
                    } else {
                        keystores.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = signing.keystore == f, onClick = {
                                    signing.keystore = f
                                    keyInfo = emptyList()
                                    keyError = null
                                    if (f.name == "debug.keystore") {
                                        signing.alias = "androiddebugkey"
                                        signing.storePass = "android"
                                        signing.keyPass = ""
                                    }
                                })
                                Text(f.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        TextButton(onClick = { signing.keystore = null }) { Text("Не подписывать") }
                        LabeledField(signing.alias, { signing.alias = it }, "Alias ключа")
                        PasswordField(signing.storePass, { signing.storePass = it }, "Пароль keystore")
                        PasswordField(signing.keyPass, { signing.keyPass = it }, "Пароль ключа (если отличается)")
                        val ks = signing.keystore
                        OutlinedButton(
                            enabled = ks != null,
                            onClick = {
                                keyError = null
                                keyInfo = emptyList()
                                if (ks != null) {
                                    try {
                                        val list = KeystoreTools.read(ks, signing.storePass.toCharArray())
                                        keyInfo = list
                                        if (signing.alias.isBlank()) list.firstOrNull { it.hasPrivateKey }?.let { signing.alias = it.alias }
                                    } catch (e: KeystoreTools.KeystoreException) {
                                        keyError = e.message
                                    }
                                }
                            },
                        ) { Text("Проверить ключ и показать SHA") }
                        keyError?.let { Notice(it, error = true) }
                        keyInfo.forEach { CertCard(it) }
                        Text(
                            "Пароли хранятся только в памяти приложения и передаются в Termux в момент сборки.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val modeArg = when (mode) {
                0 -> "debug"
                1 -> "release-apk"
                else -> "release-aab"
            }
            Button(
                onClick = { startGradle(ctx, project, modeArg, if (mode > 0) signing.keystore else null, skipLint) },
                enabled = !Runner.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Собрать") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { startGradle(ctx, project, "signing-report", null, true) },
                    enabled = !Runner.busy,
                ) { Text("SHA через Gradle") }
                OutlinedButton(
                    onClick = { startGradle(ctx, project, "clean", null, true) },
                    enabled = !Runner.busy,
                ) { Text("Очистить (clean)") }
            }

            LogPanel()

            if (outputs.isNotEmpty()) {
                Section("Готовые файлы") {
                    MonoText(File(Workspace.output, project.name).path, sizeSp = 11)
                    outputs.forEach { f ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                formatSize(f.length()) + " · " + formatDate(f.lastModified()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (f.extension == "apk") {
                                    TextButton(onClick = {
                                        try {
                                            ApkTools.install(ctx, f)
                                        } catch (e: Exception) {
                                            toast(ctx, "Не удалось начать установку: ${e.message}")
                                        }
                                    }) { Text("Установить") }
                                }
                                TextButton(onClick = {
                                    try {
                                        ApkTools.share(ctx, f)
                                    } catch (e: Exception) {
                                        toast(ctx, "Не удалось поделиться: ${e.message}")
                                    }
                                }) { Text("Поделиться") }
                                if (f.extension == "apk") {
                                    TextButton(onClick = {
                                        try {
                                            apkInfo = f.name to ApkTools.certificates(ctx, f)
                                        } catch (e: Exception) {
                                            apkInfo = null
                                            toast(ctx, e.message ?: "Не удалось прочитать подпись")
                                        }
                                    }) { Text("SHA") }
                                }
                            }
                        }
                    }
                    apkInfo?.let { (name, certs) ->
                        Text("Подпись $name", style = MaterialTheme.typography.titleSmall)
                        certs.forEach { CertCard(it) }
                        TextButton(onClick = { apkInfo = null }) { Text("Скрыть") }
                    }
                }
            }
        }
    }
}
