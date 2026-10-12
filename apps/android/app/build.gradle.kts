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
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.detekt)
}

android {
    namespace = "app.nuddle"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.nuddle"
        minSdk = 26
        // One below compileSdk so Robolectric (UI tests, screenshots) runs at the target level.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
        }
        create("e2e") {
            dimension = "env"
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", "\"http://localhost:3000/\"")
            // Maestro finds elements by test tag exposed as resource id (docs/testing.md, Android `e2e` flavor).
            buildConfigField("boolean", "EXPOSE_TEST_TAGS", "true")
            // The camera takes the photo the orchestrator put on the phone (docs/testing.md, Android `e2e` flavor).
            buildConfigField("boolean", "INJECTED_PHOTO", "true")
        }
        create("prod") {
            dimension = "env"
            // The production API, also the invite link host (open-questions#13).
            buildConfigField("String", "API_BASE_URL", "\"https://nuddle.app/\"")
            buildConfigField("boolean", "EXPOSE_TEST_TAGS", "false")
            buildConfigField("boolean", "INJECTED_PHOTO", "false")
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

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

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
