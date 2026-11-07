package com.cyclealarm.app.core

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SharedPreferences 类存储的真实读写（Robolectric，无需真机）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorePreferencesRobolectricTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `install marker persists`() {
        assertFalse(InstallMarker.hasMarker(context))
        InstallMarker.markInstalled(context)
        assertTrue(InstallMarker.hasMarker(context))
    }

    @Test
    fun `recent fires keep only the last 24 hours`() {
        val now = 1_800_000_000_000L
        ResyncCheckpoint.markFired(context, now - 30 * 60 * 60_000L) // 30h 前，应被裁掉
        ResyncCheckpoint.markFired(context, now - 2 * 60 * 60_000L)
        ResyncCheckpoint.markFired(context, now - 60_000L)

        val fires = ResyncCheckpoint.recentFires(context, now)
        assertEquals(2, fires.size)
        assertTrue(fires.all { it > now - 24 * 60 * 60_000L })
    }

    @Test
    fun `self write and diagnostic are readable`() {
        ResyncCheckpoint.markSelfWrite(context, 1_000L)
        assertEquals(1_000L, ResyncCheckpoint.lastSelfWriteAt(context))

        ResyncCheckpoint.recordDiagnostic(context, 2_000L, ResyncCheckpoint.DiagnosticCode.LATER)
        val diagnostic = ResyncCheckpoint.lastDiagnostic(context)
        assertNotNull(diagnostic)
        assertEquals(2_000L, diagnostic!!.first)
        assertEquals(ResyncCheckpoint.DiagnosticCode.LATER, diagnostic.second)
        // 标签按当前语言翻译：不会原样输出原因码，且包含时间前缀
        val label = ResyncCheckpoint.lastDiagnosticLabel(context)
        assertTrue(label.isNotBlank())
        assertFalse(label.trim() == ResyncCheckpoint.DiagnosticCode.LATER)
    }

    @Test
    fun `guard service polls the flavor default`() {
        // 直装包默认开、Play 包默认关：断言跟随 BuildConfig，而不是写死 true
        assertEquals(
            com.cyclealarm.app.BuildConfig.GUARD_SERVICE_DEFAULT_ON,
            GuardServicePolicy.isEnabled(context)
        )
        GuardServicePolicy.setEnabled(context, false)
        assertFalse(GuardServicePolicy.isEnabled(context))
        GuardServicePolicy.setEnabled(context, true)
        assertTrue(GuardServicePolicy.isEnabled(context))
    }

    @Test
    fun `temporary mute stores and prunes days`() {
        TemporaryMuteStore.muteDay(context, 20_000L)
        TemporaryMuteStore.muteDay(context, 19_000L)
        assertTrue(TemporaryMuteStore.mutedDays(context).contains(20_000L))

        TemporaryMuteStore.prune(context, todayEpochDay = 19_500L)
        assertFalse(TemporaryMuteStore.mutedDays(context).contains(19_000L))
        assertTrue(TemporaryMuteStore.mutedDays(context).contains(20_000L))

        TemporaryMuteStore.clearMutedDay(context, 20_000L)
        assertNull(TemporaryMuteStore.mutedDays(context).firstOrNull())
    }
    @Test
    fun `clearAll removes skip and muted days`() {
        TemporaryMuteStore.skipNext(context, AlarmScheduler.KIND_CYCLE, 1, 1_000L)
        TemporaryMuteStore.muteDay(context, 20_000L)
        TemporaryMuteStore.clearAll(context)
        assertTrue(TemporaryMuteStore.skipsFor(context, AlarmScheduler.KIND_CYCLE, 1).isEmpty())
        assertTrue(TemporaryMuteStore.mutedDays(context).isEmpty())
    }
}
