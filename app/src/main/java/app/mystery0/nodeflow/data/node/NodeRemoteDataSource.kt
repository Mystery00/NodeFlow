package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.network.V2exRawApi
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
        json.decodeFromString<V2exNodeDto>(api.node(name).bodyStringOrThrow()).toNode()
    }

    suspend fun topics(name: String, page: Int): List<Topic> = safeNetworkCall {
        parser.parseTopicList(
            html = api.nodeTopicsHtml(name, page.takeIf { it > 1 }).bodyStringOrThrow(),
            sourceNodeName = name,
        )
    }

    suspend fun planes(): List<NodePlane> = safeNetworkCall {
        parser.parseNodePlanes(api.planesHtml().bodyStringOrThrow())
    }
}
