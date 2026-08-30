package app.mystery0.nodeflow.core.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface V2exThankApi {
    @Headers("User-Agent: ${V2exUserAgents.DESKTOP}")
    @POST("thank/topic/{topicId}")
    suspend fun thankTopic(
        @Path("topicId") topicId: Long,
        @Query("once") once: String,
        @Header("Referer") referer: String,
        @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest",
    ): Response<ResponseBody>

    @Headers("User-Agent: ${V2exUserAgents.DESKTOP}")
    @POST("thank/reply/{replyId}")
    suspend fun thankReply(
        @Path("replyId") replyId: Long,
        @Query("once") once: String,
        @Header("Referer") referer: String,
        @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest",
    ): Response<ResponseBody>
}
