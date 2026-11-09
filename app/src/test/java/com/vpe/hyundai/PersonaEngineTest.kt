package com.vpe.hyundai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaEngineTest {
    private val baseline = VehicleSnapshot(
        9.0, 80000.0, 65.0, 70.0, 0.55, 0.40
    )

    @Test fun identicalVehicleInputsAreDeterministic() {
        assertEquals(PersonaEngine.calculate(baseline), PersonaEngine.calculate(baseline))
        assertEquals(IdentityEngine.generate("car-A", baseline),
            IdentityEngine.generate("car-A", baseline))
    }

    @Test fun voicePresentationIsNotFemaleOnly() {
        val active = VehicleSnapshot(0.0, 0.0, 65.0, 20.0, 1.0, 1.0)
        val experienced = VehicleSnapshot(20.0, 250000.0, 65.0, 100.0, 0.0, 0.0)
        assertEquals("여성형", IdentityEngine.generate("active", active).voiceBlueprint.presentation)
        assertEquals("남성형", IdentityEngine.generate("experienced", experienced).voiceBlueprint.presentation)
    }

    @Test fun fuelChangeOnlyAffectsShortTermMood() {
        val id = IdentityEngine.generate("car-A", baseline)
        val next = IdentityEngine.evolve(id, baseline.copy(fuelPercent = 7.0))
        assertEquals(id.learningEvents, next.learningEvents)
        assertEquals(id.voiceBlueprint, next.voiceBlueprint)
        assertEquals(id.memory, next.memory)
        assertTrue(PersonaEngine.calculate(baseline.copy(fuelPercent = 7.0)).hunger > 0.6)
    }

    @Test fun significantDrivingBuildsLongTermMemory() {
        val id = IdentityEngine.generate("car-A", baseline)
        val next = IdentityEngine.evolve(id, baseline.copy(odometerKm = 80120.0))
        assertEquals(1, next.learningEvents)
        assertEquals(80120.0, next.lastLearned.odometerKm, 0.001)
        assertEquals(id.voiceBlueprint, next.voiceBlueprint)
    }

    @Test fun repeatedIdenticalMeasurementDoesNotLearnAgain() {
        val id = IdentityEngine.generate("car-A", baseline)
        val after = IdentityEngine.evolve(id, baseline.copy(odometerKm = 80120.0))
        assertEquals(after, IdentityEngine.evolve(after, baseline.copy(odometerKm = 80120.0)))
    }

    @Test fun mapperDoesNotOverwriteUnknownValues() {
        val next = VehicleDataMapper.merge(baseline,
            VehicleReadings(odometerKm = 95400.0), 2026)
        assertEquals(95400.0, next.odometerKm, 0.001)
        assertEquals(baseline.fuelPercent, next.fuelPercent, 0.001)
        assertEquals(baseline.ageYears, next.ageYears, 0.001)
        assertFalse(VehicleReadings().hasMeasuredDrivingData())
        assertTrue(VehicleReadings(odometerKm = 0.0).hasMeasuredDrivingData())
    }
}
