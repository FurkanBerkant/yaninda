package com.berkant.yaninda.family.private

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateDeviceProfileTest {
    @Test
    fun storedProfile_roundTripsByStableEnumName() {
        val profile = PrivateDeviceProfile.GRANDMOTHER

        assertEquals(
            profile,
            PrivateDeviceProfile.fromStoredValue(profile.name),
        )
    }

    @Test
    fun unknownStoredProfile_failsClosed() {
        assertNull(
            PrivateDeviceProfile.fromStoredValue("UNKNOWN_PROFILE")
        )
    }
}
