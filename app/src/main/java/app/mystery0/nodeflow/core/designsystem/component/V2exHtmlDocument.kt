package app.mystery0.nodeflow.core.designsystem.component

import app.mystery0.nodeflow.core.link.ImageHostMatcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal data class V2exHtmlColors(
    val text: String,
    val secondaryText: String,
    val link: String,
    val background: String,
    val codeBackground: String,
    val quoteBackground: String,
    val border: String,
)

internal fun buildV2exHtmlDocument(
    bodyHtml: String,
    colors: V2exHtmlColors,
    customImageHosts: Collection<String> = emptySet(),
): String {
    val content = Jsoup.parseBodyFragment(bodyHtml, V2EX_BASE_URL).apply {
        select("script").forEach { script ->
            val src = script.attr("src")
            if (!src.startsWith("https://gist.github.com/") && !src.startsWith("//gist.github.com/")) {
                script.remove()
            }
        }
        // 纯文本站内主题链接先转成 <a>，随后与既有链接一起补 target/rel
        body().linkifyPlainV2exTopicLinks()
        // 命中自定义图床域名的锚点后插入同 URL 的 <img>，
        // 由注入脚本接管占位、失败重试与点击预览；锚点保留可点击
        select("a[href]")
            .filter { link ->
                link.select("img").isEmpty() &&
                    ImageHostMatcher.shouldLoadAsImage(link.absUrl("href"), customImageHosts)
            }
            .forEach { link ->
                link.after(Element("img").attr("src", link.absUrl("href")))
            }
        select("a[href]").forEach { link ->
            link.attr("target", "_blank")
            link.attr("rel", "noopener noreferrer")
        }
        select("img[src]").forEach { image ->
            // 立即加载以便可靠触发加载完成/失败回调，配合占位与失败重试
            image.attr("loading", "eager")
            image.attr("decoding", "async")
        }
    }.body().html()
    return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
          <style>
            html {
              margin: 0;
              padding: 0;
              background: ${colors.background};
              color: ${colors.text};
              -webkit-text-size-adjust: 100%;
            }
            body {
              margin: 0;
              padding: 0;
              background: ${colors.background};
              color: ${colors.text};
              font-family: system-ui, -apple-system, BlinkMacSystemFont, "Roboto", "Noto Sans CJK SC", sans-serif;
              font-size: 16px;
              line-height: 1.68;
              word-break: break-word;
              overflow-wrap: anywhere;
            }
            .nodeflow-content {
              /* 建立 BFC，阻止嵌套首尾元素（如 markdown 包装层里的 h1/p）的外边距
                 塌陷逃逸到容器外——逃逸的边距不计入 getBoundingClientRect，
                 会导致高度测量偏小、正文底部被截断 */
              display: flow-root;
            }
            .nodeflow-content > :first-child { margin-top: 0; }
            .nodeflow-content > :last-child { margin-bottom: 0; }
            p { margin: 0 0 14px; }
            a {
              color: ${colors.link};
              text-decoration: none;
            }
            a:active { opacity: 0.72; }
            h1, h2, h3, h4, h5, h6 {
              margin: 18px 0 10px;
              color: ${colors.text};
              line-height: 1.28;
              font-weight: 650;
            }
            h1 { font-size: 1.45em; }
            h2 { font-size: 1.28em; }
            h3 { font-size: 1.12em; }
            ul, ol {
              margin: 0 0 14px;
              padding-left: 22px;
            }
            li { margin: 4px 0; }
            blockquote {
              margin: 0 0 14px;
              padding: 10px 12px;
              border-left: 3px solid ${colors.link};
              border-radius: 6px;
              background: ${colors.quoteBackground};
              color: ${colors.secondaryText};
            }
            pre {
              margin: 0 0 14px;
              padding: 12px;
              border: 1px solid ${colors.border};
              border-radius: 8px;
              background: ${colors.codeBackground};
              color: ${colors.text};
              overflow-x: auto;
              white-space: pre;
              line-height: 1.55;
              font-size: 14px;
            }
            code {
              font-family: "Roboto Mono", "SFMono-Regular", "Consolas", monospace;
              background: ${colors.codeBackground};
              border-radius: 5px;
              padding: 2px 5px;
              font-size: 0.92em;
            }
            pre code {
              display: block;
              padding: 0;
              background: transparent;
              border-radius: 0;
              font-size: inherit;
            }
            img {
              display: block;
              max-width: 100%;
              height: auto;
              margin: 12px 0;
              border-radius: 8px;
            }
            .nf-img {
              position: relative;
              display: block;
              margin: 12px 0;
            }
            .nf-img > img {
              margin: 0;
            }
            .nf-img.nf-loading {
              min-height: 120px;
              border-radius: 8px;
              background: ${colors.codeBackground};
            }
            .nf-img.nf-loading > img { visibility: hidden; }
            .nf-img.nf-loading::after {
              content: "";
              position: absolute;
              left: 50%;
              top: 50%;
              width: 26px;
              height: 26px;
              margin: -13px 0 0 -13px;
              border-radius: 50%;
              border: 3px solid ${colors.border};
              border-top-color: ${colors.link};
              animation: nf-spin 0.8s linear infinite;
            }
            @keyframes nf-spin { to { transform: rotate(360deg); } }
            .nf-img.nf-error {
              min-height: 140px;
              display: flex;
              align-items: center;
              justify-content: center;
              border-radius: 8px;
              background: ${colors.codeBackground};
              cursor: pointer;
            }
            .nf-img.nf-error > img { display: none; }
            .nf-error-box {
              display: flex;
              flex-direction: column;
              align-items: center;
              gap: 8px;
              padding: 16px;
              text-align: center;
              color: ${colors.secondaryText};
            }
            .nf-error-box svg { width: 56px; height: 44px; }
            .nf-error-text { font-size: 13px; }
            table {
              display: block;
              width: 100%;
              margin: 0 0 14px;
              border-collapse: collapse;
              overflow-x: auto;
            }
            th, td {
              border: 1px solid ${colors.border};
              padding: 7px 9px;
              text-align: left;
              vertical-align: top;
            }
            hr {
              height: 1px;
              margin: 18px 0;
              border: 0;
              background: ${colors.border};
            }
            iframe, video {
              max-width: 100%;
              border: 0;
              border-radius: 8px;
            }
          </style>
        </head>
        <body>
          <main class="nodeflow-content">$content</main>
        </body>
        </html>
    """.trimIndent()
}

private const val V2EX_BASE_URL = "https://www.v2ex.com"
