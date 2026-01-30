package com.thaiprompt.smschecker.ui.theme

enum class LanguageMode(val key: String) {
    THAI("th"),
    ENGLISH("en"),
    SYSTEM("system");

    companion object {
        fun fromKey(key: String): LanguageMode =
            entries.find { it.key == key } ?: THAI
    }
}
