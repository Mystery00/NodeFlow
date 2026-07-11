package app.mystery0.nodeflow.core.network

object V2exUserAgents {
    const val MOBILE =
        "Mozilla/5.0 (Linux; Android 15; NodeFlow) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    // 移动版模板的主题列表不包含发帖时间戳，列表页需要按桌面版请求
    const val DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; NodeFlow) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
}
