package com.vpe.hyundai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanPersonalityEngineTest {
    private fun car(energy: Double = 100.0, km: Double = 2500.0) =
        HpeSignals("vehicle:verified-abc", odometerKm = km, energyPercent = energy)

    @Test fun deterministicSeedAndMbtiSameInputs() {
        val a = HumanPersonalityEngine.observe(car())
        val b = HumanPersonalityEngine.observe(car())
        assertEquals(a, b)
        assertEquals(4, a.core.mbti.length)
        assertEquals(7, a.core.traits.size)
        assertTrue(a.core.axes.all { it in 0.0..1.0 })
    }

    @Test fun vinDerivedAgeOnlyCountsWhenObserved() {
        val unknown = HumanPersonalityEngine.observe(car(km = 45000.0))
        val known = HumanPersonalityEngine.observe(car(km = 45000.0).copy(modelAgeYears = 15.0))
        assertTrue(unknown.sources.none { it.contains("차량 연식") })
        assertTrue(known.sources.any { it.contains("차량 연식") })
        assertTrue(known.core.confidence > unknown.core.confidence)
        assertNotEquals(unknown.core.traits[3], known.core.traits[3])
    }

    @Test fun pollingDoesNotAwardExperience() {
        val first = HumanPersonalityEngine.observe(car())
        val second = HumanPersonalityEngine.observe(car(), first)
        assertEquals(0, second.memory.meaningfulEvents)
        assertEquals(0.0, second.memory.sharedKm, 0.0001)
        assertEquals(first.core, second.core)
    }

    @Test fun distanceAccumulatesOverMultipleSmallUpdates() {
        var state = HumanPersonalityEngine.observe(car(km = 0.0))
        for (km in 1..101) {
            state = HumanPersonalityEngine.observe(car(km = km.toDouble()), state)
        }
        assertEquals(1, state.memory.meaningfulEvents)
        assertEquals(101.0, state.memory.sharedKm, 0.001)
    }

    @Test fun lowBatteryChangesNeedNotCharacter() {
        val full = HumanPersonalityEngine.observe(car())
        val low = HumanPersonalityEngine.observe(car(energy = 15.0), full)
        assertTrue(low.affect.hunger > full.affect.hunger)
        assertTrue(low.affect.tension > full.affect.tension)
        assertEquals(full.core, low.core)
        assertEquals(full.memory.meaningfulEvents, low.memory.meaningfulEvents)
        assertNotEquals(full.expression.utterance, low.expression.utterance)
    }

    @Test fun chargingIncreasesReliefWithoutFalselyRefillingBattery() {
        val before = HumanPersonalityEngine.observe(car(energy = 15.0))
        val charging = HumanPersonalityEngine.observe(
            car(energy = 15.0).copy(charging = true), before
        )
        assertEquals(before.affect.hunger, charging.affect.hunger, .0001)
        assertTrue(charging.affect.relief > before.affect.relief)
        assertTrue(charging.affect.tension < before.affect.tension)
    }

    @Test fun actualCabinAndTargetTemperatureMustBothBeKnown() {
        val unknown = HumanPersonalityEngine.observe(car().copy(targetCabinC = 22.0))
        assertEquals(0.0, unknown.affect.heat, .001)
        assertTrue(unknown.sources.none { it.contains("실내 현재온도: 수신값") })
        val known = HumanPersonalityEngine.observe(
            car().copy(actualCabinC = 29.0,targetCabinC = 22.0))
        assertTrue(known.affect.heat > .8)
        assertEquals("더움", known.expression.headline)
    }

    @Test fun safetySignalTakesPriorityAndDoesNotInitiateWhenMoving() {
        val state = HumanPersonalityEngine.observe(
            car(energy=12.0).copy(safetyWarning=true,parked=false))
        assertEquals("차량 상태 확인",state.expression.headline)
        assertFalse(state.expression.canInitiate)
    }

    @Test fun differentCarKeysProvideStableDifferentStartingProfiles() {
        val a = HumanPersonalityEngine.observe(car())
        val b = HumanPersonalityEngine.observe(car().copy(vehicleKey="vehicle:another-car"))
        assertNotEquals(a.core.axes, b.core.axes)
        assertEquals(a.core, HumanPersonalityEngine.observe(car()).core)
    }

    @Test fun firstObservedLongTermPatternInfluencesIdentityWithoutRegeneration() {
        val initial = HumanPersonalityEngine.observe(car(km=1000.0))
        val observed = HumanPersonalityEngine.observe(
            car(km=1000.0).copy(explorationRatio=1.0),initial)
        assertTrue(observed.core.axes[1] > initial.core.axes[1])
        assertTrue(observed.core.confidence > initial.core.confidence)
        assertEquals(1,observed.memory.meaningfulEvents)
        val again = HumanPersonalityEngine.observe(
            car(km=1000.0).copy(explorationRatio=1.0),observed)
        assertEquals(observed.core,again.core)
        assertEquals(observed.memory.meaningfulEvents,again.memory.meaningfulEvents)
    }

    @Test fun firstVinAgeEvidenceUpdatesCoverageWithoutFakeMileage() {
        val initial = HumanPersonalityEngine.observe(car())
        val observed = HumanPersonalityEngine.observe(car().copy(modelAgeYears=20.0),initial)
        assertTrue(observed.core.confidence > initial.core.confidence)
        assertNotEquals(initial.core.traits[3],observed.core.traits[3])
        assertEquals(0,observed.memory.meaningfulEvents)
    }

    @Test fun longTermEvidenceHasExpectedDirectionalEffect() {
        val input = HpeSignals("same-car",odometerKm=100.0)
        val low = HumanPersonalityEngine.makeCore(input.copy(
            interactionRatio=0.0,explorationRatio=0.0,regularityRatio=0.0))
        val high = HumanPersonalityEngine.makeCore(input.copy(
            interactionRatio=1.0,explorationRatio=1.0,regularityRatio=1.0))
        for (axis in listOf(0,1,3)) assertTrue(high.axes[axis] > low.axes[axis])
        assertEquals(low.axes[2],high.axes[2],0.0)
        assertTrue(high.traits[0] > low.traits[0])
        assertTrue(high.traits[1] > low.traits[1])
        assertTrue(high.traits[2] > low.traits[2])
        assertTrue(high.traits.all { it in 0.0..1.0 })
    }

    @Test fun memoryDoesNotConfuseOldOdometerWithJointExperience() {
        val state = HumanPersonalityEngine.observe(car(km=199177.0))
        assertEquals(0.0,state.memory.sharedKm,.0001)
        val newer = HumanPersonalityEngine.observe(car(km=199277.0),state)
        assertEquals(100.0,newer.memory.sharedKm,.0001)
    }
    @Test fun significantExperienceSlowlyAdaptsTraitsWithoutSensorPollLearning() {
        val start = HumanPersonalityEngine.observe(car(km=0.0))
        val driven = HumanPersonalityEngine.observe(car(km=50000.0),start)
        assertEquals(1,driven.memory.meaningfulEvents)
        assertTrue(driven.core.traits[3] > start.core.traits[3])
        assertEquals(start.core.mbti, driven.core.mbti)
        assertEquals(driven,HumanPersonalityEngine.observe(car(km=50000.0),driven))
    }

}
