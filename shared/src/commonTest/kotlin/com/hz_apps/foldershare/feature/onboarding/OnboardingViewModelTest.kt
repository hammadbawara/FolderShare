package com.hz_apps.foldershare.feature.onboarding

import com.hz_apps.foldershare.data.database.ClientPreferenceDao
import com.hz_apps.foldershare.data.database.ClientPreferenceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeClientPreferenceDao(
    initial: ClientPreferenceEntity? = null
) : ClientPreferenceDao {
    private val _flow = MutableStateFlow(initial)

    override fun getClientPreferenceFlow(): Flow<ClientPreferenceEntity?> = _flow.asStateFlow()

    override suspend fun getClientPreference(): ClientPreferenceEntity? = _flow.value

    override suspend fun insertOrUpdateClientPreference(preference: ClientPreferenceEntity) {
        _flow.value = preference
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun firstLaunch_whenPreferenceIsNull_showsOnboarding() = runTest(testDispatcher) {
        val dao = FakeClientPreferenceDao(null)
        val viewModel = OnboardingViewModel(dao)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertTrue(state.showOnboarding)
        assertFalse(state.isDownloadDialogOpen)
    }

    @Test
    fun completeOnboarding_persistsFlagAndHidesOnboarding() = runTest(testDispatcher) {
        val dao = FakeClientPreferenceDao(
            ClientPreferenceEntity(id = 1, isTransferShowMoreExpanded = true, hasCompletedOnboarding = false)
        )
        val viewModel = OnboardingViewModel(dao)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showOnboarding)

        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showOnboarding)
        val saved = dao.getClientPreference()
        assertTrue(saved?.hasCompletedOnboarding == true)
        assertTrue(saved.isTransferShowMoreExpanded, "Preserves existing client preferences")
    }

    @Test
    fun downloadDialog_opensAndCloses() = runTest(testDispatcher) {
        val dao = FakeClientPreferenceDao(
            ClientPreferenceEntity(hasCompletedOnboarding = true)
        )
        val viewModel = OnboardingViewModel(dao)
        backgroundScope.launch { viewModel.uiState.collect() }

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDownloadDialogOpen)

        viewModel.openDownloadDialog()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDownloadDialogOpen)

        viewModel.closeDownloadDialog()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDownloadDialogOpen)
    }
}
