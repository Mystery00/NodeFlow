package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.core.common.NodeFlowException
import com.google.common.truth.Truth.assertThat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class NetworkCallsTest {
    @Test
    fun bodyStringOrThrow_throwsNotFoundOn404() {
        val response = errorResponse(404)

        val error = runCatching { response.bodyStringOrThrow() }
            .exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.NotFound)
        assertThat(error.message).isEqualTo("请求失败：HTTP 404")
    }

    @Test
    fun bodyStringOrThrow_throwsHttpOnOtherErrorCodes() {
        val response = errorResponse(500)

        val error = runCatching { response.bodyStringOrThrow() }
            .exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Http)
        assertThat(error.message).isEqualTo("请求失败：HTTP 500")
    }

    @Test
    fun bodyBytesOrThrow_throwsNotFoundOn404() {
        val response = errorResponse(404)

        val error = runCatching { response.bodyBytesOrThrow() }
            .exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.NotFound)
    }

    @Test
    fun bodyStringOrThrow_returnsBodyOnSuccess() {
        val response = Response.success("ok".toResponseBody("text/plain".toMediaType()))

        assertThat(response.bodyStringOrThrow()).isEqualTo("ok")
    }

    private fun errorResponse(code: Int): Response<ResponseBody> =
        Response.error(code, "Not Found".toResponseBody("text/plain".toMediaType()))
}
