package com.cyclealarm.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeResolverTest {

    @Test
    fun `forced modes override the system`() {
        assertFalse(ThemeManager.resolveDark(ThemeManager.MODE_LIGHT, systemDark = true))
        assertTrue(ThemeManager.resolveDark(ThemeManager.MODE_DARK, systemDark = false))
    }

    @Test
    fun `system mode follows the platform`() {
        assertTrue(ThemeManager.resolveDark(ThemeManager.MODE_SYSTEM, systemDark = true))
        assertFalse(ThemeManager.resolveDark(ThemeManager.MODE_SYSTEM, systemDark = false))
    }
}
