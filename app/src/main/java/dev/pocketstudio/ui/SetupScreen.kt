package dev.pocketstudio.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.pocketstudio.data.AppState
import dev.pocketstudio.data.Workspace
import dev.pocketstudio.termux.Runner
import dev.pocketstudio.termux.Termux
import java.io.File

@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val tick = AppState.tick
    val finished = Runner.finishedCount
    val storageOk = remember(tick) { Workspace.hasAccess() }
    val termuxInstalled = remember(tick) { Termux.isInstalled(ctx) }
    val termuxPermission = remember(tick) { Termux.hasPermission(ctx) }
    val toolchainOk = remember(tick, finished, storageOk) { storageOk && File(Workspace.logs, "toolchain.ok").exists() }
    var accept by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { AppState.tick++ }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройка", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Редактор, ключи и SHA работают сами по себе. Для сборки APK/AAB PocketStudio запускает настоящий Gradle " +
                "внутри Termux, поэтому нужно один раз всё подготовить.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section("Состояние") {
            StatusLine(storageOk, "Доступ ко всем файлам")
            StatusLine(termuxInstalled, "Termux установлен")
            StatusLine(termuxPermission, "Разрешение запускать команды в Termux")
            StatusLine(toolchainOk, "Инструменты сборки (JDK 17, Gradle, Android SDK)")
        }

        Section("1. Доступ ко всем файлам") {
            Text(
                "Проекты, ключи и готовые APK лежат в папке PocketStudio во внутренней памяти: так их видит и Termux.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)),
                    )
                } catch (e: Exception) {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (e2: Exception) {
                        toast(ctx, "Не удалось открыть настройки")
                    }
                }
            }, enabled = !storageOk) { Text(if (storageOk) "Выдано" else "Выдать доступ") }
        }

        Section("2. Termux") {
            Text(
                "Установите Termux из F-Droid или с GitHub. Версия из Google Play не подойдёт: она устарела и не принимает команды от других приложений.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openUrl(ctx, "https://f-droid.org/packages/com.termux/") }) { Text("F-Droid") }
                OutlinedButton(onClick = { openUrl(ctx, "https://github.com/termux/termux-app/releases") }) { Text("GitHub") }
            }
            if (termuxInstalled) {
                TextButton(onClick = { if (!Termux.open(ctx)) toast(ctx, "Не удалось открыть Termux") }) { Text("Открыть Termux") }
            }
        }

        Section("3. Разрешение для PocketStudio") {
            Button(
                onClick = { permissionLauncher.launch(Termux.PERMISSION) },
                enabled = termuxInstalled && !termuxPermission,
            ) { Text(if (termuxPermission) "Выдано" else "Разрешить запуск команд в Termux") }
            if (!termuxInstalled) {
                Text("Сначала установите Termux.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Section("4. Один раз настройте сам Termux") {
            Text("Откройте Termux, вставьте эту команду и подтвердите доступ к файлам, когда Android спросит:", style = MaterialTheme.typography.bodySmall)
            SelectionContainer { MonoText(Termux.SETUP_COMMAND, sizeSp = 11) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(Termux.SETUP_COMMAND))
                    toast(ctx, "Команда скопирована")
                }) { Text("Копировать") }
                if (termuxInstalled) {
                    OutlinedButton(onClick = { Termux.open(ctx) }) { Text("Открыть Termux") }
                }
            }
        }

        Section("5. Проверка связи") {
            Text("Приложение попросит Termux отозваться и проверит доступ к папке PocketStudio.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { Runner.start(ctx, "Проверка связи", "ping") }, enabled = !Runner.busy) { Text("Проверить") }
        }

        Section("6. Инструменты сборки") {
            Text(
                "Будут установлены: JDK 17 и aapt2 из пакетов Termux, Gradle 8.7, Android SDK (platform 34, build-tools 34.0.0). " +
                    "Это примерно 1–1,5 ГБ трафика; первая сборка проекта скачает ещё несколько сотен МБ зависимостей. " +
                    "Установка занимает 10–30 минут, телефон лучше держать на зарядке и в Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accept, onCheckedChange = { accept = it })
                Text("Принимаю лицензионное соглашение Android SDK", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { openUrl(ctx, "https://developer.android.com/studio/terms") }) { Text("Прочитать условия") }
            Button(
                onClick = { Runner.start(ctx, "Установка инструментов", "setup", if (accept) "1" else "0") },
                enabled = !Runner.busy && (accept || toolchainOk),
            ) { Text(if (toolchainOk) "Переустановить / обновить" else "Установить") }
        }

        LogPanel()
    }
}
