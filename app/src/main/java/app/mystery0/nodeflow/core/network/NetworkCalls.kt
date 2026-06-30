package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.core.common.NodeFlowException
import java.io.IOException
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody
import retrofit2.Response

suspend fun <T> safeNetworkCall(block: suspend () -> T): T {
    return try {
        block()
    } catch (error: NodeFlowException) {
        throw error
    } catch (error: IOException) {
        throw NodeFlowException(
            kind = NodeFlowException.Kind.Network,
            message = "网络连接失败，请稍后重试",
            cause = error,
        )
    } catch (error: SerializationException) {
        throw NodeFlowException(
            kind = NodeFlowException.Kind.Parse,
            message = "数据解析失败",
            cause = error,
        )
    } catch (error: Exception) {
        throw NodeFlowException(
            kind = NodeFlowException.Kind.Unknown,
            message = error.message ?: "未知错误",
            cause = error,
        )
    }
}

fun Response<ResponseBody>.bodyStringOrThrow(): String {
    if (!isSuccessful) {
        throw NodeFlowException(
            kind = NodeFlowException.Kind.Http,
            message = "请求失败：HTTP ${code()}",
        )
    }
    return body()?.string()
        ?: throw NodeFlowException(
            kind = NodeFlowException.Kind.EmptyBody,
            message = "服务器返回空内容",
        )
}

fun Response<ResponseBody>.bodyBytesOrThrow(): ByteArray {
    if (!isSuccessful) {
        throw NodeFlowException(
            kind = NodeFlowException.Kind.Http,
            message = "请求失败：HTTP ${code()}",
        )
    }
    return body()?.bytes()
        ?: throw NodeFlowException(
            kind = NodeFlowException.Kind.EmptyBody,
            message = "服务器返回空内容",
        )
}
