package com.denispodpovetniy.traininglog.ui.workout

import com.denispodpovetniy.traininglog.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Каждое сообщение экрана тренировки должно иметь собственный русский текст. */
class WorkoutMessageTextTest {

    @Test
    fun everyMessageHasItsOwnString() {
        val resources = WorkoutMessage.entries.map { it.textRes }

        assertEquals(WorkoutMessage.entries.size, resources.distinct().size)
    }

    @Test
    fun messagesMapToExpectedStrings() {
        assertEquals(R.string.workout_message_duplicate, WorkoutMessage.DuplicateExercise.textRes)
        assertEquals(R.string.workout_message_not_found, WorkoutMessage.ExerciseNotFound.textRes)
        assertEquals(R.string.workout_message_save_failed, WorkoutMessage.SaveFailed.textRes)
        assertEquals(
            R.string.workout_message_synced_plan_and_preset,
            WorkoutMessage.SyncedPlanAndPreset.textRes,
        )
        assertEquals(
            R.string.workout_message_synced_preset_only,
            WorkoutMessage.SyncedPresetOnly.textRes,
        )
    }

    @Test
    fun syncedMessagesAreDifferent() {
        assertNotEquals(
            WorkoutMessage.SyncedPlanAndPreset.textRes,
            WorkoutMessage.SyncedPresetOnly.textRes,
        )
    }
}
