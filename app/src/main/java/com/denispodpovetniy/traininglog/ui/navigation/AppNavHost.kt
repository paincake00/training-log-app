package com.denispodpovetniy.traininglog.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.ui.log.LogScreen
import com.denispodpovetniy.traininglog.ui.plan.PlanScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = AppTab.fromRoute(backStackEntry?.destination?.route) ?: AppTab.START

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(currentTab.titleRes)) })
        },
        bottomBar = {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppTab.START.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(AppTab.LOG.route) {
                LogScreen()
            }
            composable(AppTab.PLAN.route) {
                PlanScreen()
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
