package app.mystery0.nodeflow.navigation

import android.net.Uri
import app.mystery0.nodeflow.core.link.V2exLink

object NodeFlowDestinations {
    const val Main = "main"
    const val Home = "home"
    const val NodeList = "nodes"
    const val Account = "account"
    const val Settings = "settings"
    const val NodeRoute = "node/{nodeName}"
    const val TopicRoute = "topic/{topicId}?replyFloor={replyFloor}"
    const val ProfileRoute = "profile/{username}"
    const val Auth = "auth"
    const val Notification = "notification"

    fun node(nodeName: String = "python"): String = "node/${Uri.encode(nodeName)}"
    fun topic(topicId: Long, replyFloor: Int? = null): String = buildString {
        append("topic/$topicId")
        replyFloor?.let { append("?replyFloor=$it") }
    }
    fun profile(username: String): String = "profile/${Uri.encode(username)}"

    fun isTopLevelRoute(route: String?): Boolean = route in setOf(Home, NodeList, Account)

    fun routeFor(link: V2exLink): String = when (link) {
        is V2exLink.Topic -> topic(link.id)
        is V2exLink.Node -> node(link.name)
        is V2exLink.Member -> profile(link.username)
    }
}

fun rootStartDestination(): String = NodeFlowDestinations.Main
