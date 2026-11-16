package com.example.davidapp.live

/** Only explicit commands can commit or cancel the currently reviewed proposal. */
internal enum class DavidLiveOperationCommand {
    CONFIRM, CANCEL;

    companion object {
        fun parse(text: String): DavidLiveOperationCommand? = when (
            text.lowercase(java.util.Locale.ROOT).trim().trimEnd('.', '!', '?')
        ) {
            "подтверждаю запись" -> CONFIRM
            "отмени запись" -> CANCEL
            else -> null
        }
    }
}
