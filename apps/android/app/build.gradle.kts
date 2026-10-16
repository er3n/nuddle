// The Android app (ADR 0004). Flavors: dev and e2e run as app.nuddle.debug against the local API, prod is app.nuddle.
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.detekt)
}

// The Play upload key lives outside the repo (CLAUDE.md rule 11, docs/human-checklist.md, Before the beta):
// Gradle properties in ~/.gradle/gradle.properties on the dev machine, or environment variables in CI.
// Without them the build still configures; only bundleProdRelease refuses to run.
fun uploadKeyValue(
    property: String,
    env: String,
): String? = providers.gradleProperty(property).orElse(providers.environmentVariable(env)).orNull

val uploadStoreFile = uploadKeyValue("nuddle.upload.storeFile", "NUDDLE_UPLOAD_STORE_FILE")
val uploadStorePassword = uploadKeyValue("nuddle.upload.storePassword", "NUDDLE_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = uploadKeyValue("nuddle.upload.keyAlias", "NUDDLE_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = uploadKeyValue("nuddle.upload.keyPassword", "NUDDLE_UPLOAD_KEY_PASSWORD")
val hasUploadKey =
    listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword).all { !it.isNullOrBlank() }

/** Crashlytics collection and the app's breadcrumbs and non-fatals (ADR 0023) are on or off together. */
fun com.android.build.api.dsl.ProductFlavor.crashReports(enabled: Boolean) {
    manifestPlaceholders["crashlyticsEnabled"] = "$enabled"
    buildConfigField("boolean", "CRASH_REPORTS", "$enabled")
}

android {
    namespace = "app.nuddle"
    compileSdk = 37

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(checkNotNull(uploadStoreFile))
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Play App Signing re-signs with the app signing key; this only proves the upload came from us.
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    defaultConfig {
        applicationId = "app.nuddle"
        minSdk = 26
        // One below compileSdk so Robolectric (UI tests, screenshots) runs at the target level.
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".debug"
            // The emulator reaches `mise run dev` through `adb reverse tcp:3000 tcp:3000`.
            buildConfigField("String", "API_BASE_URL", "\"http://localhost:3000/\"")
            buildConfigField("boolean", "EXPOSE_TEST_TAGS", "false")
            buildConfigField("boolean", "INJECTED_PHOTO", "false")
            buildConfigField("boolean", "INJECTED_SPEECH", "false")
            buildConfigField("boolean", "FAKE_TIME", "false")
            crashReports(true)
        }
        create("e2e") {
            dimension = "env"
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", "\"http://localhost:3000/\"")
            // E2E flows find elements by test tag exposed as resource id (docs/testing.md, Android `e2e` flavor).
            buildConfigField("boolean", "EXPOSE_TEST_TAGS", "true")
            // The camera takes the photo the orchestrator put on the phone (docs/testing.md, Android `e2e` flavor).
            buildConfigField("boolean", "INJECTED_PHOTO", "true")
            // Voice input "hears" the text the orchestrator put on the phone (docs/testing.md, Android `e2e` flavor).
            buildConfigField("boolean", "INJECTED_SPEECH", "true")
            // Time is the orchestrator's to move, on the phone as on the server (docs/testing.md, Fake time).
            buildConfigField("boolean", "FAKE_TIME", "true")
            // Smoke runs are not crashes worth a report, nor are their breadcrumbs (NUD-39, ADR 0023).
            crashReports(false)
        }
        create("prod") {
            dimension = "env"
            // The production API, also the invite link host (open-questions#13).
            buildConfigField("String", "API_BASE_URL", "\"https://nuddle.app/\"")
            buildConfigField("boolean", "EXPOSE_TEST_TAGS", "false")
            buildConfigField("boolean", "INJECTED_PHOTO", "false")
            buildConfigField("boolean", "INJECTED_SPEECH", "false")
            buildConfigField("boolean", "FAKE_TIME", "false")
            crashReports(true)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                // Robolectric touches FileDescriptor internals, which JDK 21 no longer exports by default.
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = false
        error +=
            setOf(
                "NewApi",
                "MissingPermission",
                "ScheduleExactAlarm",
                "ExactAlarm",
                "NotificationPermission",
            )
        // Dependency versions are Renovate's job (renovate.json); a newer release must not fail the build.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*.md")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// google-services.json is not committed (CLAUDE.md rule 11). Without it (CI, a fresh clone) the build still works
// and FCM registration is skipped at runtime.
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

roborazzi {
    outputDir = layout.projectDirectory.dir("screenshots")
    // Reason: the preview test generator has no stable API yet; it is Roborazzi's only one.
    @Suppress("UnstableApiUsage")
    generateComposePreviewRobolectricTests {
        enable = true
        packages = listOf("app.nuddle.ui")
        includePrivatePreviews = true
        robolectricConfig = mapOf("sdk" to "[36]", "qualifiers" to "RobolectricDeviceQualifiers.Pixel5")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    implementation(project(":api"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.androidx.exifinterface)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.installations)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.roborazzi.preview.scanner.support)
    testImplementation(libs.preview.scanner)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.work.testing)
    testImplementation(libs.konsist)

    detektPlugins(libs.compose.rules.detekt)
}

// An unsigned bundle is useless to Play; fail early with the fix instead of at upload time.
tasks.matching { it.name == "bundleProdRelease" }.configureEach {
    val signed = hasUploadKey
    doFirst {
        check(signed) {
            "No Play upload key. Set nuddle.upload.* in ~/.gradle/gradle.properties " +
                "(docs/human-checklist.md, Before the beta)."
        }
    }
}
