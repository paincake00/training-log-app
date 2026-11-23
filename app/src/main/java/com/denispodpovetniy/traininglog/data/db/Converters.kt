package com.denispodpovetniy.traininglog.data.db

import androidx.room.TypeConverter
import com.denispodpovetniy.traininglog.data.model.WorkoutStatus

class Converters {

    @TypeConverter
    fun workoutStatusToString(status: WorkoutStatus): String = status.name

    @TypeConverter
    fun stringToWorkoutStatus(value: String): WorkoutStatus = WorkoutStatus.valueOf(value)
}
