package dev.pocketstudio.core

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.security.auth.x500.X500Principal

/** Сертификат в виде, удобном для показа на экране. */
data class CertInfo(
    val alias: String,
    val hasPrivateKey: Boolean,
    val subject: String,
    val issuer: String,
    val serial: String,
    val validFrom: String,
    val validTo: String,
    val signatureAlgorithm: String,
    val md5: String,
    val sha1: String,
    val sha256: String,
) {
    /** Текст для копирования целиком. */
    fun asText(): String = buildString {
        appendLine("Alias: $alias")
        appendLine("Owner: $subject")
        appendLine("Valid: $validFrom — $validTo")
        appendLine("MD5: $md5")
        appendLine("SHA1: $sha1")
        append("SHA256: $sha256")
    }
}

object Fingerprints {
    fun hex(algorithm: String, data: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(data)
            .joinToString(":") { "%02X".format(Locale.ROOT, it) }

    fun describe(alias: String, cert: X509Certificate, hasPrivateKey: Boolean): CertInfo {
        val der = cert.encoded
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return CertInfo(
            alias = alias,
            hasPrivateKey = hasPrivateKey,
            subject = cert.subjectX500Principal.getName(X500Principal.RFC1779),
            issuer = cert.issuerX500Principal.getName(X500Principal.RFC1779),
            serial = cert.serialNumber.toString(16),
            validFrom = fmt.format(cert.notBefore),
            validTo = fmt.format(cert.notAfter),
            signatureAlgorithm = cert.sigAlgName,
            md5 = hex("MD5", der),
            sha1 = hex("SHA-1", der),
            sha256 = hex("SHA-256", der),
        )
    }

    /** Сертификат из сырых DER-байтов (например, подпись APK). */
    fun fromDer(alias: String, der: ByteArray): CertInfo {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        return describe(alias, cert, false)
    }
}
