package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.TrafficDatabase
import com.example.data.TrafficRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string and default shift settings from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Счётчик трафика и проходимости", appName)

        val db = TrafficDatabase.getInstance(context)
        val repo = TrafficRepository(db.trafficDao(), context)
        assertEquals(90, repo.getSilhouetteMemoryMinutes())
        assertEquals(12, repo.getParkingMemoryHours())
    }
}
