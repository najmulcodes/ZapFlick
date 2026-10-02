package com.najmulcodes.zapflick.domain.usecase

import javax.inject.Inject

/** Pulls the first http(s) link out of arbitrary text (share intents often add a caption). */
class ExtractUrlUseCase @Inject constructor() {
    operator fun invoke(text: String): String? {
        val match = URL_REGEX.find(text) ?: return null
        return match.value.trimEnd('.', ',', ';', ')', ']', '>', '!', '"', '\'')
    }

    private companion object {
        val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    }
}
