package com.denispodpovetniy.traininglog.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Действия экрана плана, которые показывает меню в top app bar.
 *
 * Экран объявляет их при входе и очищает при выходе; `AppNavHost` рисует меню,
 * только когда действия заданы и открыта вкладка «План».
 */
class PlanActionsState {

    var onExport: (() -> Unit)? by mutableStateOf(null)

    var onImport: (() -> Unit)? by mutableStateOf(null)

    val isReady: Boolean
        get() = onExport != null && onImport != null

    fun clear() {
        onExport = null
        onImport = null
    }
}
