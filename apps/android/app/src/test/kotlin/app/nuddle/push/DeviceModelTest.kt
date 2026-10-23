package app.nuddle.push

import kotlin.test.Test
import kotlin.test.assertEquals

/** The phone's name in Cihazlarım (5e, ADR 0027), from Build.MANUFACTURER and Build.MODEL. */
class DeviceModelTest {
    @Test
    fun `the maker goes before a model that does not name it`() {
        assertEquals("Samsung SM-S911B", deviceModel("samsung", "SM-S911B"))
        assertEquals("Google Pixel 8", deviceModel("Google", "Pixel 8"))
    }

    @Test
    fun `a model that already starts with the maker is kept as it is`() {
        assertEquals("Xiaomi 13T", deviceModel("Xiaomi", "Xiaomi 13T"))
        assertEquals("sdk_gphone64_arm64", deviceModel("", "sdk_gphone64_arm64"))
    }
}
