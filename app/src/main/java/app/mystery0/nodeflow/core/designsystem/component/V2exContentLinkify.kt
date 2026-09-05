package app.mystery0.nodeflow.core.designsystem.component

import app.mystery0.nodeflow.core.parser.linkifyV2exTopicReferencesHtml

/**
 * 识别正文/回复里以纯文本出现的站内主题链接（如 `/t/1226857`、`www.v2ex.com/t/1226857`），
 * 转成绝对地址的 `<a>` 标签，点击后由现有 [app.mystery0.nodeflow.core.link.V2exLinkParser]
 * 路由到应用内主题详情。已有链接、代码块内的文本不做处理。
 */
internal fun linkifyV2exTopicReferences(html: String): String {
    return linkifyV2exTopicReferencesHtml(html)
}
