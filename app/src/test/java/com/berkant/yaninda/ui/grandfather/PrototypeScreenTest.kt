package com.berkant.yaninda.ui.grandfather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrototypeScreenTest {
    @Test
    fun missingOrUnknownExtra_opensHome() {
        assertEquals(PrototypeScreen.HOME, PrototypeScreen.fromExtra(null))
        assertEquals(PrototypeScreen.HOME, PrototypeScreen.fromExtra("unknown"))
    }

    @Test
    fun knownExtra_opensRequestedPrototypeScreen() {
        assertEquals(PrototypeScreen.ALARM, PrototypeScreen.fromExtra("alarm"))
        assertEquals(PrototypeScreen.CONFIRMATION, PrototypeScreen.fromExtra("CONFIRMATION"))
    }

    @Test
    fun nullableParser_keepsPrototypeBehindAnExplicitKnownDebugExtra() {
        assertNull(PrototypeScreen.fromExtraOrNull(null))
        assertNull(PrototypeScreen.fromExtraOrNull("unknown"))
        assertEquals(PrototypeScreen.HOME, PrototypeScreen.fromExtraOrNull("home"))
    }
}
