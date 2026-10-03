package dev.pocketstudio.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.core.content.FileProvider
import dev.pocketstudio.core.CertInfo
import dev.pocketstudio.core.Fingerprints
import java.io.File

/** Работа с готовыми APK/AAB: отпечатки подписи, установка, «поделиться». */
object ApkTools {

    /** Сертификаты, которыми подписан APK (поддерживает подписи v1–v3). */
    fun certificates(ctx: Context, apk: File): List<CertInfo> {
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val info = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IllegalStateException("Не удалось прочитать APK")
        val signers: Array<Signature> = info.signingInfo?.apkContentsSigners ?: legacySignatures(info) ?: emptyArray()
        if (signers.isEmpty()) throw IllegalStateException("APK не подписан")
        return signers.mapIndexed { i, s -> Fingerprints.fromDer("signer ${i + 1}", s.toByteArray()) }
    }

    @Suppress("DEPRECATION")
    private fun legacySignatures(info: PackageInfo): Array<Signature>? = info.signatures

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
