package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import app.mystery0.nodeflow.core.link.ImageHostMatcher

/**
 * 生效的图床域名集合（内置常用图床 ∪ 用户配置），由应用根部从设置流提供；
 * HtmlText 与原生正文渲染器用它把命中域名的链接按图片渲染。
 */
val LocalCustomImageHosts: ProvidableCompositionLocal<Set<String>> =
    compositionLocalOf { ImageHostMatcher.BUILT_IN_IMAGE_HOSTS }

/** 内置图床与用户配置合并出参与匹配的完整域名集合。 */
fun effectiveImageHosts(userHosts: Collection<String>): Set<String> =
    ImageHostMatcher.BUILT_IN_IMAGE_HOSTS + userHosts
