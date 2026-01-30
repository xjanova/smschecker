package com.thaiprompt.smschecker.ui.theme

enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun fromKey(key: String): ThemeMode =
            entries.find { it.key == key } ?: DARK
    }
}
