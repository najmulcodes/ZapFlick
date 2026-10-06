package com.najmulcodes.zapflick.domain.browser

/** The page shown when a site cannot be loaded. The texts come from resources, so they are passed in. */
object ErrorPageHtml {

    fun build(title: String, message: String, retryLabel: String, url: String): String {
        val safeUrl = escape(url)
        return """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body { margin:0; padding:32px 24px; font-family:sans-serif; background:#0D1014; color:#E3E5EB; text-align:center; }
              h1 { font-size:20px; margin:48px 0 8px; }
              p { color:#A9AFBD; line-height:1.5; word-break:break-word; }
              a.retry { display:inline-block; margin-top:24px; padding:12px 28px; border-radius:24px;
                        background:#2A3AFD; color:#fff; text-decoration:none; font-weight:bold; }
            </style></head><body>
            <h1>${escape(title)}</h1>
            <p>${escape(message)}</p>
            <p>$safeUrl</p>
            <a class="retry" href="$safeUrl">${escape(retryLabel)}</a>
            </body></html>
        """.trimIndent()
    }

    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
