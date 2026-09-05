package app.mystery0.nodeflow.domain.topic

class GetFavoriteTopicsPagingUseCase(private val repository: FavoriteTopicsRepository) {
    operator fun invoke() = repository.favoriteTopicsPaging()
}
