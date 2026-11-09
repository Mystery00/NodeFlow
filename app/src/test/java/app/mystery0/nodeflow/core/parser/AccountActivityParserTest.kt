package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccountActivityParserTest {
    private val parser = V2exHtmlParser()

    @Test
    fun readsSidebarPercentageIncludingZeroAndFullActivity() {
        for (value in listOf("0", "59", "100", "59.5")) {
            assertThat(parser.parseAccountActivityPercent(sidebar("width: $value%;")))
                .isEqualTo(value.toFloat())
        }
        assertThat(parser.parseAccountActivityPercent(sidebar("background: red; WIDTH : 59.5 % ; height: 3px")))
            .isEqualTo(59.5f)
    }

    @Test
    fun readsCurrentDesktopActivityStagesWithoutSignoutLink() {
        val html = sidebar("width: 99%")
            .replace("member-activity-start", "member-activity-fourth")
            .replace("<a href='/signout'>退出</a>", "")
        assertThat(parser.parseAccountActivityPercent(html)).isEqualTo(99f)
    }

    @Test
    fun missingMalformedAndOutOfRangeAreUnknown() {
        for (style in listOf("", "width: 101%", "width: -1%", "width: NaN%", "width: Infinity%", "width: 59px", "max-width: 59%", "width: calc(59%)")) {
            assertThat(parser.parseAccountActivityPercent(sidebar(style))).isNull()
        }
        assertThat(parser.parseAccountActivityPercent("<div id='Rightbar'></div>")).isNull()
    }

    @Test
    fun ignoresBodySpoofsAndUnauthenticatedOrChallengedPages() {
        val bar = "<div class='member-activity-bar'><div class='member-activity-start' style='width: 99%'></div></div>"
        assertThat(parser.parseAccountActivityPercent("<div id='Main'>$bar</div>")).isNull()
        assertThat(parser.parseAccountActivityPercent(sidebar("width: 59%") + "<form action='/signin'></form>")).isNull()
        assertThat(parser.parseAccountActivityPercent(sidebar("width: 59%") + "<title>Just a moment...</title><div id='cf-chl-widget'></div>")).isNull()
        assertThat(parser.parseAccountActivityPercent("<div id='Rightbar'>$bar</div>")).isNull()
    }

    private fun sidebar(style: String) = """
        <div id='Rightbar'><div class='box'>
          <a href='/member/reader'>reader</a><a href='/signout'>退出</a>
          <a href='/my/nodes'>节点收藏</a><a href='/my/topics'>主题收藏</a><a href='/my/following'>特别关注</a>
          <div class='cell'><div class='member-activity-bar'>
            <div class='member-activity-start' style='$style'></div>
          </div></div>
        </div></div>
    """.trimIndent()
}
