package com.sitesnap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sitesnap.app.ui.screens.CameraScreen
import com.sitesnap.app.ui.screens.JobFolderScreen
import com.sitesnap.app.ui.screens.JobsScreen
import com.sitesnap.app.ui.screens.SettingsScreen
import com.sitesnap.app.ui.screens.WelcomeScreen
import com.sitesnap.app.ui.theme.SiteSnapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SiteSnapTheme { SiteSnapNavHost() }
        }
    }
}

@Composable
private fun SiteSnapNavHost(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val start = remember { if (vm.state.value.onboarded) "jobs" else "welcome" }

    NavHost(navController = nav, startDestination = start) {
        composable("welcome") {
            val toJobs = { nav.navigate("jobs") { popUpTo("welcome") { inclusive = true } } }
            WelcomeScreen(
                onConnected = { email -> vm.onDriveConnected(email); toJobs() },
                onSkip = { vm.skipOnboarding(); toJobs() },
            )
        }
        composable("jobs") {
            JobsScreen(
                vm = vm,
                onOpenJob = { nav.navigate("job/${it.id}") },
                onShoot = { nav.navigate("camera/${it.id}") },
                onJobCreated = { job ->
                    nav.navigate("job/${job.id}")
                    nav.navigate("camera/${job.id}")
                },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("job/{id}") { entry ->
            val job = state.jobs.find { it.id == entry.arguments?.getString("id") } ?: return@composable
            JobFolderScreen(
                vm = vm,
                job = job,
                onBack = { nav.popBackStack() },
                onTakePhotos = { nav.navigate("camera/${job.id}") },
            )
        }
        composable("camera/{id}") { entry ->
            val job = state.jobs.find { it.id == entry.arguments?.getString("id") } ?: return@composable
            CameraScreen(
                job = job,
                photoRepo = vm.photoRepo,
                onPhotoSaved = vm::onPhotoSaved,
                onClose = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
