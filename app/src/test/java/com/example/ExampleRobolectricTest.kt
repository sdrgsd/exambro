package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ExamPreferences
import com.example.model.ExamSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ExamBrowser", appName)
    }

    @Test
    fun `preferences save and restore settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ExamPreferences(context)

        val custom = ExamSettings(
            url = "http://192.168.1.50:8080",
            isFullscreen = false,
            isKeepScreenOn = true,
            isLockBackPress = false,
            isAllowZoom = true,
            isDesktopMode = true
        )
        prefs.saveSettings(custom)

        val restored = prefs.getSettings()
        assertEquals("http://192.168.1.50:8080", restored.url)
        assertEquals(false, restored.isFullscreen)
        assertEquals(true, restored.isKeepScreenOn)
        assertEquals(false, restored.isLockBackPress)
        assertEquals(true, restored.isDesktopMode)
    }

    @Test
    fun `preferences save and list bookmarks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ExamPreferences(context)

        prefs.addSavedUrl("CBT Lab", "192.168.1.200")
        val urls = prefs.getSavedUrls()
        assertNotNull(urls.find { it.label == "CBT Lab" })
    }

    @Test
    fun `preferences save and get last url`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ExamPreferences(context)

        prefs.saveLastUrl("http://192.168.1.100")
        assertEquals("http://192.168.1.100", prefs.getLastUrl())
    }

    @Test
    fun `qr code generator produces non-null bitmap`() {
        val token = com.example.util.QrCodeGenerator.getCurrentExitToken()
        val bitmap = com.example.util.QrCodeGenerator.generateQrBitmap(token, 200)
        assertNotNull(bitmap)
    }
}
