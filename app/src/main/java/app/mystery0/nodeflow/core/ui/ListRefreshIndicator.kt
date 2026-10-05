package app.mystery0.nodeflow.core.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
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

@Composable
fun NodeFlowHorizontalRefreshIndicator(modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(modifier = modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.ListRefreshIndicator(
    isRefreshing: Boolean,
    itemCount: Int,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val visible = shouldShowListRefreshIndicator(isRefreshing, itemCount)
    // 非刷新态不保留隐藏的进度语义，也不运行无限动画。
    if (!visible) return
    val state = rememberPullToRefreshState()
    LaunchedEffect(Unit) { state.animateToThreshold() }
    PullToRefreshDefaults.LoadingIndicator(
        modifier = modifier.align(Alignment.TopCenter).padding(top = topPadding + 8.dp),
        state = state,
        isRefreshing = true,
    )
}
