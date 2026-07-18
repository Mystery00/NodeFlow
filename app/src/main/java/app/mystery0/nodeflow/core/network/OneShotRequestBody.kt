package app.mystery0.nodeflow.core.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/** 标记为不可重放的请求体，避免写操作被重定向或服务端重试指令再次提交。 */
class OneShotRequestBody(
    private val delegate: RequestBody,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)

    override fun isOneShot(): Boolean = true
}

fun RequestBody.asOneShot(): RequestBody = OneShotRequestBody(this)
