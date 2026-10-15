package app.nuddle.e2e

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import app.nuddle.BuildConfig
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Fake time and the orchestrator's receiver exist only in the `e2e` flavor (NUD-65); unit tests build `dev`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class E2eHooksTest {
    @Test
    fun `a build other than e2e runs on real time and has no E2E receiver`() {
        assertFalse(BuildConfig.FAKE_TIME)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiver =
            runCatching {
                context.packageManager.getReceiverInfo(ComponentName(context, E2eReceiver::class.java), 0)
            }.getOrNull()
        assertNull(receiver)
    }
}
