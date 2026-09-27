package app.mystery0.nodeflow.imagehosting.registry

import app.mystery0.nodeflow.imagehosting.contract.ImageHostAdapter
import app.mystery0.nodeflow.imagehosting.contract.ImageHostDescriptor
import app.mystery0.nodeflow.imagehosting.contract.ImageHostId

interface ImageHostRegistry {
    fun descriptors(): List<ImageHostDescriptor>
    fun find(id: ImageHostId): ImageHostAdapter?
}

class DefaultImageHostRegistry(adapters: List<ImageHostAdapter>) : ImageHostRegistry {
    private val adaptersById: Map<ImageHostId, ImageHostAdapter>

    init {
        val duplicate = adapters.groupingBy { it.descriptor.id }.eachCount().entries.firstOrNull { it.value > 1 }
        require(duplicate == null) { "重复的图床 ID: ${duplicate!!.key.value}" }
        adaptersById = adapters.associateBy { it.descriptor.id }
    }

    override fun descriptors(): List<ImageHostDescriptor> = adaptersById.values.map { it.descriptor }
    override fun find(id: ImageHostId): ImageHostAdapter? = adaptersById[id]
}

fun imageHostRegistry(adapters: List<ImageHostAdapter>): ImageHostRegistry = DefaultImageHostRegistry(adapters)
