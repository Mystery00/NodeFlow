package app.mystery0.nodeflow.data.user

import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.data.common.V2exMemberDto
import app.mystery0.nodeflow.data.common.toUser
import javax.inject.Inject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class UserRemoteDataSource @Inject constructor(
    private val api: V2exRawApi,
    private val json: Json,
    private val parser: V2exHtmlParser,
) {
    suspend fun user(username: String): User = safeNetworkCall {
        val apiUser = json.decodeFromString<V2exMemberDto>(api.member(username).bodyStringOrThrow()).toUser()
        if (apiUser.bio.isNullOrBlank()) {
            val htmlUser = runCatching {
                parser.parseUserProfile(username, api.memberHtml(username).bodyStringOrThrow())
            }.getOrNull()
            apiUser.copy(bio = htmlUser?.bio ?: apiUser.bio)
        } else {
            apiUser
        }
    }
}
