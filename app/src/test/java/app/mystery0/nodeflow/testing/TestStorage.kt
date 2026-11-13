package app.mystery0.nodeflow.testing

import app.mystery0.nodeflow.core.security.StringKeyValueStorage

/** 测试用内存键值存储替身。 */
open class FakeStringKeyValueStorage : StringKeyValueStorage {
    val values = linkedMapOf<String, String>()
    var lastMutationThread: String? = null
        private set

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String) {
        lastMutationThread = Thread.currentThread().name
        values[key] = value
    }

    override fun remove(key: String) {
        lastMutationThread = Thread.currentThread().name
        values.remove(key)
    }
}
