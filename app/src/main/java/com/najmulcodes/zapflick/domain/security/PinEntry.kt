package com.najmulcodes.zapflick.domain.security

/** The digits typed so far on the PIN keypad. */
data class PinEntry(val digits: String = "") {
    val isComplete: Boolean get() = digits.length == PinPolicy.PIN_LENGTH

    /** Ignores anything that is not a digit, and anything past the fourth. */
    fun press(key: Char): PinEntry =
        if (key in '0'..'9' && digits.length < PinPolicy.PIN_LENGTH) PinEntry(digits + key) else this

    fun backspace(): PinEntry = PinEntry(digits.dropLast(1))
}

/** Where the "set a PIN" screen is: asking for a PIN, or asking for it again. */
sealed interface SetPinStep {
    data object Enter : SetPinStep
    data class Confirm(val first: String) : SetPinStep
}

sealed interface SetPinOutcome {
    /** The first PIN is in; ask for it again. */
    data class AskAgain(val step: SetPinStep.Confirm) : SetPinOutcome

    /** Both entries match: save [pin]. */
    data class Save(val pin: String) : SetPinOutcome

    /** The two entries differ: start over. */
    data object Mismatch : SetPinOutcome
}

object SetPinFlow {
    fun onComplete(step: SetPinStep, entered: String): SetPinOutcome = when (step) {
        SetPinStep.Enter -> SetPinOutcome.AskAgain(SetPinStep.Confirm(entered))
        is SetPinStep.Confirm ->
            if (step.first == entered && PinPolicy.isValid(entered)) SetPinOutcome.Save(entered) else SetPinOutcome.Mismatch
    }
}
