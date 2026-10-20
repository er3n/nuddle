// Root build: plugin versions come from gradle/libs.versions.toml; formatting covers every module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.openapi.generator) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.play.publisher) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    // Same values as .editorconfig, which Spotless does not pick up from this directory.
    val ktlintRules =
        mapOf(
            "ktlint_code_style" to "ktlint_official",
            "max_line_length" to "140",
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        )
    kotlin {
        target("app/src/**/*.kt")
        ktlint(ktlintVersion).editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(ktlintRules)
    }
}
