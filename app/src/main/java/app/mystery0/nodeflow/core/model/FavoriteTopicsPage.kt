package app.mystery0.nodeflow.core.model

data class FavoriteTopicsPage(
    val topics: List<Topic>,
    val nextPage: Int?,
)
