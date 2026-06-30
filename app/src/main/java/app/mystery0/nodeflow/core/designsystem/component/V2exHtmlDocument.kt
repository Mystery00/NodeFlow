package app.mystery0.nodeflow.core.designsystem.component

import org.jsoup.Jsoup

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
): String {
    val content = Jsoup.parseBodyFragment(bodyHtml, V2EX_BASE_URL).apply {
        select("script").forEach { script ->
            val src = script.attr("src")
            if (!src.startsWith("https://gist.github.com/") && !src.startsWith("//gist.github.com/")) {
                script.remove()
            }
        }
        select("a[href]").forEach { link ->
            link.attr("target", "_blank")
            link.attr("rel", "noopener noreferrer")
        }
        select("img[src]").forEach { image ->
            image.attr("loading", "lazy")
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
