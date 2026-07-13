package app.mystery0.nodeflow.core.parser

import org.jsoup.nodes.Document

internal fun Document.hasRestrictedSignInForm(): Boolean =
    select("form[action='/signin']").any { form ->
        form.selectFirst("input[type=password]") != null ||
            form.selectFirst("input[type=hidden][name=next]")
                ?.attr("value") == "/restricted"
    }
