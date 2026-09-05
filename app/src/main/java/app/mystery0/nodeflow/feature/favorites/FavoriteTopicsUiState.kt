package app.mystery0.nodeflow.feature.favorites

import androidx.paging.LoadState
import app.mystery0.nodeflow.core.common.NodeFlowException

data class FavoriteTopicsUiState(val isLoggedIn: Boolean = false)

internal fun favoriteTopicsRequiresLogin(isLoggedIn: Boolean, vararg loadStates: LoadState): Boolean =
    !isLoggedIn || loadStates.any { state ->
        val error = (state as? LoadState.Error)?.error
        error is NodeFlowException && error.kind == NodeFlowException.Kind.Auth
    }
