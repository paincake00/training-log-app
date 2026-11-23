package com.denispodpovetniy.traininglog.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutUiStateTest {

    @Test
    fun `sync to plan is enabled only when progress has content`() {
        assertTrue(canSyncProgressToPlan("5/20-5/20"))
        assertTrue(canSyncProgressToPlan(" 42 "))
        assertFalse(canSyncProgressToPlan(""))
        assertFalse(canSyncProgressToPlan("   "))
        assertFalse(canSyncProgressToPlan("\n\t "))
    }
}
