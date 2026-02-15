package com.stashortrash.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.stashortrash.app.worker.DuplicateScanWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manager class that provides an abstraction over WorkManager for duplicate scanning.
 * Handles starting, canceling, and observing scan operations.
 */
class DuplicateScanManager(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Cancel any stuck or failed work from previous sessions.
     * Call this on app startup to clean up.
     */
    fun clearStuckWork() {
        workManager.pruneWork()
    }

    /**
     * Start a new duplicate scan.
     * If a scan is already running, this will replace it.
     */
    fun startScan() {
        val scanRequest = OneTimeWorkRequestBuilder<DuplicateScanWorker>()
            .addTag(DuplicateScanWorker.WORK_NAME)
            .build()

        workManager.enqueueUniqueWork(
            DuplicateScanWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            scanRequest
        )
    }

    /**
     * Cancel any running scan.
     */
    fun cancelScan() {
        workManager.cancelUniqueWork(DuplicateScanWorker.WORK_NAME)
    }

    /**
     * Get the current scan state as a Flow.
     */
    fun getScanState(): Flow<ScanState> {
        return workManager.getWorkInfosForUniqueWorkFlow(DuplicateScanWorker.WORK_NAME)
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                workInfoToScanState(workInfo)
            }
    }

    /**
     * Get the current scan state as LiveData.
     */
    fun getScanStateLiveData(): LiveData<List<WorkInfo>> {
        return workManager.getWorkInfosForUniqueWorkLiveData(DuplicateScanWorker.WORK_NAME)
    }

    /**
     * Convert WorkInfo to ScanState.
     */
    private fun workInfoToScanState(workInfo: WorkInfo?): ScanState {
        if (workInfo == null) {
            return ScanState.Idle
        }

        return when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> ScanState.Queued
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress
                val status = progress.getString(DuplicateScanWorker.KEY_STATUS) ?: ""
                val progressPercent = progress.getInt(DuplicateScanWorker.KEY_PROGRESS, 0)
                val current = progress.getInt(DuplicateScanWorker.KEY_CURRENT, 0)
                val total = progress.getInt(DuplicateScanWorker.KEY_TOTAL, 0)

                when (status) {
                    DuplicateScanWorker.STATUS_HASHING -> ScanState.Scanning(
                        phase = ScanPhase.HASHING,
                        progress = progressPercent,
                        current = current,
                        total = total
                    )
                    DuplicateScanWorker.STATUS_COMPARING -> ScanState.Scanning(
                        phase = ScanPhase.COMPARING,
                        progress = progressPercent,
                        current = 0,
                        total = 0
                    )
                    else -> ScanState.Scanning(
                        phase = ScanPhase.HASHING,
                        progress = progressPercent,
                        current = current,
                        total = total
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                val outputData = workInfo.outputData
                val groupsFound = outputData.getInt(DuplicateScanWorker.KEY_GROUPS_FOUND, 0)
                val duplicatesFound = outputData.getInt(DuplicateScanWorker.KEY_DUPLICATES_FOUND, 0)
                ScanState.Completed(
                    groupsFound = groupsFound,
                    duplicatesFound = duplicatesFound
                )
            }
            WorkInfo.State.FAILED -> {
                val errorMessage = workInfo.outputData.getString(DuplicateScanWorker.KEY_ERROR_MESSAGE)
                    ?: "Unknown error"
                ScanState.Error(errorMessage)
            }
            WorkInfo.State.CANCELLED -> ScanState.Cancelled
            WorkInfo.State.BLOCKED -> ScanState.Queued
        }
    }
}

/**
 * Represents the current state of a duplicate scan.
 */
sealed class ScanState {
    /** No scan has been started or scan was reset */
    object Idle : ScanState()

    /** Scan is queued and waiting to start */
    object Queued : ScanState()

    /** Scan is actively running */
    data class Scanning(
        val phase: ScanPhase,
        val progress: Int,
        val current: Int,
        val total: Int
    ) : ScanState()

    /** Scan completed successfully */
    data class Completed(
        val groupsFound: Int,
        val duplicatesFound: Int
    ) : ScanState()

    /** Scan failed with an error */
    data class Error(val message: String) : ScanState()

    /** Scan was cancelled by the user */
    object Cancelled : ScanState()
}

/**
 * Phases of the duplicate scan process.
 */
enum class ScanPhase {
    HASHING,
    COMPARING
}
