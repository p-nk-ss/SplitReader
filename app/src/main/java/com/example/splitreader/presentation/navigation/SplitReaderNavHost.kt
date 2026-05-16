package com.example.splitreader.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.splitreader.presentation.home.HomeRoute
import com.example.splitreader.presentation.reader.ReaderRoute

private const val HOME_ROUTE = "home"
private const val READER_ROUTE = "reader?path={path}"
private const val ARG_PATH = "path"

@Composable
fun SplitReaderNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
        modifier = modifier,
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
    }
}
