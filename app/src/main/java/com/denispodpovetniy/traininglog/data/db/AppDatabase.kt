package com.denispodpovetniy.traininglog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.denispodpovetniy.traininglog.data.dao.ExerciseDao
import com.denispodpovetniy.traininglog.data.dao.PlanDao
import com.denispodpovetniy.traininglog.data.dao.WorkoutDao
import com.denispodpovetniy.traininglog.data.entity.ExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.PlanExerciseEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutEntity
import com.denispodpovetniy.traininglog.data.entity.WorkoutExerciseEntity

@Database(
    entities = [
        ExerciseEntity::class,
        PlanExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun planDao(): PlanDao

    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "training-log.db"
    }
}
