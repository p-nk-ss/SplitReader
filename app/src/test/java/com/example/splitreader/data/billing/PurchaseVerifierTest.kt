package com.example.splitreader.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PurchaseVerifierTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKeyB64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun sign(data: String): String {
        val sig = Signature.getInstance("SHA1withRSA").apply {
            initSign(keyPair.private)
            update(data.toByteArray(Charsets.UTF_8))
        }
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    @Test fun validSignaturePasses() {
        val data = """{"orderId":"abc","productId":"premium_unlimited"}"""
        assertTrue(PurchaseVerifier.verify(publicKeyB64, data, sign(data)))
    }

    @Test fun tamperedDataFails() {
        val data = """{"orderId":"abc","productId":"premium_unlimited"}"""
        val signature = sign(data)
        assertFalse(PurchaseVerifier.verify(publicKeyB64, data + "x", signature))
    }

    @Test fun tamperedSignatureFails() {
        val data = "payload"
        val bad = sign(data).let { if (it.startsWith("A")) "B" + it.drop(1) else "A" + it.drop(1) }
        assertFalse(PurchaseVerifier.verify(publicKeyB64, data, bad))
    }

    @Test fun wrongPublicKeyFails() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val otherB64 = Base64.getEncoder().encodeToString(other.public.encoded)
        val data = "payload"
        assertFalse(PurchaseVerifier.verify(otherB64, data, sign(data)))
    }

    @Test fun garbageInputReturnsFalseNotThrow() {
        assertFalse(PurchaseVerifier.verify("not-base64!!", "data", "also-not-base64!!"))
    }
}
