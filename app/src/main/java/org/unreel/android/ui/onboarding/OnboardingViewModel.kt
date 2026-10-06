package org.unreel.android.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.unreel.android.util.AccessibilityStatusHelper

sealed interface OnboardingUiState {
    data object PermissionRequired : OnboardingUiState
    data object PermissionGranted : OnboardingUiState
}

class OnboardingViewModel(
    private val checkPermissionEnabled: () -> Boolean
) : ViewModel() {

    constructor(context: Context) : this(
        checkPermissionEnabled = { AccessibilityStatusHelper.isServiceEnabled(context) }
    )

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.PermissionRequired)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionStatus()
    }

    fun refreshPermissionStatus() {
        if (checkPermissionEnabled()) {
            _uiState.value = OnboardingUiState.PermissionGranted
        } else {
            _uiState.value = OnboardingUiState.PermissionRequired
        }
    }
}
