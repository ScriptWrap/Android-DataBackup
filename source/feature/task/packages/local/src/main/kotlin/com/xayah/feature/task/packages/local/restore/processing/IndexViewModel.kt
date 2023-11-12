package com.xayah.feature.task.packages.local.restore.processing

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import com.xayah.core.common.viewmodel.BaseViewModel
import com.xayah.core.common.viewmodel.UiEffect
import com.xayah.core.common.viewmodel.UiIntent
import com.xayah.core.common.viewmodel.UiState
import com.xayah.core.data.repository.PackageRestoreOpRepository
import com.xayah.core.data.repository.PackageRestoreRepository
import com.xayah.core.data.repository.TaskRepository
import com.xayah.core.database.model.PackageRestoreEntire
import com.xayah.core.database.model.PackageRestoreOperation
import com.xayah.core.database.model.TaskEntity
import com.xayah.core.datastore.ConstantUtil
import com.xayah.core.model.EmojiString
import com.xayah.core.model.OpType
import com.xayah.core.model.ProcessingState
import com.xayah.core.model.TaskType
import com.xayah.core.service.packages.restore.local.RestoreService
import com.xayah.core.util.DateUtil
import com.xayah.feature.task.packages.local.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class IndexUiState(
    val timestampState: Long = DateUtil.getTimestamp(),
    val processingState: ProcessingState = ProcessingState.Idle,
) : UiState

sealed class IndexUiIntent : UiIntent {
    object Initialize : IndexUiIntent()
    object Process : IndexUiIntent()
}

sealed class IndexUiEffect : UiEffect {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val withDismissAction: Boolean = false,
        val duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
    ) : IndexUiEffect()

    object DismissSnackbar : IndexUiEffect()
}

@ExperimentalMaterial3Api
@HiltViewModel
class IndexViewModel @Inject constructor(
    packageRestoreRepository: PackageRestoreRepository,
    packageRestoreOpRepository: PackageRestoreOpRepository,
    private val taskRepository: TaskRepository,
    private val restoreService: RestoreService,
) : BaseViewModel<IndexUiState, IndexUiIntent, IndexUiEffect>(IndexUiState()) {
    override suspend fun onEvent(state: IndexUiState, intent: IndexUiIntent) {
        when (intent) {
            is IndexUiIntent.Initialize -> {
                if (taskState.value == null) {
                    taskRepository.upsertTask(
                        TaskEntity(
                            timestamp = state.timestampState,
                            opType = OpType.RESTORE,
                            taskType = TaskType.PACKAGE,
                            startTimestamp = 0,
                            endTimestamp = 0,
                            path = taskRepository.getString(R.string.internal_storage),
                            rawBytes = taskRepository.getRestoreRawBytes(),
                            availableBytes = taskRepository.getAvailableBytes(ConstantUtil.DefaultPathParent),
                            totalBytes = taskRepository.getTotalBytes(ConstantUtil.DefaultPathParent),
                        )
                    )
                }
            }

            is IndexUiIntent.Process -> {
                if (uiState.value.processingState == ProcessingState.Idle) {
                    emitStateSuspend(uiState.value.copy(processingState = ProcessingState.Processing))

                    launchOnIO {
                        taskRepository.updateStartTimestamp(timestamp = state.timestampState, startTimestamp = DateUtil.getTimestamp())

                        while (uiState.value.processingState == ProcessingState.Processing) {
                            delay(1000)
                            taskRepository.updateEndTimestamp(timestamp = state.timestampState, endTimestamp = DateUtil.getTimestamp())
                        }
                    }

                    restoreService.preprocessing()
                    restoreService.processing(timestamp = state.timestampState)
                    emitEffect(
                        IndexUiEffect.ShowSnackbar(
                            message = taskRepository.getString(R.string.wait_for_remaining_data_processing),
                            duration = SnackbarDuration.Indefinite
                        )
                    )
                    restoreService.destroyService()
                    emitStateSuspend(uiState.value.copy(processingState = ProcessingState.DONE))
                    emitEffect(IndexUiEffect.DismissSnackbar)
                    emitEffect(
                        IndexUiEffect.ShowSnackbar(
                            message = taskRepository.getString(R.string.restore_completed) + EmojiString.PARTY_POPPER.emoji
                        )
                    )
                }
            }
        }
    }

    val snackbarHostState: SnackbarHostState = SnackbarHostState()
    override suspend fun onEffect(effect: IndexUiEffect) {
        when (effect) {
            is IndexUiEffect.ShowSnackbar -> {
                snackbarHostState.showSnackbar(effect.message, effect.actionLabel, effect.withDismissAction, effect.duration)
            }

            is IndexUiEffect.DismissSnackbar -> {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }

    val taskState: StateFlow<TaskEntity?> = taskRepository.getTaskOrNull(uiState.value.timestampState).stateInScope(initialValue = null)
    val taskTimerState: StateFlow<String> = taskState.map { cur ->
        if (cur != null && cur.startTimestamp != 0L && cur.endTimestamp != 0L)
            taskRepository.getShortRelativeTimeSpanString(cur.startTimestamp, cur.endTimestamp)
        else
            taskRepository.getShortRelativeTimeSpanString(0, 0)
    }.stateInScope(initialValue = taskRepository.getShortRelativeTimeSpanString(0, 0))

    val packagesState: StateFlow<List<PackageRestoreEntire>> = packageRestoreRepository.selectedPackages.stateInScope(initialValue = listOf())
    val packagesApkOnlyState: StateFlow<List<PackageRestoreEntire>> = packageRestoreRepository.packagesApkOnly.stateInScope(initialValue = listOf())
    val packagesDataOnlyState: StateFlow<List<PackageRestoreEntire>> = packageRestoreRepository.packagesDataOnly.stateInScope(initialValue = listOf())
    val packagesBothState: StateFlow<List<PackageRestoreEntire>> = packageRestoreRepository.packagesBoth.stateInScope(initialValue = listOf())

    val operationsProcessingState: StateFlow<List<PackageRestoreOperation>> =
        packageRestoreOpRepository.getOperationsProcessing(uiState.value.timestampState).stateInScope(initialValue = listOf())
    val operationsFailedState: StateFlow<List<PackageRestoreOperation>> =
        packageRestoreOpRepository.getOperationsFailed(uiState.value.timestampState).stateInScope(initialValue = listOf())
    val operationsSucceedState: StateFlow<List<PackageRestoreOperation>> =
        packageRestoreOpRepository.getOperationsSucceed(uiState.value.timestampState).stateInScope(initialValue = listOf())
}