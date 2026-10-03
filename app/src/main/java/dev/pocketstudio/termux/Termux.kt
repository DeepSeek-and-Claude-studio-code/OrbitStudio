package dev.pocketstudio.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.pocketstudio.data.Workspace
import java.io.File

/** Запуск команд в Termux через его интерфейс RUN_COMMAND. */
object Termux {
    const val PACKAGE = "com.termux"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val PREFIX = "/data/data/com.termux/files/usr"
    private const val HOME = "/data/data/com.termux/files/home"

    /** Команда, которую нужно один раз выполнить в Termux. */
    const val SETUP_COMMAND =
        "mkdir -p ~/.termux; sed -i '/^allow-external-apps/d' ~/.termux/termux.properties 2>/dev/null; " +
            "echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-reload-settings; termux-setup-storage"

    fun isInstalled(ctx: Context): Boolean =
        try {
            ctx.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun open(ctx: Context): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return true
    }

    /** Запускает `bash ps.sh <args>` в фоне внутри Termux. Ход работы читается из файлов в logs/. */
    fun runScript(ctx: Context, commandLabel: String, vararg args: String): Result<Unit> {
        if (!isInstalled(ctx)) return Result.failure(IllegalStateException("Termux не установлен"))
        if (!hasPermission(ctx)) {
            return Result.failure(IllegalStateException("Нет разрешения запускать команды в Termux (вкладка «Настройка»)"))
        }
        val script = File(Workspace.scripts, "ps.sh").absolutePath
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName(PACKAGE, "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "$PREFIX/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(script, *args))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", commandLabel)
        }
        return try {
            val started = ctx.startForegroundService(intent)
            if (started == null) Result.failure(IllegalStateException("Termux не принял команду")) else Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Termux отклонил запуск: проверьте разрешение и allow-external-apps", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
