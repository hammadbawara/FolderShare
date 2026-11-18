package com.hz_apps.foldershare.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hz_apps.foldershare.data.database.ClientPreferenceDao
import com.hz_apps.foldershare.data.database.ClientPreferenceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isLoaded: Boolean = false,
    val showOnboarding: Boolean = false,
    val isDownloadDialogOpen: Boolean = false
)

class OnboardingViewModel(
    private val clientPreferenceDao: ClientPreferenceDao
) : ViewModel() {

    private val _isDownloadDialogOpen = MutableStateFlow(false)

    val uiState: StateFlow<OnboardingUiState> = combine(
        clientPreferenceDao.getClientPreferenceFlow(),
        _isDownloadDialogOpen
    ) { pref, isDialogOpen ->
        val hasCompleted = pref?.hasCompletedOnboarding ?: false
        OnboardingUiState(
            isLoaded = true,
            showOnboarding = !hasCompleted,
            isDownloadDialogOpen = isDialogOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OnboardingUiState(isLoaded = false)
    )

    fun completeOnboarding() {
        viewModelScope.launch {
            val current = clientPreferenceDao.getClientPreference() ?: ClientPreferenceEntity()
            clientPreferenceDao.insertOrUpdateClientPreference(
                current.copy(hasCompletedOnboarding = true)
            )
        }
    }

    fun openDownloadDialog() {
        _isDownloadDialogOpen.value = true
    }

    fun closeDownloadDialog() {
        _isDownloadDialogOpen.value = false
    }
}
