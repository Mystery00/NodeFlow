package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.network.V2exHtmlAccessTarget
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.accessibleHtmlOrThrow
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.data.common.V2exNodeDto
import app.mystery0.nodeflow.data.common.toNode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class NodeRemoteDataSource(
    private val api: V2exRawApi,
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
