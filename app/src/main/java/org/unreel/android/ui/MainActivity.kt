package org.unreel.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.unreel.android.data.FilterPreferencesRepository
import org.unreel.android.data.UnreelDatabase
import org.unreel.android.ui.dashboard.DashboardScreen
import org.unreel.android.ui.dashboard.DashboardViewModel
import org.unreel.android.ui.onboarding.OnboardingScreen
import org.unreel.android.ui.onboarding.OnboardingUiState
import org.unreel.android.ui.onboarding.OnboardingViewModel

class MainActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OnboardingViewModel(applicationContext) as T
            }
        }
    }

    private val dashboardViewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = UnreelDatabase.getInstance(applicationContext)
                val repo = FilterPreferencesRepository.getInstance(applicationContext)
                return DashboardViewModel(db.reelsInterceptDao(), repo) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val onboardingState by onboardingViewModel.uiState.collectAsState()

            if (onboardingState is OnboardingUiState.PermissionGranted) {
                DashboardScreen(viewModel = dashboardViewModel)
            } else {
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onPermissionGranted = {
                        onboardingViewModel.refreshPermissionStatus()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onboardingViewModel.refreshPermissionStatus()
    }
}
