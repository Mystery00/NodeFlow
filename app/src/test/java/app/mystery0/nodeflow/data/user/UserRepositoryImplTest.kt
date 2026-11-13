package app.mystery0.nodeflow.data.user

import app.mystery0.nodeflow.core.database.dao.UserDao
import app.mystery0.nodeflow.core.database.entity.UserEntity
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.testing.TestV2exRawApi
import app.mystery0.nodeflow.testing.emptyHtmlResponse
import app.mystery0.nodeflow.testing.testHtmlResponse
import app.mystery0.nodeflow.testing.testJsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import org.junit.Test
import retrofit2.Response

class UserRepositoryImplTest {
    private val parser = V2exHtmlParser()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun userPropagatesCancellationInsteadOfReturningCachedProfile() = runTest {
        val cancellation = kotlinx.coroutines.CancellationException("test cancellation")
        val dao = FakeUserDao()
        dao.cachedUser = app.mystery0.nodeflow.core.database.entity.UserEntity(
            username = "tester", id = 1, memberNumber = 1, dailyActivityRank = 2,
            avatarUrl = null, bio = null, tagline = null, website = null, github = null,
            location = null, createdAtEpochSeconds = null, cachedAtEpochMillis = 1,
        )
        val api = FakeV2exRawApi("""{"id":1,"username":"tester"}""", "", memberHtmlError = cancellation)
        val repository = UserRepositoryImpl(UserRemoteDataSource(api, json, parser),
            UserLocalDataSource(dao), StandardTestDispatcher(testScheduler))
        val error = runCatching { repository.user("tester", forceRefresh = true) }.exceptionOrNull()
        assertThat(error).isInstanceOf(kotlinx.coroutines.CancellationException::class.java)
    }

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
        private val memberHtmlError: Throwable? = null,
    ) : TestV2exRawApi(fallback = { emptyHtmlResponse() }) {
        var memberHtmlRequestCount: Int = 0

        override suspend fun member(username: String): Response<ResponseBody> = testJsonResponse(memberJson)

        override suspend fun memberHtml(username: String): Response<ResponseBody> {
            memberHtmlError?.let { throw it }
            memberHtmlRequestCount += 1
            return testHtmlResponse(memberHtml)
        }
    }
}
