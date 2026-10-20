package app.nuddle.architecture

import androidx.compose.runtime.Composable
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/** Layering and UI rules from docs/quality.md (Kotlin, Konsist). Run as unit tests. */
class ArchitectureTest {
    private companion object {
        // Written as an escape so this file does not contain the character it bans.
        const val EM_DASH = '\u2014'
        val PREVIEW_ANNOTATIONS = setOf("Preview", "PreviewComponent", "PreviewScreens")
    }

    private val main = Konsist.scopeFromProduction()

    @Test
    fun `domain does not import android`() {
        main.files.filter { it.packagee?.name?.startsWith("app.nuddle.domain") == true }.assertFalse { file ->
            file.imports.any { it.name.startsWith("android.") || it.name.startsWith("androidx.") }
        }
    }

    @Test
    fun `screens take UiState and lambdas, never a ViewModel`() {
        main
            .functions()
            .withAnnotationOf(Composable::class)
            .filter { it.name.endsWith("Screen") && it.resideInPackage("app.nuddle.ui.screens..") }
            .assertFalse { screen -> screen.parameters.any { it.type.name.endsWith("ViewModel") } }
    }

    @Test
    fun `no string literals are passed to Text`() {
        val literalText = Regex("""\bText\(\s*(text\s*=\s*)?"""")
        main.files.filter { it.packagee?.name?.startsWith("app.nuddle.ui") == true }.assertFalse { file ->
            literalText.containsMatchIn(file.text)
        }
    }

    @Test
    fun `colors are only constructed in the theme package`() {
        val colorCall = Regex("""(?<![\w.])Color\(""")
        main.files.filter { it.packagee?.name != "app.nuddle.ui.theme" }.assertFalse { file ->
            colorCall.containsMatchIn(file.text)
        }
    }

    @Test
    fun `no em dash anywhere in the app`() {
        Konsist.scopeFromProject().files.assertFalse { it.text.contains(EM_DASH) }
    }

    @Test
    fun `every public component has a preview`() {
        val components = main.files.filter { it.packagee?.name == "app.nuddle.ui.components" }
        components.assertTrue { file ->
            val previews =
                file
                    .functions()
                    .filter { fn -> fn.annotations.any { it.name in PREVIEW_ANNOTATIONS } }
                    .map { it.text }
            file
                .functions()
                .withAnnotationOf(Composable::class)
                .filter { it.hasPublicOrDefaultModifier }
                .all { component ->
                    previews.any { body ->
                        body.contains("${component.name}(") ||
                            body.contains("${component.name}Content(")
                    }
                }
        }
    }

    @Test
    fun `every activity draws edge to edge`() {
        // Android 15 forces edge to edge on apps targeting SDK 35; older phones get the same layout only through
        // enableEdgeToEdge() (Play's edge-to-edge check, NUD-118).
        main
            .classes()
            .filter { cls -> cls.parents().any { it.name.endsWith("Activity") } }
            .assertTrue { it.text.contains("enableEdgeToEdge()") }
    }

    @Test
    fun `every screen keeps clear of the system bars`() {
        // ScreenLayout pads with safeDrawingPadding(); a screen that skips it pads itself.
        val insets = Regex("""\b(ScreenLayout|safeDrawingPadding|systemBarsPadding|windowInsetsPadding)\(""")
        main.files
            .filter {
                it.packagee?.name?.startsWith("app.nuddle.ui.screens.") == true &&
                    it.name.endsWith("Screen")
            }.assertTrue { insets.containsMatchIn(it.text) }
    }

    @Test
    fun `every screen state has a preview`() {
        main.files
            .filter {
                it.packagee?.name?.startsWith("app.nuddle.ui.screens.") == true &&
                    it.name.endsWith("Screen")
            }.assertTrue { file ->
                val composables = file.functions().withAnnotationOf(Composable::class)
                composables.any { fn -> fn.annotations.any { it.name == "PreviewScreens" } }
            }
    }
}
