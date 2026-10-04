package com.najmulcodes.zapflick.domain.viewer

/** Why a document could not be shown. Each value maps to one message on screen. */
enum class ViewerError(val canOpenElsewhere: Boolean) {
    /** The temporary permission from the sending app is gone (process death) or was refused. */
    AccessLost(canOpenElsewhere = false),

    /** The file is missing or the provider could not open it. */
    NotReadable(canOpenElsewhere = false),

    PasswordProtected(canOpenElsewhere = true),
    Corrupt(canOpenElsewhere = true),
    Unsupported(canOpenElsewhere = true),

    /** An HTML file bigger than [HtmlLimits.MAX_BYTES]. */
    TooLarge(canOpenElsewhere = true),

    /** The Intent carried no usable content: or file: link. */
    NoDocument(canOpenElsewhere = false),
}

class ViewerException(
    val error: ViewerError,
    cause: Throwable? = null,
) : Exception(error.name, cause)
