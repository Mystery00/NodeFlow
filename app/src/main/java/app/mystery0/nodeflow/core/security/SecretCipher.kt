package app.mystery0.nodeflow.core.security

interface SecretCipher {
    fun encrypt(plainText: String): String
    fun decrypt(payload: String): String
}

interface StringKeyValueStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

class EncryptedKeyValueStore(
    private val storage: StringKeyValueStorage,
    private val cipher: SecretCipher,
) {
    fun read(key: String): String? {
        val payload = storage.read(key) ?: return null
        return try {
            cipher.decrypt(payload)
        } catch (_: Exception) {
            storage.remove(key)
            null
        }
    }

    fun write(key: String, value: String) {
        storage.write(key, cipher.encrypt(value))
    }

    fun remove(key: String) {
        storage.remove(key)
    }
}
