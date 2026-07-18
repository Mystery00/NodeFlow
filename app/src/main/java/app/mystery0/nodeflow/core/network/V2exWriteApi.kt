package app.mystery0.nodeflow.core.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface V2exWriteApi {
    @GET
    suspend fun getHtml(@Url url: String): Response<ResponseBody>

    @POST
    suspend fun submitForm(
        @Url url: String,
        @Body body: RequestBody,
        @Header("Origin") origin: String,
        @Header("Referer") referer: String,
    ): Response<ResponseBody>

    @POST("i/upload")
    suspend fun uploadImage(
        @Body body: RequestBody,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest",
        @Header("Referer") referer: String,
    ): Response<ResponseBody>
}
