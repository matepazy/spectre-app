package com.matepazy.spectre

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.matepazy.spectre.provider.InstalledAppsProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalledAppsProviderTest {

    @Test
    fun testSocialPresenceMapping() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val shadowPm = Shadows.shadowOf(pm)

        // Mock TG presence
        val telegramIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tg://resolve"))
        val telegramResolve = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "org.telegram.messenger"
                name = "TelegramActivity"
            }
        }
        shadowPm.addResolveInfoForIntent(telegramIntent, telegramResolve)

        // Mock Signal presence
        val signalIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("signal://"))
        val signalResolve = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "org.thoughtcrime.securesms"
                name = "SignalActivity"
            }
        }
        shadowPm.addResolveInfoForIntent(signalIntent, signalResolve)

        val provider = InstalledAppsProvider()
        val signals = provider.provideSignals(context)

        val sidechannelSignal = signals.find { it.id == "installed_apps_sidechannel" }
        assertNotNull("Sidechannel signal should not be null", sidechannelSignal)
        assertTrue(
            "Sidechannel should detect 2 platforms, raw value: ${sidechannelSignal!!.rawValue}",
            sidechannelSignal.rawValue.contains("2 platforms")
        )

        val detailedData = sidechannelSignal.detailedData
        assertNotNull("Detailed data should not be null", detailedData)
        val items = detailedData!![0].items

        val telegramItem = items.find { it.label == "Telegram" }
        assertNotNull("Telegram should be in detailed items", telegramItem)
        assertEquals("Active Footprint Detected", telegramItem!!.value)

        val signalItem = items.find { it.label == "Signal" }
        assertNotNull("Signal should be in detailed items", signalItem)
        assertEquals("Active Footprint Detected", signalItem!!.value)

        val snapchatItem = items.find { it.label == "Snapchat" }
        assertNotNull("Snapchat should be in detailed items", snapchatItem)
        assertEquals("Not Detected (Sandboxed)", snapchatItem!!.value)
    }
}
