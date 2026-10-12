package com.kroombox.termx.data

import androidx.annotation.StringRes
import com.kroombox.termx.R
import com.kroombox.termx.i18n.S

import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey

/** PasswordFinder sederhana untuk passphrase kunci. */
class StaticPasswordFinder(private val password: String?) : PasswordFinder {
    override fun reqPassword(resource: Resource<*>?): CharArray = (password ?: "").toCharArray()
    override fun shouldRetry(resource: Resource<*>?): Boolean = false
}

object KeyManager {

    enum class GenType(@StringRes val label: Int) {
        ED25519(R.string.keys_type_ed25519), RSA4096(R.string.keys_type_rsa4096), RSA2048(R.string.keys_type_rsa2048)
    }

    fun generate(name: String, type: GenType, comment: String): SshKey {
        return when (type) {
            GenType.ED25519 -> {
                val gen = Ed25519KeyPairGenerator()
                gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
                val kp = gen.generateKeyPair()
                val priv = (kp.private as Ed25519PrivateKeyParameters).encoded
                val pub = (kp.public as Ed25519PublicKeyParameters).encoded
                val blob = OpenSshKeyFormat.ed25519PublicBlob(pub)
                SshKey(
                    name = name,
                    type = "ed25519",
                    publicKey = OpenSshKeyFormat.publicLine("ssh-ed25519", blob, comment),
                    privateKeyEnc = SecretBox.encrypt(OpenSshKeyFormat.ed25519Private(priv, pub, comment)),
                    fingerprint = OpenSshKeyFormat.fingerprintSha256(blob),
                )
            }
            GenType.RSA4096, GenType.RSA2048 -> {
                val g = KeyPairGenerator.getInstance("RSA")
                g.initialize(if (type == GenType.RSA4096) 4096 else 2048, SecureRandom())
                val kp = g.generateKeyPair()
                val p = kp.private as RSAPrivateCrtKey
                val blob = OpenSshKeyFormat.rsaPublicBlob(p.publicExponent, p.modulus)
                SshKey(
                    name = name,
                    type = "rsa",
                    publicKey = OpenSshKeyFormat.publicLine("ssh-rsa", blob, comment),
                    privateKeyEnc = SecretBox.encrypt(
                        OpenSshKeyFormat.rsaPrivate(
                            p.modulus, p.publicExponent, p.privateExponent, p.crtCoefficient, p.primeP, p.primeQ, comment,
                        ),
                    ),
                    fingerprint = OpenSshKeyFormat.fingerprintSha256(blob),
                )
            }
        }
    }

    /**
     * Impor kunci privat (OpenSSH, PEM/PKCS#1/PKCS#8, PuTTY). Kunci dibaca dengan sshj untuk
     * memastikan valid dan passphrase benar.
     */
    fun import(name: String, privateKey: String, passphrase: String?): SshKey {
        val text = privateKey.trim().replace("\r\n", "\n") + "\n"
        val format = KeyProviderUtil.detectKeyFileFormat(text, false)
        val provider = Factory.Named.Util.create(DefaultConfig().fileKeyProviderFactories, format.toString())
            ?: throw IllegalArgumentException(S.get(R.string.keys_err_unsupported_format, format.toString()))
        provider.init(text, null, StaticPasswordFinder(passphrase))
        // Memanggil private memastikan passphrase benar.
        provider.private
        val pub = provider.public
        val blob = Buffer.PlainBuffer().putPublicKey(pub).compactData
        val typeName = OpenSshKeyFormat.typeOfBlob(blob) ?: provider.type.toString()
        return SshKey(
            name = name,
            type = typeName,
            publicKey = OpenSshKeyFormat.publicLine(typeName, blob, name.replace(' ', '_')),
            privateKeyEnc = SecretBox.encrypt(text),
            passphraseEnc = passphrase?.takeIf { it.isNotEmpty() }?.let { SecretBox.encrypt(it) },
            fingerprint = OpenSshKeyFormat.fingerprintSha256(blob),
        )
    }

    fun privateKeyText(key: SshKey): String = SecretBox.decrypt(key.privateKeyEnc)
    fun passphrase(key: SshKey): String? = SecretBox.decryptOrNull(key.passphraseEnc)
}
