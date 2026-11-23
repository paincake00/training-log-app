package com.denispodpovetniy.traininglog.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppTabTest {

    @Test
    fun `start tab is log`() {
        assertEquals(AppTab.LOG, AppTab.START)
    }

    @Test
    fun `tab routes are unique`() {
        val routes = AppTab.entries.map(AppTab::route)

        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `fromRoute resolves every tab`() {
        AppTab.entries.forEach { tab ->
            assertEquals(tab, AppTab.fromRoute(tab.route))
        }
    }

    @Test
    fun `fromRoute returns null for unknown route`() {
        assertNull(AppTab.fromRoute("unknown"))
        assertNull(AppTab.fromRoute(null))
    }
}
