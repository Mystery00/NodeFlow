package app.mystery0.nodeflow.feature.topicdetail

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.LocalMemberTags
import app.mystery0.nodeflow.core.designsystem.component.LocalCustomImageHosts
import app.mystery0.nodeflow.core.designsystem.component.memberTagsFor
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.designsystem.component.NodeChip
import app.mystery0.nodeflow.core.designsystem.component.RichContent
import app.mystery0.nodeflow.core.designsystem.component.RichContentBlockView
import app.mystery0.nodeflow.core.designsystem.component.RichContentImageSizeCache
import app.mystery0.nodeflow.core.designsystem.component.ZoomableImageViewer
import app.mystery0.nodeflow.core.designsystem.component.rememberRichContentImageSizeCache
import app.mystery0.nodeflow.core.link.V2exLink
import app.mystery0.nodeflow.core.link.V2exLinkParser
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.TopicAppend
import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.parser.RichContentParser
import app.mystery0.nodeflow.core.ui.MemberTagChips
import app.mystery0.nodeflow.core.ui.NodeFlowHorizontalRefreshIndicator
import app.mystery0.nodeflow.core.ui.ReplyItem
import app.mystery0.nodeflow.core.ui.TopicNodeChip
import app.mystery0.nodeflow.core.ui.formatEpochSeconds
import app.mystery0.nodeflow.core.ui.isReplyFromTopicAuthor
import app.mystery0.nodeflow.core.ui.topicNodeChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.mystery0.nodeflow.feature.replyeditor.ReplyEditorBottomSheet
import app.mystery0.nodeflow.feature.replyeditor.ReplyEditorUiEvent
import app.mystery0.nodeflow.feature.replyeditor.ReplyEditorUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private const val SecondsPerMinute = 60L
private const val SecondsPerHour = 60L * SecondsPerMinute
private const val SecondsPerDay = 24L * SecondsPerHour

internal fun topicReplyListIndex(
    bodyBlockCount: Int,
    hasAppends: Boolean,
    replyIndex: Int,
): Int = 1 + bodyBlockCount + (if (hasAppends) 1 else 0) + 1 + replyIndex

internal fun topicMetadataText(
    username: String,
    time: String,
    viewCount: Int?,
): String = buildList {
    username.takeIf { it.isNotBlank() }?.let(::add)
    time.takeIf { it.isNotBlank() }?.let(::add)
    viewCount?.let { add("${it} 次点击") }
}.joinToString(" · ")

internal fun topicDetailNodeChip(detail: TopicDetail): TopicNodeChip? =
    topicNodeChip(detail.topic)

internal data class TopicDetailMetadataLayout(
    val nodeChipMaxWidth: Dp,
)

internal fun topicDetailMetadataLayout(): TopicDetailMetadataLayout = TopicDetailMetadataLayout(
    nodeChipMaxWidth = 120.dp,
)

internal fun formatTopicMetadataTime(
    epochSeconds: Long?,
    nowEpochSeconds: Long = java.time.Instant.now().epochSecond,
): String {
    if (epochSeconds == null || epochSeconds <= 0) return ""
    val elapsedSeconds = nowEpochSeconds - epochSeconds
    if (elapsedSeconds < SecondsPerMinute) return "刚刚"
    if (elapsedSeconds < SecondsPerHour) return "${elapsedSeconds / SecondsPerMinute} 分钟前"
    if (elapsedSeconds < SecondsPerDay) {
        val hours = elapsedSeconds / SecondsPerHour
        val minutes = elapsedSeconds % SecondsPerHour / SecondsPerMinute
        return if (minutes > 0) {
            "${hours} 小时 ${minutes} 分钟前"
        } else {
            "${hours} 小时前"
        }
    }
    return formatEpochSeconds(epochSeconds, nowEpochSeconds)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TopicDetailScreen(
    state: TopicDetailUiState,
    onEvent: (TopicDetailUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onNodeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (Long) -> Unit = {},
    initialReplyFloor: Int? = null,
    replyEditorState: ReplyEditorUiState = ReplyEditorUiState(),
    onReplyEditorEvent: (ReplyEditorUiEvent) -> Unit = {},
    onLoginClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val detail = state.detail
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    var replyFabVisible by remember { mutableStateOf(true) }
    var previousPosition by remember { mutableStateOf(ScrollPosition(0, 0)) }
    var selectedReply by remember { mutableStateOf<Reply?>(null) }
    var showTopicThankConfirmation by remember { mutableStateOf(false) }
    var pendingThankReply by remember { mutableStateOf<Reply?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { onReplyEditorEvent(ReplyEditorUiEvent.ImageSelected(it.toString())) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(state.favoriteToastMessage) {
        state.favoriteToastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onEvent(TopicDetailUiEvent.FavoriteToastConsumed)
        }
    }
    DisposableEffect(lifecycleOwner, onReplyEditorEvent) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onReplyEditorEvent(ReplyEditorUiEvent.FlushDraft)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(listState, replyEditorState.isOpen) {
        if (replyEditorState.isOpen) return@LaunchedEffect
        snapshotFlow { ScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collect { current ->
                replyFabVisible = replyFabVisibleAfterScroll(previousPosition, current, replyFabVisible)
                previousPosition = current
            }
    }
    BackHandler(enabled = replyEditorState.isOpen) {
        if (imeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onReplyEditorEvent(ReplyEditorUiEvent.Close)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("主题详情") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(TopicDetailUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    if (detail != null) {
                        TopicActions(
                            detail = detail,
                            isTogglingFavorite = state.isTogglingFavorite,
                            isThankingTopic = state.isThankingTopic,
                            isThankingReply = state.thankingReplyId != null,
                            onToggleFavorite = { onEvent(TopicDetailUiEvent.ToggleFavorite) },
                            onThankTopic = { showTopicThankConfirmation = true },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingContent(paddingValues = paddingValues)
            state.errorMessage != null && detail == null -> ErrorContent(
                message = state.errorMessage,
                onRetry = { onEvent(TopicDetailUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            detail == null -> EmptyContent(
                message = "主题不存在",
                paddingValues = paddingValues,
            )
            else -> TopicDetailContent(
                detail = detail,
                isRefreshing = state.isRefreshing,
                errorMessage = state.errorMessage,
                hasMoreReplies = state.hasMoreReplies,
                isLoadingMore = state.isLoadingMore,
                loadMoreError = state.loadMoreError,
                onLoadMore = { onEvent(TopicDetailUiEvent.LoadMoreReplies) },
                onNodeClick = onNodeClick,
                onUserClick = onUserClick,
                onTopicClick = onTopicClick,
                onImageClick = { previewImageUrl = it },
                initialReplyFloor = initialReplyFloor,
                replyFloorTarget = state.replyFloorTarget,
                onReplyFloorTargetConsumed = {
                    onEvent(TopicDetailUiEvent.ReplyFloorTargetConsumed)
                },
                contentPadding = paddingValues,
                listState = listState,
                replyEditorOpen = replyEditorState.isOpen,
                onReplyMoreClick = { selectedReply = it },
                onDirectReplyClick = {
                    onReplyEditorEvent(
                        ReplyEditorUiEvent.OpenFloorReply(it.author.username, it.floor),
                    )
                },
            )
        }
    }
        AnimatedVisibility(
            visible = detail != null && replyFabVisible && !replyEditorState.isOpen,
            modifier = Modifier.align(BottomEnd).navigationBarsPadding().padding(20.dp),
            enter = slideInVertically { it / 2 } + fadeIn() + scaleIn(),
            exit = slideOutVertically { it / 2 } + fadeOut() + scaleOut(),
        ) {
            FloatingActionButton(onClick = { onReplyEditorEvent(ReplyEditorUiEvent.OpenTopicReply) }) {
                Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = "回复主题")
            }
        }
        ReplyEditorBottomSheet(
            state = replyEditorState,
            onEvent = onReplyEditorEvent,
            onPickImage = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onLoginClick = onLoginClick,
        )
    }
    selectedReply?.let { reply ->
        ModalBottomSheet(onDismissRequest = { selectedReply = null }) {
            Button(
                onClick = {
                    selectedReply = null
                    onReplyEditorEvent(
                        ReplyEditorUiEvent.OpenFloorReply(reply.author.username, reply.floor),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text("回复") }
            TextButton(
                onClick = {
                    selectedReply = null
                    pendingThankReply = reply
                },
                enabled = reply.isThanked == false && detail?.thankOnce != null && state.thankingReplyId == null && !state.isThankingTopic,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(if (reply.isThanked == true) "感谢已发送" else "感谢回复者 · 10 铜币")
            }
            Spacer(Modifier.size(16.dp))
        }
    }
    if (showTopicThankConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!state.isThankingTopic) showTopicThankConfirmation = false },
            title = { Text("感谢主题") },
            text = { Text("确定要向本主题创建者发送谢意吗？") },
            confirmButton = {
                TextButton(onClick = { onEvent(TopicDetailUiEvent.ThankTopic) }, enabled = !state.isThankingTopic) {
                    Text(if (state.isThankingTopic) "发送中…" else "发送感谢")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTopicThankConfirmation = false }, enabled = !state.isThankingTopic) { Text("取消") }
            },
        )
        LaunchedEffect(detail?.isThanked) {
            if (detail?.isThanked == true) showTopicThankConfirmation = false
        }
    }
    pendingThankReply?.let { reply ->
        AlertDialog(
            onDismissRequest = { if (state.thankingReplyId == null) pendingThankReply = null },
            title = { Text("感谢回复者") },
            text = { Text("确定花费 10 个铜币向 @${reply.author.username} 的这条回复发送感谢吗？") },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(TopicDetailUiEvent.ThankReply(reply.id)) },
                    enabled = state.thankingReplyId == null,
                ) { Text(if (state.thankingReplyId == reply.id) "发送中…" else "发送感谢") }
            },
            dismissButton = {
                TextButton(onClick = { pendingThankReply = null }, enabled = state.thankingReplyId == null) { Text("取消") }
            },
        )
        LaunchedEffect(detail?.replies?.firstOrNull { it.id == reply.id }?.isThanked) {
            if (detail?.replies?.firstOrNull { it.id == reply.id }?.isThanked == true) pendingThankReply = null
        }
    }
    state.thankError?.let { message ->
        AlertDialog(
            onDismissRequest = { onEvent(TopicDetailUiEvent.ThankErrorConsumed) },
            title = { Text("感谢发送失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { onEvent(TopicDetailUiEvent.ThankErrorConsumed) }) { Text("知道了") }
            },
        )
    }
    state.shareTarget?.let { target ->
        LaunchedEffect(target) {
            val uri = Uri.parse(target.contentUri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = target.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "Image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享图片"))
            onEvent(TopicDetailUiEvent.ShareTargetConsumed)
        }
    }
    state.shareError?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onEvent(TopicDetailUiEvent.ShareErrorConsumed)
        }
    }
    previewImageUrl?.let { imageUrl ->
        ZoomableImageViewer(
            imageUrl = imageUrl,
            onDismiss = { previewImageUrl = null },
            isSharing = state.isSharingImage,
            onShare = { onEvent(TopicDetailUiEvent.ShareImage(imageUrl)) },
        )
    }
}


@Composable
private fun TopicActions(
    detail: TopicDetail,
    isTogglingFavorite: Boolean,
    isThankingTopic: Boolean,
    isThankingReply: Boolean,
    onToggleFavorite: () -> Unit,
    onThankTopic: () -> Unit,
) {
    val context = LocalContext.current
    val url = detail.topic.url
    // 收藏按钮：仅在登录且解析到收藏状态时显示
    if (detail.isFavorited != null) {
        IconButton(
            onClick = onToggleFavorite,
            enabled = !isTogglingFavorite && detail.favoriteOnce != null,
        ) {
            Icon(
                imageVector = if (detail.isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (detail.isFavorited) "取消收藏" else "收藏",
                tint = if (detail.isFavorited) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
    if (detail.isThanked != null) {
        IconButton(
            onClick = onThankTopic,
            enabled = detail.isThanked == false && detail.thankOnce != null && !isThankingTopic && !isThankingReply,
        ) {
            Icon(
                Icons.Outlined.ThumbUp,
                contentDescription = if (detail.isThanked == true) "感谢已发送" else "感谢主题",
                tint = if (detail.isThanked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    IconButton(
        onClick = {
            val systemClipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            systemClipboard?.setPrimaryClip(ClipData.newPlainText("NodeFlow topic", url))
        },
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制链接")
    }
    IconButton(
        onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${detail.topic.title}\n$url")
            }
            context.startActivity(Intent.createChooser(intent, "分享主题"))
        },
    ) {
        Icon(Icons.Outlined.Share, contentDescription = "分享")
    }
}

@Composable
private fun TopicDetailContent(
    detail: TopicDetail,
    isRefreshing: Boolean,
    errorMessage: String?,
    hasMoreReplies: Boolean,
    isLoadingMore: Boolean,
    loadMoreError: String?,
    onLoadMore: () -> Unit,
    onNodeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onImageClick: (String) -> Unit,
    initialReplyFloor: Int?,
    replyFloorTarget: Int?,
    onReplyFloorTargetConsumed: () -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
    replyEditorOpen: Boolean,
    onReplyMoreClick: (Reply) -> Unit,
    onDirectReplyClick: (Reply) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val customImageHosts = LocalCustomImageHosts.current
    val bodyDocument = remember(detail.contentRendered, customImageHosts) {
        RichContentParser.parse(detail.contentRendered, customImageHosts)
    }
    val imageSizeCache = rememberRichContentImageSizeCache(detail.topic.id)
    val replyListStartIndex = topicReplyListIndex(
        bodyBlockCount = bodyDocument.blocks.size,
        hasAppends = detail.appends.isNotEmpty(),
        replyIndex = 0,
    )
    var highlightedReplyId by remember(detail.topic.id) { mutableStateOf<Long?>(null) }
    val replyRefreshKey = if (replyFloorTarget != null) detail.replies.lastOrNull()?.id else null
    // 按需分页下目标楼层可能在补页完成后才出现，用该布尔值的翻转重新触发定位
    val targetFloorLoaded =
        detail.replies.size >= (replyFloorTarget ?: initialReplyFloor ?: 0)
    LaunchedEffect(
        detail.topic.id,
        initialReplyFloor,
        replyFloorTarget,
        replyRefreshKey,
        targetFloorLoaded,
        isRefreshing,
        replyListStartIndex,
    ) {
        if (replyFloorTarget != null && isRefreshing) return@LaunchedEffect
        val targetFloor = replyFloorTarget ?: initialReplyFloor
        val targetIndex = detail.replies.indexOfFirst { it.floor == targetFloor }
        if (targetIndex >= 0) {
            val replyId = detail.replies[targetIndex].id
            listState.scrollToItem(index = replyListStartIndex + targetIndex)
            highlightedReplyId = replyId
            delay(1400)
            if (highlightedReplyId == replyId) highlightedReplyId = null
            if (replyFloorTarget != null) onReplyFloorTargetConsumed()
        } else if (replyFloorTarget != null) {
            if (detail.replies.isNotEmpty()) {
                listState.scrollToItem(replyListStartIndex + detail.replies.lastIndex)
            }
            onReplyFloorTargetConsumed()
        }
    }
    // 正文与回复里的 v2ex 链接优先在 app 内打开，无法识别的返回 false 走浏览器
    val openV2exUrl: (String) -> Boolean = { url ->
        when (val link = V2exLinkParser.parse(url)) {
            is V2exLink.Topic -> {
                onTopicClick(link.id)
                true
            }
            is V2exLink.Node -> {
                onNodeClick(link.name)
                true
            }
            is V2exLink.Member -> {
                onUserClick(link.username)
                true
            }
            null -> false
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (isRefreshing) {
            NodeFlowHorizontalRefreshIndicator(Modifier.fillMaxWidth())
        }
        if (errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 80.dp,
            ),
        ) {
            item(
                key = TOPIC_DETAIL_HEADER_KEY,
                contentType = TOPIC_DETAIL_HEADER_CONTENT_TYPE,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = detail.topic.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TopicMetadataRow(
                        detail = detail,
                        onUserClick = onUserClick,
                        onNodeClick = onNodeClick,
                    )
                    val authorTags = memberTagsFor(
                        LocalMemberTags.current,
                        detail.topic.author.username,
                    )
                    if (authorTags.isNotEmpty()) {
                        MemberTagChips(tags = authorTags)
                    }
                }
            }
            itemsIndexed(
                items = bodyDocument.blocks,
                key = { index, _ -> "topic-body-$index" },
                contentType = { _, _ -> TOPIC_DETAIL_BODY_CONTENT_TYPE },
            ) { _, block ->
                RichContentBlockView(
                    block = block,
                    onUrlClick = openV2exUrl,
                    onImageClick = onImageClick,
                    imageSizeCache = imageSizeCache,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
            if (detail.appends.isNotEmpty()) {
                item(
                    key = TOPIC_DETAIL_APPENDS_KEY,
                    contentType = TOPIC_DETAIL_APPENDS_CONTENT_TYPE,
                ) {
                    TopicAppendsSection(
                        appends = detail.appends,
                        onImageClick = onImageClick,
                        onUrlClick = openV2exUrl,
                        customImageHosts = customImageHosts,
                        imageSizeCache = imageSizeCache,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            item(
                key = TOPIC_DETAIL_REPLY_SUMMARY_KEY,
                contentType = TOPIC_DETAIL_REPLY_SUMMARY_CONTENT_TYPE,
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                )
                ReplySummaryRow(detail = detail, hasMore = hasMoreReplies)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                )
            }
            items(
                items = detail.replies,
                key = { it.id },
                contentType = { TOPIC_DETAIL_REPLY_CONTENT_TYPE },
            ) { reply ->
                ReplyItem(
                    reply = reply,
                    highlighted = highlightedReplyId == reply.id,
                    isTopicAuthor = isReplyFromTopicAuthor(
                        replyUsername = reply.author.username,
                        topicAuthorUsername = detail.topic.author.username,
                    ),
                    onUserClick = onUserClick,
                    onImageClick = onImageClick,
                    showDirectReplyAction = replyEditorOpen,
                    onMoreClick = { onReplyMoreClick(reply) },
                    onReplyClick = { onDirectReplyClick(reply) },
                    onUrlClick = openV2exUrl,
                    onReferenceClick = { reference ->
                        val targetIndex = detail.replies.indexOfFirst { it.id == reference.replyId }
                        if (targetIndex >= 0) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index = replyListStartIndex + targetIndex)
                                highlightedReplyId = reference.replyId
                                delay(1400)
                                if (highlightedReplyId == reference.replyId) {
                                    highlightedReplyId = null
                                }
                            }
                        }
                    },
                )
            }
            if (hasMoreReplies || loadMoreError != null) {
                item(
                    key = "reply-load-more",
                    contentType = TOPIC_DETAIL_LOAD_MORE_CONTENT_TYPE,
                ) {
                    ReplyLoadMoreFooter(
                        isLoading = isLoadingMore,
                        errorMessage = loadMoreError,
                        onRetry = onLoadMore,
                    )
                }
            }
        }
    }
    // 滚动接近已加载内容尾部时自动加载下一页
    LaunchedEffect(listState, hasMoreReplies, isLoadingMore, loadMoreError) {
        if (!hasMoreReplies || isLoadingMore || loadMoreError != null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LOAD_MORE_PREFETCH_ITEMS
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd) onLoadMore()
            }
    }
}

@Composable
private fun ReplyLoadMoreFooter(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            errorMessage != null -> {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = "正在加载更多回复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val LOAD_MORE_PREFETCH_ITEMS = 10
private const val TOPIC_DETAIL_HEADER_KEY = "topic-detail-header"
private const val TOPIC_DETAIL_HEADER_CONTENT_TYPE = "topic-detail-header"
private const val TOPIC_DETAIL_BODY_CONTENT_TYPE = "topic-detail-body"
private const val TOPIC_DETAIL_APPENDS_KEY = "topic-detail-appends"
private const val TOPIC_DETAIL_APPENDS_CONTENT_TYPE = "topic-detail-appends"
private const val TOPIC_DETAIL_REPLY_SUMMARY_KEY = "topic-detail-reply-summary"
private const val TOPIC_DETAIL_REPLY_SUMMARY_CONTENT_TYPE = "topic-detail-reply-summary"
private const val TOPIC_DETAIL_REPLY_CONTENT_TYPE = "topic-detail-reply"
private const val TOPIC_DETAIL_LOAD_MORE_CONTENT_TYPE = "topic-detail-load-more"

@Composable
private fun TopicMetadataRow(
    detail: TopicDetail,
    onUserClick: (String) -> Unit,
    onNodeClick: (String) -> Unit,
) {
    val username = detail.topic.author.username
    val time = formatTopicMetadataTime(detail.topic.createdAtEpochSeconds)
    val nodeChip = topicDetailNodeChip(detail)
    val layout = topicDetailMetadataLayout()
    val primaryColor = MaterialTheme.colorScheme.primary
    // 元信息用单个 Text 渲染，保证发帖人、时间、点击数共享同一基线，
    // 仅发帖人作为文本内的可点击链接；节点入口由末尾的 Chip 独立处理。
    val metadata = buildAnnotatedString {
        var isFirst = true
        fun appendSeparator() {
            if (!isFirst) append(" · ")
            isFirst = false
        }
        if (username.isNotBlank()) {
            appendSeparator()
            withLink(
                LinkAnnotation.Clickable(
                    tag = "author",
                    linkInteractionListener = { onUserClick(username) },
                ),
            ) {
                withStyle(SpanStyle(color = primaryColor)) {
                    append(username)
                }
            }
        }
        if (time.isNotBlank()) {
            appendSeparator()
            append(time)
        }
        detail.viewCount?.let { viewCount ->
            appendSeparator()
            append("$viewCount 次点击")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = metadata,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        nodeChip?.let { chip ->
            NodeChip(
                title = chip.label,
                onClick = { onNodeClick(chip.nodeName) },
                modifier = Modifier.widthIn(max = layout.nodeChipMaxWidth),
            )
        }
    }
}

@Composable
private fun ReplySummaryRow(detail: TopicDetail, hasMore: Boolean) {
    val replyCount = detail.topic.replyCount.takeIf { it > 0 } ?: detail.replies.size
    val summaryText = buildString {
        if (hasMore) append("共 ")
        append(replyCount)
        append(" 条回复")
        if (hasMore) {
            append(" · 已加载 ")
            append(detail.replies.size)
            append(" 条")
        }
        detail.hotReplyCount?.takeIf { it > 0 }?.let { count ->
            append(" · ")
            append(count)
            append(" 条热门回复")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summaryText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.tags.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                detail.tags.forEach { tag ->
                    TopicTagChip(tag = tag)
                }
            }
        }
    }
}

@Composable
private fun TopicTagChip(tag: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalOffer,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TopicAppendsSection(
    appends: List<TopicAppend>,
    onImageClick: (String) -> Unit,
    onUrlClick: (String) -> Boolean,
    customImageHosts: Set<String>,
    imageSizeCache: RichContentImageSizeCache,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        appends.forEach { append ->
            TopicAppendCard(
                append = append,
                onImageClick = onImageClick,
                onUrlClick = onUrlClick,
                customImageHosts = customImageHosts,
                imageSizeCache = imageSizeCache,
            )
        }
    }
}

@Composable
private fun TopicAppendCard(
    append: TopicAppend,
    onImageClick: (String) -> Unit,
    onUrlClick: (String) -> Boolean,
    customImageHosts: Set<String>,
    imageSizeCache: RichContentImageSizeCache,
) {
    val document = remember(append.contentRendered, customImageHosts) {
        RichContentParser.parse(append.contentRendered, customImageHosts)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val timeText = when {
                append.createdAtEpochSeconds != null -> formatTopicMetadataTime(append.createdAtEpochSeconds)
                !append.relativeTime.isNullOrBlank() -> append.relativeTime
                else -> null
            }
            val titleText = buildString {
                append("第 ${append.index} 条附言")
                if (!timeText.isNullOrBlank()) {
                    append(" · ")
                    append(timeText)
                }
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            RichContent(
                document = document,
                imageSizeCache = imageSizeCache,
                onImageClick = onImageClick,
                onUrlClick = onUrlClick,
            )
        }
    }
}
