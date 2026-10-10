package app.mystery0.nodeflow.core.designsystem.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mystery0.nodeflow.R
import app.mystery0.nodeflow.core.model.RichInline
import app.mystery0.nodeflow.core.parser.decodeReadableBase64
import app.mystery0.nodeflow.core.parser.recognizeContentText
import app.mystery0.nodeflow.core.parser.safeContentUrl

/** 只在当前页面内存中保存已展开内容；离屏重组不重置，不进入保存状态、日志或数据库。 */
@Stable
internal class Base64RevealState {
    private val expanded = mutableStateMapOf<String, String>()

    fun decoded(token: RichInline.Base64Text): String? = expanded[token.key]

    fun toggle(token: RichInline.Base64Text): Boolean {
        if (expanded.remove(token.key) != null) return true
        val decoded = decodeReadableBase64(token.encoded) ?: return false
        expanded[token.key] = decoded
        return true
    }
}

internal val LocalBase64RevealState = staticCompositionLocalOf<Base64RevealState?> { null }

/** 真正的布局和按钮，短内容可放进行内占位，长内容在原位置按块排版。 */
@Composable
internal fun Base64RevealContent(
    token: RichInline.Base64Text,
    state: Base64RevealState,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    bodyStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val decoded = state.decoded(token)
    val expanded = decoded != null
    val context = LocalContext.current
    val showLabel = stringResource(R.string.base64_show)
    val hideLabel = stringResource(R.string.base64_hide)
    val stateLabel = stringResource(if (expanded) R.string.base64_expanded else R.string.base64_collapsed)
    val failedLabel = stringResource(R.string.base64_failed)
    val colors = MaterialTheme.colorScheme
    val text = if (decoded == null) AnnotatedString(token.encoded) else buildAnnotatedString {
        var end = 0
        // 解码只呈现文本，链接是独立点击目标；不递归解码、不执行 HTML。
        recognizeContentText(decoded, includeBase64 = false).forEach { match ->
            append(decoded.substring(end, match.start))
            val target = requireNotNull(match.target)
            withLink(
                LinkAnnotation.Clickable(
                    tag = "decoded-link-${match.start}",
                    styles = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { safeContentUrl(target)?.let(onUrlClick) },
                ),
            ) { append(decoded.substring(match.start, match.end)) }
            end = match.end
        }
        append(decoded.substring(end))
    }
    Box(modifier = modifier.semantics { stateDescription = stateLabel }) {
        // 底色比实际布局薄；48dp 触控区完整计入布局，不能靠越界扩展覆盖相邻文字。
        Box(
            Modifier.matchParentSize()
                .padding(vertical = BASE64_BACKGROUND_INSET)
                .clip(MaterialTheme.shapes.small)
                .background(colors.surfaceVariant.copy(alpha = 0.45f)),
        )
        Row(verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top) {
            Text(
                text = text,
                modifier = Modifier.weight(1f, fill = false).padding(
                    start = BASE64_TEXT_START_PADDING,
                    top = BASE64_TEXT_VERTICAL_PADDING,
                    bottom = BASE64_TEXT_VERTICAL_PADDING,
                ),
                style = base64TextStyle(token, expanded, bodyStyle),
                color = colors.onSurface,
                softWrap = !singleLine,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            )
            IconButton(
                onClick = {
                    if (!state.toggle(token)) Toast.makeText(context, failedLabel, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(BASE64_ACTION_SIZE),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    contentDescription = if (expanded) hideLabel else showLabel,
                    modifier = Modifier.size(20.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** 测量与显示共用同一字形规格，保留片段自身的强调样式。 */
@Composable
internal fun base64TextStyle(
    token: RichInline.Base64Text,
    expanded: Boolean,
    base: TextStyle = MaterialTheme.typography.bodyLarge,
): TextStyle {
    return base.copy(
        fontFamily = if (expanded) base.fontFamily else FontFamily.Monospace,
        letterSpacing = if (expanded) base.letterSpacing else 0.sp,
        fontSize = base.fontSize * token.style.fontScale,
        fontWeight = if (token.style.bold) FontWeight.Bold else base.fontWeight,
        fontStyle = if (token.style.italic) FontStyle.Italic else base.fontStyle,
        textDecoration = buildList {
            if (token.style.underline) add(TextDecoration.Underline)
            if (token.style.strikethrough) add(TextDecoration.LineThrough)
        }.takeIf { it.isNotEmpty() }?.let(TextDecoration::combine),
    )
}

internal val BASE64_ACTION_SIZE = 48.dp
internal val BASE64_TEXT_START_PADDING = 8.dp
internal val BASE64_TEXT_VERTICAL_PADDING = 6.dp
private val BASE64_BACKGROUND_INSET = 4.dp
