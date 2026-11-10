package com.salesdrive.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test fun `saved mode is restored`() {
        assertEquals(ThemeMode.DARK, ThemeMode.parse("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse("LIGHT"))
    }

    @Test fun `missing or unknown value falls back to light`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse(null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse("SEPIA"))
    }
}
