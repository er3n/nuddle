// Generated API client (ADR 0008): Kotlin + Retrofit + kotlinx.serialization from packages/contract/openapi.yaml.
// Generated sources are not committed; they are rebuilt whenever the contract changes.
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

val contract = rootProject.layout.projectDirectory.file("../../packages/contract/openapi.yaml")
val generatedDir = layout.buildDirectory.dir("generated/openapi")

val generateApi =
    tasks.named<GenerateTask>("openApiGenerate") {
        generatorName = "kotlin"
        library = "jvm-retrofit2"
        inputSpec = contract
        outputDir = generatedDir
        packageName = "app.nuddle.api"
        apiPackage = "app.nuddle.api"
        modelPackage = "app.nuddle.api.model"
        // Ids and timestamps stay strings on the wire; the app parses them where it needs types.
        typeMappings = mapOf("UUID" to "kotlin.String", "URI" to "kotlin.String")
        configOptions =
            mapOf(
                "serializationLibrary" to "kotlinx_serialization",
                "useCoroutines" to "true",
                "dateLibrary" to "string",
                "enumPropertyNaming" to "UPPERCASE",
                "sourceFolder" to "src/main/kotlin",
            )
        inputs.file(contract).withPathSensitivity(PathSensitivity.RELATIVE)
        // Free-form objects (`additionalProperties: true`, e.g. SyncEvent.payload) come out as Map<String, Any>, which
        // kotlinx.serialization cannot serialize. Replace them with JsonObject.
        val modelDir = generatedDir.map { it.dir("src/main/kotlin/app/nuddle/api/model").asFile }
        doLast {
            modelDir.get().walk().filter { it.extension == "kt" }.forEach { file ->
                val text = file.readText()
                val fixed =
                    text.replace(
                        "kotlin.collections.Map<kotlin.String, kotlin.Any>",
                        "kotlinx.serialization.json.JsonObject",
                    )
                if (fixed != text) file.writeText(fixed)
            }
        }
    }

sourceSets.main {
    kotlin.srcDir(generateApi.map { it.outputDir.dir("src/main/kotlin") })
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Not allWarningsAsErrors here, unlike :app: this module holds only generated code, which we do not edit.
        allWarningsAsErrors = false
        freeCompilerArgs.add("-nowarn")
    }
}

dependencies {
    api(libs.retrofit)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit.kotlinx.serialization)
    // Used only by the generated ApiClient, which the app does not use (it builds its own Retrofit).
    implementation(libs.retrofit.scalars)
    implementation(libs.okhttp.logging)
}
