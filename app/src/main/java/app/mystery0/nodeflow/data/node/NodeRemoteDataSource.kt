package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.network.V2exHtmlAccessTarget
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.V2exWriteApi
import app.mystery0.nodeflow.core.network.accessibleHtmlOrThrow
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.data.common.V2exNodeDto
import app.mystery0.nodeflow.data.common.toNode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl

class NodeRemoteDataSource(
    private val api: V2exRawApi,
    private val writeApi: V2exWriteApi,
    private val json: Json,
    private val parser: V2exHtmlParser,
) {
    suspend fun node(name: String): Node = safeNetworkCall {
        val apiResult = runCatching {
            json.decodeFromString<V2exNodeDto>(api.node(name).bodyStringOrThrow()).toNode()
        }
        val apiNode = apiResult.getOrNull()
        if (apiNode != null && !apiNode.avatarUrl.isNullOrBlank()) {
            apiNode
        } else {
            val htmlNode = runCatching {
                parser.parseNodeDetail(
                    name = name,
                    html = api.nodeTopicsHtml(name, page = null).bodyStringOrThrow(),
                )
            }.getOrNull()
            when {
                apiNode != null -> apiNode.mergeSupplementalNode(htmlNode)
                htmlNode != null -> htmlNode
                else -> apiResult.getOrThrow()
            }
        }
    }

    suspend fun topics(name: String, page: Int): List<Topic> = safeNetworkCall {
        parser.parseTopicList(
            html = api.nodeTopicsHtml(
                nodeName = name,
                page = page.takeIf { it > 1 },
            ).accessibleHtmlOrThrow(V2exHtmlAccessTarget.NodeTopics),
            sourceNodeName = name,
        )
    }

    suspend fun planes(): List<NodePlane> = safeNetworkCall {
        parser.parseNodePlanes(api.planesHtml().bodyStringOrThrow())
    }

    suspend fun blockNode(name: String): Unit = safeNetworkCall {
        val nodeId = node(name).id ?: throw NodeFlowException(
            kind = NodeFlowException.Kind.Parse,
            message = "未获取到节点 ID，无法屏蔽节点",
        )
        val nodeResponse = api.nodeTopicsHtml(name, page = null)
        val nodeHtml = nodeResponse.accessibleHtmlOrThrow(V2exHtmlAccessTarget.NodeTopics)
        if (!nodeResponse.raw().request.url.isExpectedNodePage(name)) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Parse,
                message = "未确认节点操作页面，请刷新后重试",
            )
        }
        val once = parser.parseNodeActionOnce(nodeId = nodeId, html = nodeHtml)
            ?: throw NodeFlowException(
                kind = NodeFlowException.Kind.Auth,
                message = "未获取到节点屏蔽凭证，请刷新登录状态后重试",
            )
        val response = writeApi.getHtml(
            "$V2EX_BASE_URL/settings/ignore/node/$nodeId?once=$once",
        )
        // 屏蔽成功后 V2EX 当前会回到首页，写操作结果不能套用“节点页跳首页即无权访问”的读取规则。
        val resultHtml = response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.ActionResult)
        val resultUrl = response.raw().request.url
        if (!resultUrl.isTrustedV2exPage()) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Parse,
                message = "未确认节点屏蔽结果，请刷新后重试",
            )
        }
        if (parser.hasAccessChallenge(resultHtml)) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.AccessDenied,
                message = "V2EX 暂时拒绝节点操作，请稍后重试",
            )
        }
    }

    private fun HttpUrl.isExpectedNodePage(name: String): Boolean =
        scheme == "https" &&
            host == V2EX_HOST &&
            port == 443 &&
            encodedPath == "/go/$name"

    private fun HttpUrl.isTrustedV2exPage(): Boolean =
        scheme == "https" &&
            host == V2EX_HOST &&
            port == 443

    private companion object {
        const val V2EX_BASE_URL = "https://www.v2ex.com"
        const val V2EX_HOST = "www.v2ex.com"
    }
}

private fun Node.mergeSupplementalNode(supplemental: Node?): Node {
    if (supplemental == null) return this
    return copy(
        id = id ?: supplemental.id,
        name = name.ifBlank { supplemental.name },
        title = title.ifBlank { supplemental.title },
        header = header ?: supplemental.header,
        avatarUrl = avatarUrl ?: supplemental.avatarUrl,
        topics = topics ?: supplemental.topics,
        stars = stars ?: supplemental.stars,
    )
}
