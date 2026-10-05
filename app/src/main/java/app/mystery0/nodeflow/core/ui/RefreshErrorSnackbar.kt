package app.mystery0.nodeflow.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import app.mystery0.nodeflow.R

/** 保留已有内容时，将刷新错误展示在固定位置，避免被列表滚动位置隐藏。 */
@Composable
fun rememberRefreshErrorSnackbar(errorMessage: String?, onRetry: () -> Unit): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    val retry by rememberUpdatedState(onRetry)
    val retryLabel = stringResource(R.string.favorites_retry)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            val result = host.showSnackbar(
                message = errorMessage,
                actionLabel = retryLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) retry()
        }
    }
    return host
}
