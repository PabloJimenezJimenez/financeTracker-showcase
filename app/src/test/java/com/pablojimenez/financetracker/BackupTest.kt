package com.pablojimenez.financetracker

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupTest {
    @Test
    fun `the installed app does not allow Android backup`() {
        val flags = ApplicationProvider.getApplicationContext<Context>().applicationInfo.flags

        assertEquals(0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
