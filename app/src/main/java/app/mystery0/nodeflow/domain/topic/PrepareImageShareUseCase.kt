package app.mystery0.nodeflow.domain.topic

import app.mystery0.nodeflow.core.model.ImageShareTarget

class PrepareImageShareUseCase(
    private val repository: ImageShareRepository,
) {
    suspend operator fun invoke(imageUrl: String): Result<ImageShareTarget> =
        repository.prepareImageShare(imageUrl)
}
