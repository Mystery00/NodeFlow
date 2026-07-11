package app.mystery0.nodeflow.core.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.POST
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

    // 移动版模板的主题列表不含发帖时间，这两个列表页固定按桌面版请求
    @Headers("User-Agent: ${V2exUserAgents.DESKTOP}")
    @GET("go/{nodeName}")
    suspend fun nodeTopicsHtml(
        @Path("nodeName") nodeName: String,
        @Query("p") page: Int? = null,
    ): Response<ResponseBody>

    @Headers("User-Agent: ${V2exUserAgents.DESKTOP}")
    @GET("recent")
    suspend fun recentTopicsHtml(@Query("p") page: Int? = null): Response<ResponseBody>

    @GET("planes")
    suspend fun planesHtml(): Response<ResponseBody>

    @GET("t/{topicId}")
    suspend fun topicHtml(
        @Path("topicId") topicId: Long,
        @Query("p") page: Int? = null,
    ): Response<ResponseBody>

    @GET("member/{username}")
    suspend fun memberHtml(@Path("username") username: String): Response<ResponseBody>

    @GET("signin")
    suspend fun signInPage(@Query("next") next: String = "/mission/daily"): Response<ResponseBody>

    @GET("_captcha")
    suspend fun captcha(
        @Query("_") cacheBust: Long,
        @Header("Referer") referer: String = "https://www.v2ex.com/signin",
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("signin")
    suspend fun signIn(
        @FieldMap fields: Map<String, String>,
        @Header("Origin") origin: String = "https://www.v2ex.com",
        @Header("Referer") referer: String = "https://www.v2ex.com/signin?next=/mission/daily",
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("2fa")
    suspend fun signInTwoFactor(
        @Query("next") next: String = "/mission/daily",
        @FieldMap fields: Map<String, String>,
        @Header("Referer") referer: String = "https://www.v2ex.com/mission/daily",
    ): Response<ResponseBody>

    @GET(".")
    suspend fun home(): Response<ResponseBody>

    @GET("mission/daily")
    suspend fun dailyMission(): Response<ResponseBody>

    @GET("balance")
    suspend fun balance(): Response<ResponseBody>
}
