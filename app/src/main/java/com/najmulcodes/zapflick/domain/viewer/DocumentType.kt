package com.najmulcodes.zapflick.domain.viewer

/** What ZapFlick can show. [Unsupported] means "ask another app". */
enum class DocumentType(val mimeType: String) {
    Pdf("application/pdf"),
    Html("text/html"),
    Unsupported("*/*"),
}
