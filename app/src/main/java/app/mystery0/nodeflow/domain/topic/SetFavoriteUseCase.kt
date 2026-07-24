package app.mystery0.nodeflow.domain.topic

class SetFavoriteUseCase(private val repository: TopicRepository) {
    suspend operator fun invoke(
        topicId: Long,
        favorite: Boolean,
        once: String,
    ) = repository.setFavorite(topicId, favorite, once)
}
