// Root build file — AGP 9 has built-in Kotlin support; only the Compose plugin is added
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
