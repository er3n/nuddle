package app.nuddle.data

import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.voice.Heard
import app.nuddle.data.voice.InjectedSpeechInput
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The e2e flavor's speech (docs/testing.md): it says the injected text and ends by itself, like silence (NUD-60). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InjectedSpeechInputTest {
    @Test
    fun `says the injected text and ends by itself without a tap on stop`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            File(context.filesDir, InjectedSpeechInput.FILE).writeText("eren yarın akşam çöpü atsın\n")

            val heard = InjectedSpeechInput(context).listen().toList()

            assertEquals(
                listOf(Heard.Partial("eren yarın akşam çöpü atsın"), Heard.Final("eren yarın akşam çöpü atsın")),
                heard,
            )
        }
}
