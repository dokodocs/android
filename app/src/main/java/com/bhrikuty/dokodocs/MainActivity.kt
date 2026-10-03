package com.bhrikuty.dokodocs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bhrikuty.dokodocs.theme.DokoDocsTheme
import com.bhrikuty.dokodocs.theme.PrimaryLight
import com.bhrikuty.dokodocs.ui.editor.DocumentDetailScreen
import com.bhrikuty.dokodocs.ui.folders.FoldersScreen
import com.bhrikuty.dokodocs.ui.home.HomeScreen
import com.bhrikuty.dokodocs.ui.navigation.Screen
import com.bhrikuty.dokodocs.ui.onboarding.OnboardingScreen
import com.bhrikuty.dokodocs.ui.onboarding.SplashScreen
import com.bhrikuty.dokodocs.ui.qr.QrScannerScreen
import com.bhrikuty.dokodocs.ui.scan.CameraScannerScreen
import com.bhrikuty.dokodocs.ui.scan.CropEditorScreen
import com.bhrikuty.dokodocs.ui.scan.ScanReviewScreen
import com.bhrikuty.dokodocs.ui.settings.SettingsScreen
import com.bhrikuty.dokodocs.ui.signature.SignatureDrawScreen
import com.bhrikuty.dokodocs.ui.signature.SignaturePlacementScreen
import com.bhrikuty.dokodocs.ui.signature.SignaturesScreen
import com.bhrikuty.dokodocs.ui.tools.ToolsScreen
import com.bhrikuty.dokodocs.viewmodel.MainViewModel
import com.bhrikuty.dokodocs.viewmodel.ScanViewModel
import kotlinx.coroutines.launch

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by mainViewModel.settings.collectAsState()
            val isDark = when (settings.theme.lowercase()) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            DokoDocsTheme(darkTheme = isDark) {
                AppRootNavigation(
                    mainViewModel = mainViewModel,
                    scanViewModel = scanViewModel
                )
            }
        }
    }
}

@Composable
fun AppRootNavigation(
    mainViewModel: MainViewModel,
    scanViewModel: ScanViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val coroutineScope = rememberCoroutineScope()

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Home.route, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(Screen.Folders.route, "Folders", Icons.Filled.Folder, Icons.Outlined.Folder),
        BottomNavItem(Screen.Tools.route, "Tools", Icons.Filled.Widgets, Icons.Outlined.Widgets),
        BottomNavItem(Screen.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Folders.route,
        Screen.Tools.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(68.dp)
                    ) {
                        bottomNavItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryLight,
                                    selectedTextColor = PrimaryLight,
                                    unselectedIconColor = MaterialTheme.colorScheme.outline,
                                    unselectedTextColor = MaterialTheme.colorScheme.outline,
                                    indicatorColor = PrimaryLight.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onFinished = {
                        val settings = mainViewModel.settings.value
                        val targetRoute = if (settings.onboardingComplete) Screen.Home.route else Screen.Onboarding.route
                        navController.navigate(targetRoute) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinished = {
                        coroutineScope.launch {
                            mainViewModel.settingsRepo.setOnboardingComplete()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = mainViewModel,
                    onNavigateToScan = {
                        scanViewModel.clearSession()
                        navController.navigate(Screen.CameraScan.route)
                    },
                    onNavigateToDocument = { docId ->
                        navController.navigate(Screen.DocumentDetail.createRoute(docId))
                    }
                )
            }

            composable(Screen.Folders.route) {
                FoldersScreen(
                    viewModel = mainViewModel,
                    onSelectFolder = { folderId ->
                        mainViewModel.setSelectedFolder(folderId)
                        navController.navigate(Screen.Home.route)
                    }
                )
            }

            composable(Screen.Tools.route) {
                ToolsScreen(
                    onNavigateToSignatures = {
                        navController.navigate(Screen.SignaturesList.route)
                    },
                    onNavigateToQrScanner = {
                        navController.navigate(Screen.QrScanner.route)
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = mainViewModel)
            }

            composable(Screen.CameraScan.route) {
                CameraScannerScreen(
                    viewModel = scanViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReview = { navController.navigate(Screen.ScanReview.route) },
                    onNavigateToCrop = { pageIndex ->
                        navController.navigate(Screen.CropEditor.createRoute(pageIndex))
                    }
                )
            }

            composable(
                route = Screen.CropEditor.route,
                arguments = listOf(navArgument("pageIndex") { type = NavType.IntType })
            ) { backStackEntry ->
                val pageIndex = backStackEntry.arguments?.getInt("pageIndex") ?: 0
                CropEditorScreen(
                    pageIndex = pageIndex,
                    viewModel = scanViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReview = { navController.navigate(Screen.ScanReview.route) }
                )
            }

            composable(Screen.ScanReview.route) {
                ScanReviewScreen(
                    viewModel = scanViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCrop = { pageIndex ->
                        navController.navigate(Screen.CropEditor.createRoute(pageIndex))
                    },
                    onNavigateToCamera = { navController.navigate(Screen.CameraScan.route) },
                    onDocumentSaved = { docId ->
                        navController.navigate(Screen.DocumentDetail.createRoute(docId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(
                route = Screen.DocumentDetail.route,
                arguments = listOf(navArgument("documentId") { type = NavType.LongType })
            ) { backStackEntry ->
                val docId = backStackEntry.arguments?.getLong("documentId") ?: 0L
                DocumentDetailScreen(
                    documentId = docId,
                    viewModel = mainViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSign = { pageIndex ->
                        navController.navigate(Screen.SignaturePlacement.createRoute(docId, pageIndex))
                    }
                )
            }

            composable(
                route = Screen.SignaturePlacement.route,
                arguments = listOf(
                    navArgument("documentId") { type = NavType.LongType },
                    navArgument("pageIndex") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { backStackEntry ->
                val docId = backStackEntry.arguments?.getLong("documentId") ?: 0L
                val pageIndex = backStackEntry.arguments?.getInt("pageIndex") ?: 0
                SignaturePlacementScreen(
                    documentId = docId,
                    pageIndex = pageIndex,
                    viewModel = mainViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onSignatureApplied = { navController.popBackStack() }
                )
            }

            composable(Screen.SignaturesList.route) {
                SignaturesScreen(
                    viewModel = mainViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDraw = { navController.navigate(Screen.SignatureDraw.route) }
                )
            }

            composable(Screen.SignatureDraw.route) {
                SignatureDrawScreen(
                    viewModel = mainViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.QrScanner.route) {
                QrScannerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
