package app.nuddle.data

import androidx.test.core.app.ApplicationProvider
import app.nuddle.data.network.CrashlyticsErrorReporter
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.Test
import kotlin.test.assertTrue

/** 6f's code (NUD-39): without a Firebase app (CI, tests) it only goes to the log, and nothing crashes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashlyticsErrorReporterTest {
    @Test
    fun `without Firebase the code goes to the log`() {
        CrashlyticsErrorReporter(ApplicationProvider.getApplicationContext()).report("DRT-5001")
        assertTrue(ShadowLog.getLogsForTag("Nuddle").any { "DRT-5001" in it.msg })
    }
}
