// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.content.Context
import android.os.Bundle
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.IntentHandler.Companion.DoSync.Companion.INTENT_SYNC_MIN_INTERVAL
import com.ichi2.anki.millisecondsSinceLastSyncAttempt
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.syncAuth
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.anki.worker.toWorkData
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Permission enforcement belongs to CardContentProvider.call, before entering this API. */
object VerifiedSyncApi {
    fun profile(context: Context): String =
        (context.applicationContext as AnkiDroidApp)
            .profileManager!!
            .activeProfileContext.profileId.value

    @Synchronized
    fun call(
        context: Context,
        uid: Int,
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val requestId = UUID.fromString(requireNotNull(arg)).toString()
        // Scope client IDs to the caller. A caller cannot inspect another client's work.
        val workId = UUID.nameUUIDFromBytes("verified-sync:$uid:$requestId".toByteArray(Charsets.UTF_8))
        val manager = WorkManager.getInstance(context)
        val existing = manager.getWorkInfoById(workId).get()
        if (existing != null) return status(context, requestId, existing)
        if (method == "verified_sync_status_v1") return result(requestId, "unknown")
        val query = requireNotNull(extras?.getString("query"))
        require(query.isNotBlank() && query.length <= 2048) { "Invalid query length" }
        val ownerTag = "verified-sync-owner:$uid"
        if (manager.getWorkInfosByTag(ownerTag).get().any { !it.state.isFinished }) {
            return result(requestId, "busy")
        }
        if (millisecondsSinceLastSyncAttempt() < INTENT_SYNC_MIN_INTERVAL) return result(requestId, "busy")
        val auth = syncAuth()?.takeIf { it.toProto().hkey.isNotBlank() } ?: return result(requestId, "not_logged_in")
        val data =
            Data
                .Builder()
                .putAll(auth.toWorkData())
                .putBoolean("verified", true)
                .putString("profile", profile(context))
                .putString("query", query)
                .build()
        val work =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setId(workId)
                .setInputData(data)
                .addTag(ownerTag)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(MeteredSyncPolicy.getNetworkTypeRequiredForSync()).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .keepResultsForAtLeast(7, TimeUnit.DAYS)
                .build()
        // Share the normal sync chain; do not cancel an existing sync.
        manager.enqueueUniqueWork(UniqueWorkNames.SYNC, ExistingWorkPolicy.APPEND_OR_REPLACE, work).result.get()
        return result(requestId, "pending")
    }

    private fun status(
        context: Context,
        requestId: String,
        work: WorkInfo,
    ): Bundle {
        val outcome =
            when (work.state) {
                WorkInfo.State.SUCCEEDED -> if (work.outputData.getString("profile") == profile(context)) "success" else "profile_changed"
                WorkInfo.State.FAILED -> work.outputData.getString("outcome") ?: "sync_failed"
                WorkInfo.State.CANCELLED -> "cancelled"
                else -> "pending"
            }
        return result(requestId, outcome).apply {
            if (outcome == "success") {
                putString("profile", work.outputData.getString("profile"))
                putInt("count", work.outputData.getInt("count", -1))
                putLong("completed_at", work.outputData.getLong("completed_at", 0))
            }
        }
    }

    private fun result(
        requestId: String,
        outcome: String,
    ) = Bundle().apply {
        putInt("api_version", 1)
        putString("request_id", requestId)
        putString("outcome", outcome)
    }
}
