// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class VerifiedSyncApiTest : RobolectricTest() {
    @Test
    fun `request is idempotent private to caller and cancellation remains readable`() {
        Prefs.hkey = "test-key"
        Prefs.lastSyncTime = 0
        val id = UUID.randomUUID().toString()
        val extras = Bundle().apply { putString("query", "prop:due<=-1") }
        assertEquals("pending", VerifiedSyncApi.call(targetContext, 123, "verified_sync_v1", id, extras).getString("outcome"))
        assertEquals("pending", VerifiedSyncApi.call(targetContext, 123, "verified_sync_v1", id, extras).getString("outcome"))
        val manager = WorkManager.getInstance(targetContext)
        val work = manager.getWorkInfosByTag("verified-sync-owner:123").get().single()
        assertEquals("unknown", VerifiedSyncApi.call(targetContext, 456, "verified_sync_status_v1", id, null).getString("outcome"))
        manager.cancelWorkById(work.id).result.get()
        assertEquals("cancelled", VerifiedSyncApi.call(targetContext, 123, "verified_sync_v1", id, extras).getString("outcome"))
        assertEquals(1, manager.getWorkInfosByTag("verified-sync-owner:123").get().size)
    }

    @Test
    fun `caller cannot pile up pending syncs`() {
        Prefs.hkey = "test-key"
        Prefs.lastSyncTime = 0
        val extras = Bundle().apply { putString("query", "prop:due<=-1") }
        VerifiedSyncApi.call(targetContext, 123, "verified_sync_v1", UUID.randomUUID().toString(), extras)
        assertEquals(
            "busy",
            VerifiedSyncApi.call(targetContext, 123, "verified_sync_v1", UUID.randomUUID().toString(), extras).getString("outcome"),
        )
        assertEquals(
            1,
            WorkManager
                .getInstance(targetContext)
                .getWorkInfosByTag("verified-sync-owner:123")
                .get()
                .size,
        )
    }
}
