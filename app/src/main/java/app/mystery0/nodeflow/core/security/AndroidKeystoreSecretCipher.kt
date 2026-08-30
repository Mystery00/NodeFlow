package app.mystery0.nodeflow.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.SecretKey

class AndroidKeystoreSecretCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SecretCipher {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val iv = cipher.iv
        require(iv.size == IV_SIZE_BYTES) { "Invalid generated IV" }
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return "${FORMAT_VERSION}:${encode(iv)}:${encode(encrypted)}"
    }

    override fun decrypt(payload: String): String {
        val parts = payload.split(':')
        require(parts.size == 3 && parts[0] == FORMAT_VERSION) { "Unsupported encrypted payload" }
        val iv = decode(parts[1])
        require(iv.size == IV_SIZE_BYTES) { "Invalid encrypted payload" }
        val encrypted = decode(parts[2])
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        }
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun key(): SecretKey = SynchronizedSecretKeyResolver(
        lock = KEY_LOCK,
        lookup = {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(keyAlias, null) as? SecretKey
        },
        create = {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(KEY_SIZE_BITS)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generator.generateKey()
        },
    ).resolve()

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.DEFAULT)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
        const val FORMAT_VERSION = "1"
        const val DEFAULT_KEY_ALIAS = "nodeflow_secure_storage_key"
        val KEY_LOCK = Any()
    }
}

internal class SynchronizedSecretKeyResolver(
    private val lock: Any,
    private val lookup: () -> SecretKey?,
    private val create: () -> SecretKey,
) {
    fun resolve(): SecretKey {
        lookup()?.let { return it }
        return synchronized(lock) {
            lookup()?.let { return@synchronized it }
            create()
        }
    }
}
