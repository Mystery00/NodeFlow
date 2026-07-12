package app.mystery0.nodeflow.navigation

import android.net.Uri

object NodeFlowDestinations {
    const val Main = "main"
    const val Home = "home"
    const val NodeList = "nodes"
    const val Account = "account"
    const val Settings = "settings"
    const val NodeRoute = "node/{nodeName}"
    const val TopicRoute = "topic/{topicId}"
    const val ProfileRoute = "profile/{username}"
    const val Auth = "auth"
    const val Notification = "notification"
    const val Editor = "editor"

    fun node(nodeName: String = "python"): String = "node/${Uri.encode(nodeName)}"
    fun topic(topicId: Long): String = "topic/$topicId"
    fun profile(username: String): String = "profile/${Uri.encode(username)}"

    fun isTopLevelRoute(route: String?): Boolean = route in setOf(Home, NodeList, Account)
}

fun rootStartDestination(): String = NodeFlowDestinations.Main
