package com.comicify.core.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyNavigatorTest {

    @Test
    fun volumeDownMapsToNext() {
        assertEquals(PageTurnDirection.Next, volumeKeyPageTurnDirection(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun volumeUpMapsToPrevious() {
        assertEquals(PageTurnDirection.Previous, volumeKeyPageTurnDirection(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun unrelatedKeyMapsToNull() {
        assertNull(volumeKeyPageTurnDirection(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun dispatchFailsWithoutRegisteredListener() {
        assertFalse(VolumeKeyPageTurnDispatcher.dispatch(PageTurnDirection.Next))
    }

    @Test
    fun dispatchInvokesRegisteredListener() {
        var received: PageTurnDirection? = null
        val listener: (PageTurnDirection) -> Unit = { received = it }
        VolumeKeyPageTurnDispatcher.register(listener)
        try {
            assertTrue(VolumeKeyPageTurnDispatcher.dispatch(PageTurnDirection.Previous))
            assertEquals(PageTurnDirection.Previous, received)
            assertTrue(VolumeKeyPageTurnDispatcher.isRegistered)
        } finally {
            VolumeKeyPageTurnDispatcher.unregister(listener)
        }
    }

    @Test
    fun unregisterStopsDispatch() {
        val listener: (PageTurnDirection) -> Unit = {}
        VolumeKeyPageTurnDispatcher.register(listener)
        VolumeKeyPageTurnDispatcher.unregister(listener)
        assertFalse(VolumeKeyPageTurnDispatcher.isRegistered)
        assertFalse(VolumeKeyPageTurnDispatcher.dispatch(PageTurnDirection.Next))
    }
}
