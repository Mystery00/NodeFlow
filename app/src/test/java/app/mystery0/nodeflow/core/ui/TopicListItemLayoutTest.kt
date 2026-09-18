package app.mystery0.nodeflow.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicListItemLayoutTest {
    @Test
    fun compactTopicListItemLayout_matchesReferenceListDensity() {
        val layout = compactTopicListItemLayout()

        assertThat(layout.horizontalPadding.value).isEqualTo(16f)
        assertThat(layout.verticalPadding.value).isEqualTo(12f)
        assertThat(layout.avatarSize.value).isEqualTo(40f)
        assertThat(layout.avatarCornerRadius.value).isEqualTo(8f)
        assertThat(layout.avatarToContentSpacing.value).isEqualTo(12f)
        assertThat(layout.contentSpacing.value).isEqualTo(6f)
        assertThat(layout.metadataSpacing.value).isEqualTo(8f)
        assertThat(layout.replyBadgeSpacing.value).isEqualTo(12f)
        assertThat(layout.dividerStartPadding.value).isEqualTo(68f)
        assertThat(layout.dividerEndPadding.value).isEqualTo(16f)
        assertThat(layout.dividerHorizontalPadding.value).isEqualTo(16f)
        assertThat(layout.dividerThickness.value).isEqualTo(0.5f)
    }
}
