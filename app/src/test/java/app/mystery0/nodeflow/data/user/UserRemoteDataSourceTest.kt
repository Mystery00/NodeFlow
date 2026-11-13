package app.mystery0.nodeflow.data.user

import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.testing.TestV2exRawApi
import app.mystery0.nodeflow.testing.emptyHtmlResponse
import app.mystery0.nodeflow.testing.testHtmlResponse
import app.mystery0.nodeflow.testing.testJsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import org.junit.Test
import retrofit2.Response

class UserRemoteDataSourceTest {
    private val parser = V2exHtmlParser()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun user_keepsApiProfileAndMergesHtmlMetadata() = runTest {
        val api = FakeV2exRawApi(
            memberJson = """
                {
                  "id": 1,
                  "username": "Mystery0",
                  "bio": "api bio",
                  "avatar_large": "https://cdn.v2ex.com/avatar/api_large.png",
                  "created": 1500561803
                }
            """.trimIndent(),
            memberHtml = """
                <html>
                  <head>
                    <script type="application/ld+json">
                      {
                        "@type": "ProfilePage",
                        "mainEntity": {
                          "@type": "Person",
                          "identifier": "243339"
                        }
                      }
                    </script>
                  </head>
                  <body>
                    <div class="cell">
                      <span class="gray">Today's activity rank <a href="/top/dau">6,695</a></span>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )
        val dataSource = UserRemoteDataSource(api, json, parser)

        val user = dataSource.user("Mystery0")

        assertThat(user.id).isEqualTo(1)
        assertThat(user.bio).isEqualTo("api bio")
        assertThat(user.memberNumber).isEqualTo(243339)
        assertThat(user.dailyActivityRank).isEqualTo(6695)
        assertThat(api.memberHtmlRequestCount).isEqualTo(1)
    }

    private class FakeV2exRawApi(
        private val memberJson: String,
        private val memberHtml: String,
    ) : TestV2exRawApi(fallback = { emptyHtmlResponse() }) {
        var memberHtmlRequestCount: Int = 0

        override suspend fun member(username: String): Response<ResponseBody> = testJsonResponse(memberJson)

        override suspend fun memberHtml(username: String): Response<ResponseBody> {
            memberHtmlRequestCount += 1
            return testHtmlResponse(memberHtml)
        }
    }
}
