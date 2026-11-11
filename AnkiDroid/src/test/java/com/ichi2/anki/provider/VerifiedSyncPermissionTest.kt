// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class VerifiedSyncPermissionTest : RobolectricTest() {
    @Test
    fun `both sync methods enforce database permission before inspecting requests`() {
        val provider = Robolectric.buildContentProvider(CardContentProvider::class.java).create().get()
        shadowOf(targetContext.applicationContext as Application)
            .denyPermissions("${BuildConfig.APPLICATION_ID}.permission.READ_WRITE_DATABASE")
        for (method in listOf("verified_sync_v1", "verified_sync_status_v1")) {
            assertFailsWith<SecurityException> { provider.call(method, "not-even-a-uuid", null) }
        }
    }
}
