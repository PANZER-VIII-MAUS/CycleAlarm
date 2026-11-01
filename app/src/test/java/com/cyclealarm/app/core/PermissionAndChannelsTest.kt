package com.cyclealarm.app.core

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionAndChannelsTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Test
    fun `notifications disabled is reported on every api level`() {
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(PermissionHelper.notificationsGranted(app))

        shadowOf(manager).setNotificationsEnabled(true)
        assertTrue(PermissionHelper.notificationsGranted(app))
    }

    @Test
    fun `createAll registers the three channels`() {
        NotificationChannels.createAll(app)

        listOf(
            NotificationChannels.RING,
            NotificationChannels.RING_FALLBACK,
            NotificationChannels.GUARD
        ).forEach { id ->
            assertNotNull("渠道 $id 应存在", manager.getNotificationChannel(id))
        }
    }
}
