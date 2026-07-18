package app.mystery0.nodeflow.feature.replyeditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReplyEditorTextTest {
    @Test
    fun insertFloorReference_usesCurrentCursorAndAvoidsAdjacentDuplicate() {
        val inserted = insertFloorReference(
            TextFieldValue("前后", selection = TextRange(1)),
            "alice",
            7,
        )
        assertThat(inserted.text).isEqualTo("前@alice #7 后")
        assertThat(inserted.selection.start).isEqualTo("前@alice #7 ".length)
        assertThat(insertFloorReference(inserted, "alice", 7)).isEqualTo(inserted)
    }

    @Test
    fun insertImageUrl_putsBareUrlOnOwnLine() {
        val result = insertImageUrl(
            TextFieldValue("前后", selection = TextRange(1)),
            "https://i.v2ex.co/image.png",
        )
        assertThat(result.text).isEqualTo("前\nhttps://i.v2ex.co/image.png\n后")
    }
}
