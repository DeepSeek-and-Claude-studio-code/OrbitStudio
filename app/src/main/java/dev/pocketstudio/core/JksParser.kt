package dev.pocketstudio.core

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Читает сертификаты из файлов формата JKS (Java KeyStore).
 * Android не умеет открывать JKS, поэтому разбираем файл сами.
 * Сертификаты в JKS хранятся открытым текстом, пароль для них не нужен;
 * закрытые ключи мы не трогаем вообще.
 */
object JksParser {
    private const val MAGIC = -17957139 // 0xFEEDFEED

    class Entry(val alias: String, val hasPrivateKey: Boolean, val chain: List<X509Certificate>)

    fun isJks(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            (bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xFF) == 0xED &&
            (bytes[2].toInt() and 0xFF) == 0xFE && (bytes[3].toInt() and 0xFF) == 0xED

    fun parse(bytes: ByteArray): List<Entry> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        if (input.readInt() != MAGIC) throw IOException("Это не JKS-файл")
        val version = input.readInt()
        if (version != 1 && version != 2) throw IOException("Неизвестная версия JKS: $version")
        val count = input.readInt()
        if (count < 0 || count > 100_000) throw IOException("Повреждённый JKS-файл")
        val factory = CertificateFactory.getInstance("X.509")
        val result = ArrayList<Entry>()
        repeat(count) {
            val tag = input.readInt()
            val alias = input.readUTF()
            input.readLong() // дата создания
            when (tag) {
                1 -> { // запись с закрытым ключом
                    skipExactly(input, input.readInt())
                    val n = input.readInt()
                    if (n < 0 || n > 1000) throw IOException("Повреждённая цепочка сертификатов")
                    val chain = ArrayList<X509Certificate>()
                    repeat(n) { chain += readCert(input, version, factory) }
                    result += Entry(alias, true, chain)
                }
                2 -> result += Entry(alias, false, listOf(readCert(input, version, factory)))
                else -> throw IOException("Неизвестный тип записи JKS: $tag")
            }
        }
        return result
    }

    private fun readCert(input: DataInputStream, version: Int, factory: CertificateFactory): X509Certificate {
        if (version == 2) input.readUTF() // тип сертификата, обычно "X.509"
        val len = input.readInt()
        if (len < 0 || len > 10_000_000) throw IOException("Повреждённый сертификат")
        val der = ByteArray(len)
        input.readFully(der)
        return factory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    private fun skipExactly(input: DataInputStream, length: Int) {
        if (length < 0) throw IOException("Повреждённый ключ")
        var left = length
        val buf = ByteArray(4096)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size, left))
            if (n < 0) throw IOException("Файл оборван")
            left -= n
        }
    }
}
