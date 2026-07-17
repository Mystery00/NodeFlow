package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 用户配置的自定义图床域名集合，由应用根部从设置流提供；
 * HtmlText/RichHtmlText 用它把命中域名的链接按图片渲染。
 */
val LocalCustomImageHosts: ProvidableCompositionLocal<Set<String>> =
    compositionLocalOf { emptySet() }
