package dev.pocketstudio.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketstudio.core.CertInfo
import dev.pocketstudio.termux.RunState
import dev.pocketstudio.termux.Runner
import java.text.DateFormat
import java.util.Date

fun toast(ctx: Context, message: String) {
    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast(ctx, "Не удалось открыть ссылку")
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f МБ", bytes / 1048576.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f КБ", bytes / 1024.0)
    else -> "$bytes Б"
}

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

/** Блок с рамкой, как лист чертежа: без теней, группировка выражена линией. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun Notice(text: String, error: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (error) c.errorContainer else c.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (error) c.onErrorContainer else c.onSecondaryContainer,
        )
    }
}

@Composable
fun StatusLine(ok: Boolean, text: String) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Text(if (ok) "✓ " else "✗ ", color = if (ok) c.tertiary else c.error, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, sizeSp: Int = 12) {
    Text(
        text,
        modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp + 5).sp,
    )
}

@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun FingerprintRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Box(Modifier.weight(1f))
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(value))
                toast(ctx, "$label скопирован")
            }) { Text("Копировать") }
        }
        SelectionContainer { MonoText(value, sizeSp = 11) }
    }
}

/** Карточка сертификата: владелец, срок действия, отпечатки SHA-1 / SHA-256 / MD5 с кнопками «Копировать». */
@Composable
fun CertCard(cert: CertInfo) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Section(title = "Alias: ${cert.alias}") {
        Text(cert.subject, style = MaterialTheme.typography.bodySmall)
        Text(
            "Действителен: ${cert.validFrom} — ${cert.validTo}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FingerprintRow("SHA-1", cert.sha1)
        FingerprintRow("SHA-256", cert.sha256)
        FingerprintRow("MD5", cert.md5)
        OutlinedButton(onClick = {
            clipboard.setText(AnnotatedString(cert.asText()))
            toast(ctx, "Скопировано")
        }) { Text("Копировать всё") }
    }
}

/** Ход выполнения задачи в Termux: статус, журнал, кнопки «Остановить» и «Сбросить». */
@Composable
fun LogPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state = Runner.state
    val log = Runner.log
    val problem = Runner.problem
    if (state == RunState.IDLE && log.isEmpty() && problem == null) return
    val colors = LocalEditorColors.current

    Section(title = "Termux: " + Runner.label, modifier = modifier) {
        if (problem != null) Notice(problem, error = true)
        when (state) {
            RunState.STARTING -> Text("Запускаю в Termux…", style = MaterialTheme.typography.bodyMedium)
            RunState.RUNNING -> Text("Выполняется. Можно свернуть приложение — Termux продолжит работу.", style = MaterialTheme.typography.bodyMedium)
            RunState.DONE ->
                if (Runner.exitCode == 0) StatusLine(true, "Готово")
                else StatusLine(false, "Завершено с ошибкой (код ${Runner.exitCode})")
            RunState.IDLE -> {}
        }
        if (Runner.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        val scroll = rememberScrollState()
        LaunchedEffect(log) { scroll.scrollTo(scroll.maxValue) }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .background(colors.bg)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .verticalScroll(scroll)
                .padding(8.dp)
        ) {
            SelectionContainer {
                Text(
                    log.ifEmpty { "…" },
                    color = colors.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (Runner.busy) {
                OutlinedButton(onClick = { Runner.stop(ctx) }) { Text("Остановить") }
                TextButton(onClick = { Runner.forceReset() }) { Text("Сбросить статус") }
            }
            if (log.isNotEmpty()) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(log))
                    toast(ctx, "Журнал скопирован")
                }) { Text("Копировать журнал") }
            }
        }
    }
}
