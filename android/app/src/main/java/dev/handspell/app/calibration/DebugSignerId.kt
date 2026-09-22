package dev.handspell.app.calibration

/** Opaque research-only signer label; it cannot contain a name or free text. */
@JvmInline
value class DebugSignerId private constructor(val value: String) {
    companion object {
        private val FORMAT = Regex("s[1-9][0-9]{0,5}")
        fun parse(value: String): DebugSignerId? = value.takeIf(FORMAT::matches)?.let(::DebugSignerId)
        val quickChoices: List<DebugSignerId> = (1..5).map { DebugSignerId("s$it") }
    }
}
