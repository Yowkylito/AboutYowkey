package dev.yowkey.portfolio.app.owner

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals the owner's token with AES-256-GCM under a key that lives in the Android Keystore (hardware-backed where the
 * phone has it) and never leaves it, so the preferences file holds only ciphertext. The platform API directly, not
 * the deprecated security-crypto library. A sealed value reads `v1:<iv>:<ciphertext>`, both Base64.
 */
internal class TokenCipher(private val alias: String = "owner-token") {

    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key()) // the Keystore picks a fresh random IV each time
        val sealed = cipher.doFinal(plain.encodeToByteArray())
        return "v1:${encode(cipher.iv)}:${encode(sealed)}"
    }

    /** @throws Exception when [sealed] is not this key's (the key was lost, the data altered) */
    fun open(sealed: String): String {
        val parts = sealed.split(':')
        require(parts.size == 3 && parts[0] == "v1") { "unknown format" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, decode(parts[1])))
        return cipher.doFinal(decode(parts[2])).decodeToString()
    }

    /** Drops the key, so whatever it sealed can never be read again. */
    fun forget() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
