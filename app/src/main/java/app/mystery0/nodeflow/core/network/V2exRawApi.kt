package app.mystery0.nodeflow.core.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
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

    // 移动版帖子页的回复只有相对时间，桌面版才带精确时间戳，因此按桌面版请求
    @Headers("User-Agent: ${V2exUserAgents.DESKTOP}")
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

    @GET("mission/daily/redeem")
    suspend fun redeemDailyMission(
        @Query("once") once: String,
        @Header("Referer") referer: String = "https://www.v2ex.com/mission/daily",
    ): Response<ResponseBody>

    @GET("balance")
    suspend fun balance(): Response<ResponseBody>

    // 通知页的移动端请求会触发“干净安装的浏览器”校验，复用现有桌面页面请求策略。
    @Headers("User-Agent: ${V2exUserAgents.DESKTOP}")
    @GET("notifications")
    suspend fun notifications(@Query("p") page: Int = 1): Response<ResponseBody>

    // 记事本页面用于读取 V2EX_Polish 插件同步的用户标签数据
    @GET("notes")
    suspend fun notesHtml(): Response<ResponseBody>

    @GET("notes/edit/{id}")
    suspend fun noteEditHtml(@Path("id") id: Long): Response<ResponseBody>

    // 记事编辑/新建表单用于写回 Polish 用户标签；syntax=0 与插件提交行为保持一致
    @FormUrlEncoded
    @POST("notes/edit/{id}")
    suspend fun noteEditSubmit(
        @Path("id") id: Long,
        @Field("content") content: String,
        @Field("syntax") syntax: String = "0",
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("notes/new")
    suspend fun noteNewSubmit(
        @Field("content") content: String,
        @Field("syntax") syntax: String = "0",
    ): Response<ResponseBody>

    // 收藏主题：V2EX 通过 GET 请求完成收藏操作，需要 once token 防 CSRF
    @Headers("User-Agent: ${V2exUserAgents.DESKTOP}")
    @GET("favorite/topic/{topicId}")
    suspend fun favoriteTopic(
        @Path("topicId") topicId: Long,
        @Query("once") once: String,
        @Header("Referer") referer: String,
    ): Response<ResponseBody> = throw NotImplementedError()

    // 取消收藏主题
    @Headers("User-Agent: ${V2exUserAgents.DESKTOP}")
    @GET("unfavorite/topic/{topicId}")
    suspend fun unfavoriteTopic(
        @Path("topicId") topicId: Long,
        @Query("once") once: String,
        @Header("Referer") referer: String,
    ): Response<ResponseBody> = throw NotImplementedError()
}
