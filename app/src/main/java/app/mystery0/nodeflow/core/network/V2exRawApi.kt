package app.mystery0.nodeflow.core.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface V2exRawApi {
    @GET("api/topics/latest.json")
    suspend fun latestTopics(): Response<ResponseBody>

    @GET("api/topics/show.json")
    suspend fun topic(@Query("id") id: Long): Response<ResponseBody>

    @GET("api/replies/show.json")
    suspend fun replies(@Query("topic_id") topicId: Long): Response<ResponseBody>

    @GET("api/nodes/show.json")
    suspend fun node(@Query("name") name: String): Response<ResponseBody>

    @GET("api/members/show.json")
    suspend fun member(@Query("username") username: String): Response<ResponseBody>

    @GET("go/{nodeName}")
    suspend fun nodeTopicsHtml(
        @Path("nodeName") nodeName: String,
        @Query("p") page: Int? = null,
    ): Response<ResponseBody>

    @GET("t/{topicId}")
    suspend fun topicHtml(
        @Path("topicId") topicId: Long,
        @Query("p") page: Int? = null,
    ): Response<ResponseBody>

    @GET("member/{username}")
    suspend fun memberHtml(@Path("username") username: String): Response<ResponseBody>
}
