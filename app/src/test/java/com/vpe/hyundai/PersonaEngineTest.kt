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
    @Test fun energyDropChangesMoodBeforeLongTermIdentity() {
        val full = baseline.copy(fuelPercent = 100.0)
        val low = baseline.copy(fuelPercent = 15.0)
        val fullMood = PersonaEngine.calculate(full)
        val lowMood = PersonaEngine.calculate(low)
        assertTrue(lowMood.hunger > fullMood.hunger)
        assertTrue(lowMood.concern > fullMood.concern)
        assertTrue(fullMood.line != lowMood.line)
        assertTrue(PersonaEngine.moodLabel(fullMood) != PersonaEngine.moodLabel(lowMood))
        val identity = IdentityEngine.generate("same-vehicle", full)
        val after = IdentityEngine.evolve(identity, low)
        assertEquals(identity.memory, after.memory)
        assertEquals(identity.voiceBlueprint, after.voiceBlueprint)
    }

    @Test fun largeDistanceChangeAffectsExpressedTraitsWithoutReplacingBaseline() {
        val start = VehicleSnapshot(10.0, 0.0, 100.0, 50.0, 0.5, 0.5)
        val id = IdentityEngine.generate("same-vehicle", start)
        val driven = start.copy(odometerKm = 50000.0)
        val next = IdentityEngine.evolve(id, driven)
        val expressed = PersonaEngine.expressedTraits(next.memory, driven)
        assertTrue(expressed[2] > id.memory.traits[2])
        assertTrue(expressed[3] > id.memory.traits[3])
        assertEquals(id.voiceBlueprint, next.voiceBlueprint)
        assertEquals(1, next.learningEvents)
        assertEquals(id.initial, next.initial)
        assertEquals(expressed, PersonaEngine.expressedTraits(next.memory, driven))
    }

}
