package com.denispodpovetniy.traininglog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.denispodpovetniy.traininglog.ui.navigation.AppNavHost
import com.denispodpovetniy.traininglog.ui.theme.TrainingLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TrainingLogTheme {
                AppNavHost()
            }
        }
    }
}
