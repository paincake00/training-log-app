package com.denispodpovetniy.traininglog.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.ui.log.LogScreen
import com.denispodpovetniy.traininglog.ui.plan.PlanActionsMenu
import com.denispodpovetniy.traininglog.ui.plan.PlanActionsState
import com.denispodpovetniy.traininglog.ui.plan.PlanScreen
import com.denispodpovetniy.traininglog.ui.workout.WorkoutScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isWorkout = currentRoute == Routes.WORKOUT
    val currentTab = AppTab.fromRoute(currentRoute) ?: AppTab.START
    val planActions = remember { PlanActionsState() }

    Scaffold(
        topBar = {
            if (!isWorkout) {
                TopAppBar(
                    title = { Text(stringResource(currentTab.titleRes)) },
                    actions = {
                        if (currentTab == AppTab.PLAN && planActions.isReady) {
                            PlanActionsMenu(planActions)
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!isWorkout) {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(tab.iconRes),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = if (isWorkout) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppTab.START.route,
            modifier = if (isWorkout) {
                Modifier
            } else {
                Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            },
        ) {
            composable(
                route = AppTab.LOG.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                LogScreen(
                    onOpenWorkout = { workoutId ->
                        navController.navigate(Routes.workout(workoutId))
                    },
                )
            }
            composable(
                route = AppTab.PLAN.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                PlanScreen(
                    planActions = planActions,
                    onOpenWorkout = { workoutId ->
                        navController.navigate(Routes.workout(workoutId))
                    },
                )
            }
            composable(
                route = Routes.WORKOUT,
                arguments = listOf(
                    navArgument(Routes.WORKOUT_ID_ARG) { type = NavType.LongType },
                ),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                WorkoutScreen(
                    workoutId = entry.arguments?.getLong(Routes.WORKOUT_ID_ARG) ?: 0L,
                    onBack = { navController.popBackStack() },
                    onFinished = {
                        navController.navigate(Routes.LOG) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

@get:StringRes
private val AppTab.titleRes: Int
    get() = when (this) {
        AppTab.LOG -> R.string.log_title
        AppTab.PLAN -> R.string.plan_title
    }

@get:StringRes
private val AppTab.labelRes: Int
    get() = when (this) {
        AppTab.LOG -> R.string.tab_log
        AppTab.PLAN -> R.string.tab_plan
    }

private val AppTab.iconRes: Int
    get() = when (this) {
        AppTab.LOG -> R.drawable.ic_tab_log
        AppTab.PLAN -> R.drawable.ic_tab_plan
    }
