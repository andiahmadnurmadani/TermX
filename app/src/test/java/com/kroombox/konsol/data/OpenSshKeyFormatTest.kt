package com.kroombox.konsol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey

class OpenSshKeyFormatTest {
    @Test fun ed25519RoundTripPublicBlob() {
        val seed = ByteArray(32) { it.toByte() }
        val pub = ByteArray(32) { (it + 100).toByte() }
        val pem = OpenSshKeyFormat.ed25519Private(seed, pub, "c@x")
        assertTrue(pem.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))
        val blob = OpenSshKeyFormat.publicBlobFromOpenSshPrivate(pem)!!
        assertTrue(blob.contentEquals(OpenSshKeyFormat.ed25519PublicBlob(pub)))
        assertEquals("ssh-ed25519", OpenSshKeyFormat.typeOfBlob(blob))
        assertTrue(OpenSshKeyFormat.fingerprintSha256(blob).startsWith("SHA256:"))
    }

    @Test fun rsaKeyHasRsaType() {
        val g = KeyPairGenerator.getInstance("RSA"); g.initialize(1024)
        val p = g.generateKeyPair().private as RSAPrivateCrtKey
        val pem = OpenSshKeyFormat.rsaPrivate(p.modulus, p.publicExponent, p.privateExponent, p.crtCoefficient, p.primeP, p.primeQ, "r")
        val blob = OpenSshKeyFormat.publicBlobFromOpenSshPrivate(pem)!!
        assertEquals("ssh-rsa", OpenSshKeyFormat.typeOfBlob(blob))
        val line = OpenSshKeyFormat.publicLine("ssh-rsa", blob, "r")
        assertTrue(OpenSshKeyFormat.blobFromPublicLine(line)!!.contentEquals(blob))
    }
}
