package com.salesdrive.vehicle

import com.salesdrive.vehicle.fake.DemoSwitchSource
import com.salesdrive.vehicle.fake.FakeVehicleSource
import com.salesdrive.vehicle.fake.Segment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoSwitchSourceTest {

    private val real = object : VehicleSignalSource {
        override val snapshots: Flow<VehicleSnapshot> =
            flowOf(VehicleSnapshot(0, true, 0f, Gear.P, false, 500.0, null))
    }

    @Test fun `demo continues from the last real odometer with scaled distance`() = runTest {
        val source = DemoSwitchSource(real) { startKm ->
            FakeVehicleSource(
                scenario = listOf(Segment(2, 36f, Gear.D, false)),
                from = GeoPoint(0.0, 0.0), to = GeoPoint(1.0, 1.0),
                startOdometerKm = startKm, speedUp = 1000.0, odometerScale = 100.0,
            )
        }
        val seen = mutableListOf<VehicleSnapshot>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { source.snapshots.take(3).toList(seen) }
        source.start()
        job.join()

        assertEquals(listOf(500.0, 501.0, 502.0), seen.map { it.odometerKm })
        assertEquals(listOf(Gear.P, Gear.D, Gear.D), seen.map { it.gear })
    }
}
