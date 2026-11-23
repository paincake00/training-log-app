package com.denispodpovetniy.traininglog.ui.navigation

/** Маршруты верхнего уровня. */
object Routes {
    const val LOG = "log"
    const val PLAN = "plan"
}

/** Вкладки нижней навигации. */
enum class AppTab(val route: String) {
    LOG(Routes.LOG),
    PLAN(Routes.PLAN),
    ;

    companion object {
        val START: AppTab = LOG

        fun fromRoute(route: String?): AppTab? = entries.firstOrNull { it.route == route }
    }
}
