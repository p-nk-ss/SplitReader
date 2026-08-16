package com.example.splitreader.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.splitreader.presentation.navigation.AUTH_ROUTE
import com.example.splitreader.presentation.navigation.CATALOG_ROUTE
import com.example.splitreader.presentation.navigation.HOME_ROUTE
import com.example.splitreader.presentation.navigation.PROFILE_ROUTE
import com.example.splitreader.presentation.navigation.navigateToTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The account screens (auth/profile) are modal overlays, not tab content. The tab pattern's
 * `popUpTo(saveState) + restoreState` must not embalm one into a tab's saved stack: doing so
 * resurrects it on the next visit to that tab (found on-device: Home → avatar → Catalog → Home
 * landed the user back on the sign-in screen with Cancel as the only way out).
 *
 * The graph here is a minimal stand-in (empty composables, real route constants) but the
 * navigation calls are the production ones: `navigateToTab` itself, and the avatar handler's
 * exact `navigate(target) { launchSingleTop = true }`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TabBackStackTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    private fun composeGraph() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = HOME_ROUTE) {
                composable(HOME_ROUTE) { Box {} }
                composable(CATALOG_ROUTE) { Box {} }
                composable(AUTH_ROUTE) { Box {} }
                composable(PROFILE_ROUTE) { Box {} }
            }
        }
        composeRule.waitForIdle()
    }

    private fun currentRoute() = navController.currentDestination?.route

    @Test
    fun `returning to a tab does not resurrect the auth screen`() {
        composeGraph()

        navController.navigate(AUTH_ROUTE) { launchSingleTop = true } // avatar tap, signed out
        composeRule.waitForIdle()
        navController.navigateToTab(CATALOG_ROUTE)
        composeRule.waitForIdle()
        navController.navigateToTab(HOME_ROUTE)
        composeRule.waitForIdle()

        assertEquals(
            "Coming back to the Home tab must land on Home, not on the sign-in screen " +
                "restored from the tab's saved stack.",
            HOME_ROUTE,
            currentRoute(),
        )
    }

    @Test
    fun `returning to a tab does not resurrect the profile screen`() {
        composeGraph()

        navController.navigate(PROFILE_ROUTE) { launchSingleTop = true } // avatar tap, signed in
        composeRule.waitForIdle()
        navController.navigateToTab(CATALOG_ROUTE)
        composeRule.waitForIdle()
        navController.navigateToTab(HOME_ROUTE)
        composeRule.waitForIdle()

        assertEquals(HOME_ROUTE, currentRoute())
    }
}
