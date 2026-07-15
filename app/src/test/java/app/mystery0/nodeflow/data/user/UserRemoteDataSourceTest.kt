package app.mystery0.nodeflow.data.user

import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
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
    ) : V2exRawApi {
        var memberHtmlRequestCount: Int = 0

        override suspend fun latestTopics(): Response<ResponseBody> = htmlResponse("")

        override suspend fun topic(id: Long): Response<ResponseBody> = htmlResponse("")

        override suspend fun replies(topicId: Long): Response<ResponseBody> = htmlResponse("")

        override suspend fun node(name: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun member(username: String): Response<ResponseBody> =
            Response.success(memberJson.toResponseBody("application/json".toMediaType()))

        override suspend fun nodeTopicsHtml(nodeName: String, page: Int?): Response<ResponseBody> = htmlResponse("")

        override suspend fun recentTopicsHtml(page: Int?): Response<ResponseBody> = htmlResponse("")

        override suspend fun planesHtml(): Response<ResponseBody> = htmlResponse("")

        override suspend fun topicHtml(topicId: Long, page: Int?): Response<ResponseBody> = htmlResponse("")

        override suspend fun memberHtml(username: String): Response<ResponseBody> {
            memberHtmlRequestCount += 1
            return htmlResponse(memberHtml)
        }

        override suspend fun signInPage(next: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun captcha(cacheBust: Long, referer: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun signIn(
            fields: Map<String, String>,
            origin: String,
            referer: String,
        ): Response<ResponseBody> = htmlResponse("")

        override suspend fun signInTwoFactor(
            next: String,
            fields: Map<String, String>,
            referer: String,
        ): Response<ResponseBody> = htmlResponse("")

        override suspend fun home(): Response<ResponseBody> = htmlResponse("")

        override suspend fun dailyMission(): Response<ResponseBody> = htmlResponse("")

        override suspend fun redeemDailyMission(once: String, referer: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun balance(): Response<ResponseBody> = htmlResponse("")

        private fun htmlResponse(html: String): Response<ResponseBody> =
            Response.success(html.toResponseBody("text/html".toMediaType()))
    }
}
