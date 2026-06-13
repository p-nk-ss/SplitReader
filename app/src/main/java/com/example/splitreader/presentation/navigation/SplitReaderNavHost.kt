package com.example.splitreader.presentation.navigation

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.R
import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.presentation.profile.AccountViewModel
import com.example.splitreader.presentation.profile.ProfileRoute
import com.example.splitreader.presentation.theme.MotionTokens
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.splitreader.presentation.almanac.AlmanacRoute
import com.example.splitreader.presentation.auth.AuthRoute
import com.example.splitreader.presentation.catalog.CatalogRoute
import com.example.splitreader.presentation.words.WordsRoute
import com.example.splitreader.presentation.home.HomeRoute
import com.example.splitreader.presentation.reader.ReaderRoute
import com.example.splitreader.presentation.settings.SettingsRoute

const val HOME_ROUTE     = "home"
const val READER_ROUTE   = "reader?path={path}"
const val CATALOG_ROUTE  = "catalog"
const val ALMANAC_ROUTE  = "almanac"
const val WORDS_ROUTE    = "words"
const val SETTINGS_ROUTE = "settings"
const val AUTH_ROUTE     = "auth"
const val PROFILE_ROUTE  = "profile"
private const val ARG_PATH = "path"

// Route slide settles on a gentle spring so transitions feel organic, not mechanical.
private val NavSlideSpring = spring(
    dampingRatio = 0.9f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

@Composable
fun SplitReaderNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val accountViewModel: AccountViewModel = hiltViewModel()
    val authState by accountViewModel.authState.collectAsStateWithLifecycle()
    val signedInUser = (authState as? AuthState.SignedIn)?.user
    val avatarLabel = signedInUser
        ?.let { (it.email ?: it.displayName)?.trim()?.firstOrNull()?.uppercaseChar()?.toString() }
        ?: "?"
    val avatarSubtitle = signedInUser
        ?.let { it.email?.substringBefore("@")?.take(10) ?: it.displayName?.take(10) }
        ?: stringResource(R.string.account_avatar_sign_in)

    AppShell(
        currentRoute = currentRoute,
        avatarLabel = avatarLabel,
        avatarSubtitle = avatarSubtitle,
        onNavigateToHome = { navController.navigate(HOME_ROUTE) { launchSingleTop = true } },
        onNavigateToCatalog = { navController.navigate(CATALOG_ROUTE) { launchSingleTop = true } },
        onNavigateToAlmanac = { navController.navigate(ALMANAC_ROUTE) { launchSingleTop = true } },
        onNavigateToWords = { navController.navigate(WORDS_ROUTE) { launchSingleTop = true } },
        onNavigateToSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
        onNavigateToAccount = {
            val target = if (signedInUser != null) PROFILE_ROUTE else AUTH_ROUTE
            navController.navigate(target) { launchSingleTop = true }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE,
            modifier = modifier,
            enterTransition = {
                fadeIn(tween(MotionTokens.Medium)) +
                    slideInHorizontally(animationSpec = NavSlideSpring) { it / 12 }
            },
            exitTransition = { fadeOut(tween(MotionTokens.Medium)) },
            popEnterTransition = {
                fadeIn(tween(MotionTokens.Medium)) +
                    slideInHorizontally(animationSpec = NavSlideSpring) { -it / 12 }
            },
            popExitTransition = { fadeOut(tween(MotionTokens.Medium)) },
        ) {
            composable(HOME_ROUTE) {
                HomeRoute(
                    onNavigateToReader = { filePath ->
                        navController.navigate("reader?path=${Uri.encode(filePath)}")
                    },
                )
            }
            composable(
                route = READER_ROUTE,
                arguments = listOf(navArgument(ARG_PATH) { type = NavType.StringType }),
            ) { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString(ARG_PATH) ?: return@composable
                ReaderRoute(
                    filePath = encoded,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(CATALOG_ROUTE) {
                CatalogRoute(
                    onNavigateToReader = { filePath ->
                        navController.navigate("reader?path=${Uri.encode(filePath)}")
                    },
                    onNavigateToAuth = {
                        navController.navigate(AUTH_ROUTE) { launchSingleTop = true }
                    },
                )
            }
            composable(ALMANAC_ROUTE) {
                AlmanacRoute()
            }
            composable(WORDS_ROUTE) {
                WordsRoute()
            }
            composable(SETTINGS_ROUTE) {
                SettingsRoute()
            }
            composable(AUTH_ROUTE) {
                AuthRoute(
                    onAuthComplete = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(PROFILE_ROUTE) {
                ProfileRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
