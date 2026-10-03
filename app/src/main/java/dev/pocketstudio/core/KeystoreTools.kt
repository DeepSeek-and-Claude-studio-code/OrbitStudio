package dev.pocketstudio.core

import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.Date

object KeystoreTools {

    class KeystoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Открывает .jks / .keystore / .p12 / .pfx и возвращает сертификаты с отпечатками SHA-1 и SHA-256.
     * Для JKS пароль не нужен; для PKCS12 — нужен.
     */
    fun read(file: File, password: CharArray?): List<CertInfo> {
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            throw KeystoreException("Не удалось прочитать файл ${file.name}", e)
        }
        if (JksParser.isJks(bytes)) {
            try {
                return JksParser.parse(bytes)
                    .filter { it.chain.isNotEmpty() }
                    .map { Fingerprints.describe(it.alias, it.chain.first(), it.hasPrivateKey) }
            } catch (e: Exception) {
                throw KeystoreException("Не удалось разобрать JKS: ${e.message}", e)
            }
        }
        try {
            val store = KeyStore.getInstance("PKCS12")
            bytes.inputStream().use { store.load(it, password) }
            val result = ArrayList<CertInfo>()
            for (alias in Collections.list(store.aliases())) {
                val cert = store.getCertificate(alias) as? X509Certificate ?: continue
                result += Fingerprints.describe(alias, cert, store.isKeyEntry(alias))
            }
            return result
        } catch (e: Exception) {
            throw KeystoreException(
                "Не удалось открыть keystore: неверный пароль или неподдерживаемый формат (нужен JKS или PKCS12)", e
            )
        }
    }

    /** Создаёт новый PKCS12-keystore с ключом RSA и самоподписанным сертификатом. */
    fun generate(
        file: File,
        alias: String,
        password: CharArray,
        commonName: String,
        organization: String,
        country: String,
        validityYears: Int,
        keySize: Int = 2048,
    ): CertInfo {
        if (alias.isBlank()) throw KeystoreException("Укажите alias")
        if (password.size < 6) throw KeystoreException("Пароль должен быть не короче 6 символов")
        if (commonName.isBlank()) throw KeystoreException("Укажите имя владельца (CN)")
        if (validityYears !in 1..100) throw KeystoreException("Срок действия: от 1 до 100 лет")
        if (file.exists()) throw KeystoreException("Файл ${file.name} уже существует")
        try {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply {
                initialize(keySize, SecureRandom())
            }.generateKeyPair()

            val name = X500NameBuilder(BCStyle.INSTANCE).apply {
                addRDN(BCStyle.CN, commonName.trim())
                if (organization.isNotBlank()) addRDN(BCStyle.O, organization.trim())
                if (country.trim().length == 2) addRDN(BCStyle.C, country.trim().uppercase())
            }.build()

            val now = System.currentTimeMillis()
            val notBefore = Date(now - 60_000L)
            val notAfter = Date(now + validityYears * 365L * 24 * 3600 * 1000)
            val serial = BigInteger(64, SecureRandom()).add(BigInteger.ONE)

            val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            val cert = JcaX509CertificateConverter().getCertificate(
                JcaX509v3CertificateBuilder(name, serial, notBefore, notAfter, name, keyPair.public).build(signer)
            )

            val store = KeyStore.getInstance("PKCS12")
            store.load(null, null)
            store.setKeyEntry(alias, keyPair.private, password, arrayOf(cert))
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { store.store(it, password) }
            return Fingerprints.describe(alias, cert, true)
        } catch (e: KeystoreException) {
            throw e
        } catch (e: Exception) {
            file.delete()
            throw KeystoreException("Не удалось создать keystore: ${e.message}", e)
        }
    }
}
