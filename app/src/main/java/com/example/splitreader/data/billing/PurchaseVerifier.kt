package com.example.splitreader.data.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies a Google Play purchase's signature against the app's base64-encoded RSA public key
 * (Play Console → Monetization setup → Licensing). Returns true iff [base64Signature] is a valid
 * SHA1withRSA signature of [signedData] under that key. Any malformed input returns false — never
 * throws. Uses only java.security + java.util.Base64 so it is JVM-unit-testable without a device.
 */
object PurchaseVerifier {

    fun verify(base64PublicKey: String, signedData: String, base64Signature: String): Boolean =
        try {
            val keyBytes = Base64.getDecoder().decode(base64PublicKey)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(publicKey)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(base64Signature))
            }
        } catch (t: Throwable) {
            false
        }
}
