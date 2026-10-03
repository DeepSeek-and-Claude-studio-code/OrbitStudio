package dev.pocketstudio.data

import android.content.Context
import android.os.Environment
import dev.pocketstudio.core.TemplateKind
import dev.pocketstudio.core.Templates
import java.io.File

/** Рабочая папка в общем хранилище: /storage/emulated/0/PocketStudio */
object Workspace {
    val root: File get() = File(Environment.getExternalStorageDirectory(), "PocketStudio")
    val projects: File get() = File(root, "projects")
    val keystores: File get() = File(root, "keystores")
    val output: File get() = File(root, "output")
    val logs: File get() = File(root, "logs")
    val scripts: File get() = File(root, ".scripts")

    private val scriptNames = listOf("ps.sh", "init.gradle")
    private val keystoreExt = setOf("jks", "keystore", "p12", "pfx", "bks")

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    /** Создаёт папки и кладёт актуальные скрипты для Termux. */
    fun ensure(context: Context) {
        for (d in listOf(projects, keystores, output, logs, scripts)) d.mkdirs()
        for (name in scriptNames) {
            val bytes = context.assets.open("scripts/$name").use { it.readBytes() }
            val dest = File(scripts, name)
            if (dest.exists() && dest.readBytes().contentEquals(bytes)) continue
            // Пишем во временный файл и переименовываем: уже запущенный bash не увидит «битый» скрипт.
            val tmp = File(scripts, "$name.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(dest)) {
                dest.writeBytes(bytes)
                tmp.delete()
            }
        }
    }

    fun listProjects(): List<File> =
        projects.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun isAndroidProject(dir: File): Boolean =
        File(dir, "settings.gradle").exists() || File(dir, "settings.gradle.kts").exists()

    fun createProject(kind: TemplateKind, name: String, pkg: String): Result<File> = runCatching {
        Templates.validate(name, pkg)?.let { error(it) }
        val dir = File(projects, name)
        if (dir.exists()) error("Проект «$name» уже существует")
        for (f in Templates.files(kind, name, pkg)) {
            val target = File(dir, f.path)
            target.parentFile?.mkdirs()
            target.writeText(f.content)
        }
        dir
    }

    fun listKeystores(): List<File> =
        keystores.listFiles { f -> f.isFile && f.extension.lowercase() in keystoreExt }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun listOutputs(project: String): List<File> =
        File(output, project).listFiles { f -> f.isFile && (f.extension == "apk" || f.extension == "aab") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
}
