package app.mystery0.nodeflow.core.designsystem.component

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player.Listener
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player
import app.mystery0.nodeflow.R
import app.mystery0.nodeflow.core.model.RichEmbed
import app.mystery0.nodeflow.core.model.RichImage
import app.mystery0.nodeflow.core.model.RichVideo
import app.mystery0.nodeflow.core.model.RichVideoSource
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

internal class RichContentImageSizeCache {
    private val ratios = mutableMapOf<String, Float>()

    fun put(url: String, width: Int, height: Int) {
        if (width > 0 && height > 0) ratios[url] = width.toFloat() / height
    }

    fun aspectRatio(url: String, width: Int?, height: Int?): Float {
        ratios[url]?.let { return it }
        if (width != null && height != null && width > 0 && height > 0) {
            return (width.toFloat() / height).coerceIn(
                MIN_DECLARED_MEDIA_ASPECT_RATIO,
                MAX_DECLARED_MEDIA_ASPECT_RATIO,
            )
        }
        return DEFAULT_MEDIA_ASPECT_RATIO
    }
}

@Composable
internal fun rememberRichContentImageSizeCache(key: Any?): RichContentImageSizeCache =
    remember(key) { RichContentImageSizeCache() }

internal fun selectVideoSource(sources: List<RichVideoSource>): RichVideoSource? {
    return sources.firstOrNull { source ->
        source.normalizedMimeType() == "video/mp4" ||
            source.url.substringBefore('?').endsWith(".mp4", true)
    } ?: sources.firstOrNull { source ->
        source.normalizedMimeType()?.contains("mpegurl", ignoreCase = true) == true ||
            source.url.substringBefore('?').endsWith(".m3u8", true)
    } ?: sources.firstOrNull()
}

private fun RichVideoSource.normalizedMimeType(): String? =
    mimeType?.substringBefore(';')?.trim()?.lowercase()?.takeIf(String::isNotEmpty)

@Composable
internal fun RichBlockImage(
    image: RichImage,
    sizeCache: RichContentImageSizeCache,
    onImageClick: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var retryKey by remember(image.url) { mutableIntStateOf(0) }
    val aspectRatio = sizeCache.aspectRatio(image.url, image.widthPx, image.heightPx)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.widthIn(max = maxWidth)) {
            SubcomposeAsyncImage(
                model = "${image.url}#retry=$retryKey",
                contentDescription = image.alt,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImageClick(image.url) },
            ) {
                when (val state = painter.state) {
                    is AsyncImagePainter.State.Success -> {
                        val drawable = state.result.drawable
                        sizeCache.put(image.url, drawable.intrinsicWidth, drawable.intrinsicHeight)
                        SubcomposeAsyncImageContent()
                    }
                    is AsyncImagePainter.State.Error -> ContentImageErrorPlaceholder(
                        onRetry = { retryKey += 1 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio),
                    )
                    else -> ContentImageLoadingPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio),
                    )
                }
            }
            image.linkUrl?.let { linkUrl ->
                IconButton(
                    onClick = { onOpenUrl(linkUrl) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.rich_open_image_link),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun RichVideoPlayer(
    video: RichVideo,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember(video.sources) { selectVideoSource(video.sources) }
    var activated by remember(source?.url) { mutableStateOf(false) }
    if (source == null) {
        RichEmbedPlaceholder(
            embed = RichEmbed(video.openUrl, video.title ?: stringResource(R.string.rich_video_unavailable)),
            onOpenUrl = onOpenUrl,
            showUnsupportedDescription = false,
            modifier = modifier,
        )
    } else if (!activated) {
        VideoPoster(
            video = video,
            openUrl = source.url,
            onPlay = { activated = true },
            onOpenUrl = onOpenUrl,
            modifier = modifier,
        )
    } else {
        ActiveVideoPlayer(
            source = source,
            onOpenUrl = onOpenUrl,
            modifier = modifier,
        )
    }
}

@Composable
private fun VideoPoster(
    video: RichVideo,
    openUrl: String,
    onPlay: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .aspectRatio(DEFAULT_MEDIA_ASPECT_RATIO)
            .clickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        if (video.posterUrl != null) {
            SubcomposeAsyncImage(
                model = video.posterUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                    else -> Unit
                }
            }
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.rich_video_play),
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(
            onClick = { onOpenUrl(openUrl) },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.rich_open_in_browser),
                tint = Color.White,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ActiveVideoPlayer(
    source: RichVideoSource,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackError by remember(source.url) { mutableStateOf(false) }
    var retryAttempt by remember(source.url) { mutableIntStateOf(0) }
    val player = remember(source.url, retryAttempt) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(source.url)
                    .setMimeType(source.normalizedMimeType())
                    .build(),
            )
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    if (playbackError) {
        RichEmbedPlaceholder(
            embed = RichEmbed(source.url, stringResource(R.string.rich_video_failed)),
            onOpenUrl = onOpenUrl,
            onRetry = {
                playbackError = false
                retryAttempt += 1
            },
            showUnsupportedDescription = false,
            modifier = modifier,
        )
    } else {
        Player(
            player = player,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(DEFAULT_MEDIA_ASPECT_RATIO),
        )
    }
}

@Composable
internal fun RichEmbedPlaceholder(
    embed: RichEmbed,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    showUnsupportedDescription: Boolean = true,
    onRetry: (() -> Unit)? = null,
) {
    val url = embed.url
    val fallbackText = stringResource(
        if (url != null) R.string.rich_embed_unsupported else R.string.rich_embed_unavailable,
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (url != null && onRetry == null) Modifier.clickable { onOpenUrl(url) } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = embed.title ?: fallbackText,
                style = MaterialTheme.typography.titleSmall,
            )
            if (embed.title != null && showUnsupportedDescription) {
                Text(
                    text = fallbackText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (url != null) {
                Text(
                    text = url.toUri().host ?: url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (onRetry != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.rich_retry))
                    }
                    if (url != null) {
                        TextButton(onClick = { onOpenUrl(url) }) {
                            Text(stringResource(R.string.rich_open_in_browser))
                        }
                    }
                }
            }
        }
    }
}

private const val DEFAULT_MEDIA_ASPECT_RATIO = 16f / 9f
private const val MIN_DECLARED_MEDIA_ASPECT_RATIO = 0.05f
private const val MAX_DECLARED_MEDIA_ASPECT_RATIO = 20f
