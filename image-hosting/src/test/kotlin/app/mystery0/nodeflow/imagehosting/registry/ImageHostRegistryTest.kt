package app.mystery0.nodeflow.imagehosting.registry

import app.mystery0.nodeflow.imagehosting.contract.*
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageHostRegistryTest {
    private fun adapter(id: String) = object : ImageHostAdapter {
        override val descriptor = ImageHostDescriptor(ImageHostId(id), id, ImageHostCapabilities(setOf("image/png"), 10))
        override suspend fun upload(image: UploadImage) = UploadResult.Failure(
            UploadFailure(descriptor.id, FailureCategory.Server, RequestStage.Upload, ResultCertainty.Rejected)
        )
    }

    @Test fun rejectsBlankOrNamespacedId() {
        assertThrows(IllegalArgumentException::class.java) { ImageHostId(" ") }
        assertThrows(IllegalArgumentException::class.java) { ImageHostId("a:b") }
    }
    @Test fun preservesOrderAndFindsExactId() {
        val registry: ImageHostRegistry = DefaultImageHostRegistry(listOf(adapter("a"), adapter("b")))
        assertThat(registry.descriptors().map { it.id.value }).containsExactly("a", "b").inOrder()
        assertThat(registry.find(ImageHostId("missing"))).isNull()
    }
    @Test fun rejectsDuplicateId() {
        assertThrows(IllegalArgumentException::class.java) { DefaultImageHostRegistry(listOf(adapter("a"), adapter("a"))) }
    }

    @Test fun factoryReturnsPublicInterface() {
        assertThat(imageHostRegistry(listOf(adapter("a"))).find(ImageHostId("a"))).isNotNull()
    }

    @Test fun capabilitiesEnforceMimeAndInclusiveSizeBoundary() {
        val capabilities = ImageHostCapabilities(setOf("image/png"), 3)
        assertThat(capabilities.supports(UploadImage("a.png", "image/png", ByteArray(3)))).isTrue()
        assertThat(capabilities.supports(UploadImage("a.jpg", "image/jpeg", ByteArray(3)))).isFalse()
        assertThat(capabilities.supports(UploadImage("a.png", "image/png", ByteArray(4)))).isFalse()
    }
}
