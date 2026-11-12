package com.vpe.hyundai

import org.junit.Assert.*
import org.junit.Test

class LiveProactiveEngineTest {
    private fun state(soc: Double? = null, cabin: Double? = null, target: Double? = null): HpeState =
        HumanPersonalityEngine.observe(HpeSignals("test-car", 100.0, soc, cabin, target))

    @Test fun onlyOneGreetingAndVehicleSwitchResetsIt() {
        val engine = LiveProactiveEngine()
        val a = state(80.0)
        val b = HumanPersonalityEngine.observe(HpeSignals("other-car",100.0,80.0))
        assertEquals("greeting",engine.greeting(a,1000L)?.kind)
        assertNull(engine.greeting(a,2000L))
        assertEquals("greeting",engine.greeting(b,3000L)?.kind)
    }

    @Test fun newVehicleReceivesItsOwnEnergyPrompt() {
        val engine = LiveProactiveEngine()
        val a = state(15.0)
        val b = HumanPersonalityEngine.observe(HpeSignals("other-car",100.0,15.0))
        val r = VehicleReadings(evBatteryLevel=15.0)
        assertEquals("low_energy",engine.next(r,a,10000L)?.kind)
        assertEquals("low_energy",engine.next(r,b,11000L)?.kind)
    }

    @Test fun urgentLowEnergyBypassesCooldown() {
        val engine = LiveProactiveEngine()
        assertEquals("low_energy",engine.next(VehicleReadings(evBatteryLevel=19.0),state(19.0),10000L)?.kind)
        assertEquals("critical_energy",engine.next(VehicleReadings(evBatteryLevel=9.0),state(9.0),11000L)?.kind)
    }

    @Test fun batteryWarnsOnceUntilRearmed() {
        val events = LiveProactiveEngine()
        val low = VehicleReadings(evBatteryLevel = 15.0)
        assertEquals("low_energy", events.next(low, state(15.0), 10_000L)?.kind)
        assertNull(events.next(low, state(15.0), 20_000L))
        assertNull(events.next(VehicleReadings(evBatteryLevel = 50.0), state(50.0), 30_000L))
        assertEquals("low_energy", events.next(low, state(15.0), 40_000L)?.kind)
    }

    @Test fun highCabinTemperatureTriggersWithoutUserMessage() {
        val events = LiveProactiveEngine()
        val hot = VehicleReadings(cabinTemperatureC = 32.0, targetTemperatureC = 21.0)
        assertEquals("hot", events.next(hot, state(cabin = 32.0, target = 21.0), 10_000L)?.kind)
        assertNull(events.next(hot, state(cabin = 32.0, target = 21.0), 20_000L))
    }

    @Test fun chargeSwitchEventNamesTheSwitchNotCharging() {
        val events = LiveProactiveEngine()
        val e = events.next(VehicleReadings(charging = true), state(80.0), 10_000L)
        assertEquals("charge_switch", e?.kind)
        assertTrue(e!!.text.contains("스위치"))
    }
}
