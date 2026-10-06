package org.unreel.android.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.unreel.android.ui.onboarding.OnboardingUiState
import org.unreel.android.ui.onboarding.OnboardingViewModel

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OnboardingViewModelTest {

    @Test
    fun testInitialStatePermissionRequiredWhenServiceDisabled() {
        val viewModel = OnboardingViewModel(checkPermissionEnabled = { false })
        assertEquals(OnboardingUiState.PermissionRequired, viewModel.uiState.value)
    }

    @Test
    fun testInitialStatePermissionGrantedWhenServiceAlreadyEnabled() {
        val viewModel = OnboardingViewModel(checkPermissionEnabled = { true })
        assertEquals(OnboardingUiState.PermissionGranted, viewModel.uiState.value)
    }

    @Test
    fun testStateTransitionsToGrantedOnRefresh() {
        var isEnabled = false
        val viewModel = OnboardingViewModel(checkPermissionEnabled = { isEnabled })
        assertEquals(OnboardingUiState.PermissionRequired, viewModel.uiState.value)

        // User enables permission in system settings and returns to app
        isEnabled = true
        viewModel.refreshPermissionStatus()
        assertEquals(OnboardingUiState.PermissionGranted, viewModel.uiState.value)
    }

    @Test
    fun testStateTransitionsBackToRequiredIfRevoked() {
        var isEnabled = true
        val viewModel = OnboardingViewModel(checkPermissionEnabled = { isEnabled })
        assertEquals(OnboardingUiState.PermissionGranted, viewModel.uiState.value)

        // Permission revoked in settings
        isEnabled = false
        viewModel.refreshPermissionStatus()
        assertEquals(OnboardingUiState.PermissionRequired, viewModel.uiState.value)
    }
}
