package app.mystery0.nodeflow.data.user

import app.mystery0.nodeflow.core.database.dao.UserDao
import app.mystery0.nodeflow.core.database.entity.UserEntity
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class UserRepositoryImplTest {
    private val parser = V2exHtmlParser()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun user_refreshesCachedProfileWhenHtmlMetadataIsMissing() = runTest {
        val userDao = FakeUserDao().apply {
            upsertUser(
                UserEntity(
                    username = "Mystery0",
                    id = 1,
                    memberNumber = null,
                    dailyActivityRank = null,
                    avatarUrl = null,
                    bio = "cached bio",
                    tagline = null,
                    website = null,
                    github = null,
                    location = null,
                    createdAtEpochSeconds = 1500561803,
                    cachedAtEpochMillis = 1,
                ),
            )
        }
        val api = FakeV2exRawApi(
            memberJson = """
                {
                  "id": 1,
                  "username": "Mystery0",
                  "bio": "api bio",
                  "created": 1500561803
                }
            """.trimIndent(),
            memberHtml = """
                <html>
                  <body>
                    <img class="avatar" data-uid="243339" src="//cdn.v2ex.com/avatar/sample_large.png" />
                    <span class="gray">Today's activity rank <a href="/top/dau">6,695</a></span>
                  </body>
                </html>
            """.trimIndent(),
        )
        val repository = UserRepositoryImpl(
            remoteDataSource = UserRemoteDataSource(api, json, parser),
            localDataSource = UserLocalDataSource(userDao),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val user = repository.user("Mystery0", forceRefresh = false).getOrThrow()

        assertThat(user.memberNumber).isEqualTo(243339)
        assertThat(user.dailyActivityRank).isEqualTo(6695)
        assertThat(api.memberHtmlRequestCount).isEqualTo(1)
        assertThat(userDao.cachedUser?.dailyActivityRank).isEqualTo(6695)
    }

    private class FakeUserDao : UserDao {
        var cachedUser: UserEntity? = null

        override suspend fun user(username: String): UserEntity? =
            cachedUser?.takeIf { it.username == username }

        override suspend fun upsertUser(user: UserEntity) {
            cachedUser = user
        }

        override suspend fun clear() {
            cachedUser = null
        }
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

        override suspend fun notifications(page: Int): Response<ResponseBody> = htmlResponse("")

        private fun htmlResponse(html: String): Response<ResponseBody> =
            Response.success(html.toResponseBody("text/html".toMediaType()))
    }
}
