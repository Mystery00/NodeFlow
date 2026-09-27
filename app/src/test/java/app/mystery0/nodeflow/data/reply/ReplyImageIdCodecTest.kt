package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.imagehosting.contract.ImageHostId
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ReplyImageIdCodecTest {
    @Test fun legacyIdUsesV2ex() {
        assertThat(ReplyImageIdCodec.parse("abc")).isEqualTo(NamespacedImageId(ImageHostId("v2ex"), "abc"))
    }
    @Test fun formatsAndParsesFirstSeparatorOnly() {
        assertThat(ReplyImageIdCodec.format(ImageHostId("imgur"), "a:b")).isEqualTo("imgur:a:b")
        assertThat(ReplyImageIdCodec.parse("imgur:a:b").remoteId).isEqualTo("a:b")
    }
    @Test fun rejectsEmptyParts() {
        assertThrows(IllegalArgumentException::class.java) { ReplyImageIdCodec.parse(":x") }
        assertThrows(IllegalArgumentException::class.java) { ReplyImageIdCodec.parse("x:") }
    }
    @Test fun deduplicatesWithinProviderButKeepsProviders() {
        assertThat(ReplyImageIdCodec.deduplicate(listOf("old", "v2ex:old", "imgur:old")))
            .containsExactly("v2ex:old", "imgur:old").inOrder()
    }
}
