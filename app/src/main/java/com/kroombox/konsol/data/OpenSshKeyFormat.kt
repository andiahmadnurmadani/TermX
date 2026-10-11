package com.kroombox.konsol.data

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Penulis format kunci OpenSSH ("openssh-key-v1", tanpa enkripsi) dan baris public key.
 * Murni Kotlin/JDK supaya bisa diuji di JVM. Kunci privat disimpan terenkripsi oleh [SecretBox].
 */
object OpenSshKeyFormat {

    private class SshBuf {
        val out = ByteArrayOutputStream()
        fun u32(v: Int) {
            out.write((v ushr 24) and 0xFF); out.write((v ushr 16) and 0xFF)
            out.write((v ushr 8) and 0xFF); out.write(v and 0xFF)
        }
        fun bytes(b: ByteArray) { u32(b.size); out.write(b) }
        fun str(s: String) = bytes(s.toByteArray(Charsets.UTF_8))
        fun mpint(v: BigInteger) = bytes(if (v.signum() == 0) ByteArray(0) else v.toByteArray())
        fun raw(b: ByteArray) = out.write(b)
        fun toBytes(): ByteArray = out.toByteArray()
    }

    fun ed25519PublicBlob(pub: ByteArray): ByteArray = SshBuf().apply {
        str("ssh-ed25519"); bytes(pub)
    }.toBytes()

    fun rsaPublicBlob(e: BigInteger, n: BigInteger): ByteArray = SshBuf().apply {
        str("ssh-rsa"); mpint(e); mpint(n)
    }.toBytes()

    fun publicLine(type: String, blob: ByteArray, comment: String): String =
        "$type ${Base64.getEncoder().encodeToString(blob)}" + (if (comment.isNotBlank()) " $comment" else "")

    fun fingerprintSha256(blob: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(d)
    }

    /** Ambil blob dari baris "ssh-xxx AAAA... komentar". */
    fun blobFromPublicLine(line: String): ByteArray? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        return try { Base64.getDecoder().decode(parts[1]) } catch (_: IllegalArgumentException) { null }
    }

    fun ed25519Private(seed: ByteArray, pub: ByteArray, comment: String): String {
        require(seed.size == 32 && pub.size == 32)
        val pubBlob = ed25519PublicBlob(pub)
        val priv = SshBuf()
        val check = SecureRandom().nextInt()
        priv.u32(check); priv.u32(check)
        priv.str("ssh-ed25519")
        priv.bytes(pub)
        priv.bytes(seed + pub)
        priv.str(comment)
        return wrap(pubBlob, priv)
    }

    fun rsaPrivate(
        n: BigInteger, e: BigInteger, d: BigInteger, iqmp: BigInteger, p: BigInteger, q: BigInteger, comment: String,
    ): String {
        val pubBlob = rsaPublicBlob(e, n)
        val priv = SshBuf()
        val check = SecureRandom().nextInt()
        priv.u32(check); priv.u32(check)
        priv.str("ssh-rsa")
        priv.mpint(n); priv.mpint(e); priv.mpint(d); priv.mpint(iqmp); priv.mpint(p); priv.mpint(q)
        priv.str(comment)
        return wrap(pubBlob, priv)
    }

    private fun wrap(pubBlob: ByteArray, priv: SshBuf): String {
        var privBytes = priv.toBytes()
        var pad = 1
        while (privBytes.size % 8 != 0) {
            privBytes += pad.toByte()
            pad++
        }
        val all = SshBuf()
        all.raw("openssh-key-v1".toByteArray(Charsets.US_ASCII) + byteArrayOf(0))
        all.str("none")
        all.str("none")
        all.str("")
        all.u32(1)
        all.bytes(pubBlob)
        all.bytes(privBytes)
        val b64 = Base64.getEncoder().encodeToString(all.toBytes())
        return buildString {
            append("-----BEGIN OPENSSH PRIVATE KEY-----\n")
            b64.chunked(70).forEach { append(it).append('\n') }
            append("-----END OPENSSH PRIVATE KEY-----\n")
        }
    }

    /** Baca blob public key dari header kunci OpenSSH v1 (berlaku juga untuk kunci terenkripsi). */
    fun publicBlobFromOpenSshPrivate(pem: String): ByteArray? {
        val body = pem.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("-----") }
            .joinToString("")
        val data = try { Base64.getDecoder().decode(body) } catch (_: IllegalArgumentException) { return null }
        val magic = "openssh-key-v1".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        if (data.size < magic.size || !data.copyOfRange(0, magic.size).contentEquals(magic)) return null
        var pos = magic.size
        fun u32(): Int {
            if (pos + 4 > data.size) throw IllegalArgumentException("truncated")
            val v = ((data[pos].toInt() and 0xFF) shl 24) or ((data[pos + 1].toInt() and 0xFF) shl 16) or
                ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
            pos += 4
            return v
        }
        fun bytes(): ByteArray {
            val len = u32()
            if (len < 0 || pos + len > data.size) throw IllegalArgumentException("truncated")
            val b = data.copyOfRange(pos, pos + len)
            pos += len
            return b
        }
        return try {
            bytes(); bytes(); bytes() // cipher, kdf, kdfoptions
            if (u32() != 1) return null
            bytes()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Nama tipe dari blob public key (mis. "ssh-ed25519"). */
    fun typeOfBlob(blob: ByteArray): String? {
        if (blob.size < 4) return null
        val len = ((blob[0].toInt() and 0xFF) shl 24) or ((blob[1].toInt() and 0xFF) shl 16) or
            ((blob[2].toInt() and 0xFF) shl 8) or (blob[3].toInt() and 0xFF)
        if (len <= 0 || 4 + len > blob.size) return null
        return String(blob, 4, len, Charsets.US_ASCII)
    }
}
