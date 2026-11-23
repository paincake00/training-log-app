package com.denispodpovetniy.traininglog

import android.app.Application

class TrainingLogApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
