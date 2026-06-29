package app.mystery0.nodeflow.core.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun shouldShowListRefreshIndicator(
    isRefreshing: Boolean,
    itemCount: Int,
): Boolean = isRefreshing && itemCount > 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.ListRefreshIndicator(
    isRefreshing: Boolean,
    itemCount: Int,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val visible = shouldShowListRefreshIndicator(isRefreshing = isRefreshing, itemCount = itemCount)
    val state = rememberPullToRefreshState()

    LaunchedEffect(visible) {
        if (visible) {
            state.animateToThreshold()
        } else {
            state.animateToHidden()
        }
    }

    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = visible,
        modifier = modifier
            .align(Alignment.TopCenter)
            .padding(top = topPadding + 8.dp),
    )
}
