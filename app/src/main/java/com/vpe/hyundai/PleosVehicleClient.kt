package com.vpe.hyundai

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import android.util.Log
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import java.security.MessageDigest
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener

/** Typed, read-only Pleos Vehicle SDK 2.3.2 integration, confirmed from actual AAR. */
class PleosVehicleClient(context: Context) {
    private val appContext = context.applicationContext
    private val vehicle = Vehicle(appContext)
    private var initialized = false
    @Volatile private var distanceRegistered = false
    @Volatile private var fuelRegistered = false
    @Volatile private var evSocRegistered = false
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    @Volatile private var cabinListenerRegistered = false
    @Volatile private var targetListenerRegistered = false
    @Volatile private var hvacPowerListenerRegistered = false

    private val cabinListener = object : HvacTemperatureListener {
        override fun onFailed(error: Exception) {
            emitUpdate("CABIN_LISTENER_ERROR: " + safeMessage(error), null)
        }
        override fun onHvacTemperatureUpdated(value: Float?) {
            val c = value?.toDouble()
            if (c != null && c.isFinite() && c in -30.0..60.0)
                emitUpdate("CABIN_UPDATE: c=" + c, VehicleReadings(cabinTemperatureC = c))
        }
    }
    private val targetListener = object : HvacTemperatureListener {
        override fun onFailed(error: Exception) {
            emitUpdate("TARGET_LISTENER_ERROR: " + safeMessage(error), null)
        }
        override fun onHvacTemperatureUpdated(value: Float?) {
            val c = value?.toDouble()
            if (c != null && c.isFinite() && c in 14.0..35.0)
                emitUpdate("TARGET_UPDATE: c=" + c, VehicleReadings(targetTemperatureC = c))
        }
    }
    private val hvacListener = object : HvacPowerStateListener {
        override fun onFailed(error: Exception) {
            emitUpdate("HVAC_LISTENER_ERROR: " + safeMessage(error), null)
        }
        override fun onHvacPowerStateUpdated(value: Boolean?) {
            value?.let { emitUpdate("HVAC_UPDATE: on=" + it, VehicleReadings(hvacOn = it)) }
        }
    }
    @Volatile private var listenerReporter: ((String, VehicleReadings?) -> Unit)? = null

    private val distanceListener = object : DistanceDrivenListener {
        override fun onFailed(error: Exception) {
            emitUpdate("ODO_LISTENER_ERROR: " + safeMessage(error), null)
        }
        override fun onDistanceDrivenUpdated(value: Float?) {
            val km = value?.toDouble()
            if (km != null && km.isFinite() && km >= 0.0) {
                emitUpdate("ODO_UPDATE: km=" + km, VehicleReadings(odometerKm = km))
            }
        }
    }

    private val fuelListener = object : FuelPercentListener {
        override fun onFailed(error: Exception) {
            emitUpdate("FUEL_LISTENER_ERROR: " + safeMessage(error), null)
        }
        override fun onFuelPercentUpdated(value: Int?) {
            val percent = value?.toDouble()
            if (percent != null && percent in 0.0..100.0) {
                emitUpdate("FUEL_UPDATE: percent=" + percent, VehicleReadings(fuelPercent = percent))
            }
        }
    }

    private val evSocListener = object : EvBatteryStateOfChargeListener {
        override fun onFailed(error: Exception) {
            emitUpdate("EV_SOC_LISTENER_ERROR: " + safeMessage(error), null)
        }
        override fun onEvBatteryStateOfChargeUpdated(percent: Int?) {
            val value = percent?.toDouble()
            if (value != null && value in 0.0..100.0)
                emitUpdate("EV_SOC_UPDATE: percent=" + value, VehicleReadings(evBatteryLevel = value))
        }
    }

    private fun emitUpdate(message: String, reading: VehicleReadings?) {
        Log.i("VPE_PLEOS", message)
        listenerReporter?.invoke(message, reading)
    }


    @Synchronized private fun ready() {
        if (!initialized) {
            vehicle.initialize()
            initialized = true
        }
    }

    fun readVehicleData(onReading: (String, VehicleReadings?) -> Unit) {
        listenerReporter = onReading
        fun report(message: String, readings: VehicleReadings?) {
            Log.i("VPE_PLEOS", message)
            onReading(message, readings)
        }
        val environment = VehicleEnvironmentInspector.inspect(appContext)
        if (!environment.automotive) {
            report("ENVIRONMENT_ERROR: " + environment.code + ": " + environment.summary, null)
            return
        }
        try {
            ready()
            report("SDK initialize() 호출 완료 · Vehicle Service 통신 성공은 데이터 응답으로 확인", null)
        } catch (error: Exception) {
            report("SERVICE_ERROR: ${safeMessage(error)}", null)
            return
        }

        try {
            vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(
                onComplete = { capability ->
                    if (capability.accessType == AccessType.NONE) {
                        report("ODO_UNSUPPORTED: 주행거리 읽기 미지원", null)
                    } else {
                        if (!distanceRegistered) {
                            try {
                                vehicle.getOdometer().registerDistanceDriven(distanceListener)
                                distanceRegistered = true
                                report("ODO_LISTENER_READY: 주행거리 변경 구독 등록", null)
                            } catch (error: Exception) {
                                report("ODO_LISTENER_ERROR: " + safeMessage(error), null)
                            }
                        }
                        try {
                            vehicle.getOdometer().getCurrentDistanceDriven(
                                onComplete = { distance ->
                                    val km = distance?.toDouble()
                                    if (km != null && km.isFinite() && km >= 0.0) {
                                        report("ODO_OK: km=" + km, VehicleReadings(odometerKm = km))
                                    } else {
                                        report("ODO_EMPTY: 주행거리 값 없음", null)
                                    }
                                },
                                onFailed = { error -> report("ODO_ERROR: " + safeMessage(error), null) }
                            )
                        } catch (error: Exception) {
                            report("ODO_ERROR: " + safeMessage(error), null)
                        }
                    }
                },
                onFailed = { error -> report("ODO_CAP_ERROR: " + safeMessage(error), null) }
            )
        } catch (error: Exception) {
            report("ODO_CAP_ERROR: " + safeMessage(error), null)
        }

        try {
            vehicle.getFuel().checkFuelPercentCapabilities(
                onComplete = { capability ->
                    if (capability.accessType == AccessType.NONE) {
                        report("FUEL_UNSUPPORTED: 연료 비율 읽기 미지원", null)
                    } else {
                        if (!fuelRegistered) {
                            try {
                                vehicle.getFuel().registerFuelPercent(fuelListener)
                                fuelRegistered = true
                                report("FUEL_LISTENER_READY: 연료 변경 구독 등록", null)
                            } catch (error: Exception) {
                                report("FUEL_LISTENER_ERROR: " + safeMessage(error), null)
                            }
                        }
                        try {
                            vehicle.getFuel().getFuelPercent(
                                onComplete = { percent ->
                                    val value = percent?.toDouble()
                                    if (value != null && value in 0.0..100.0) {
                                        report("FUEL_OK: percent=" + value, VehicleReadings(fuelPercent = value))
                                    } else {
                                        report("FUEL_EMPTY: 연료 비율 확인 불가", null)
                                    }
                                },
                                onFailed = { error -> report("FUEL_ERROR: " + safeMessage(error), null) }
                            )
                        } catch (error: Exception) {
                            report("FUEL_ERROR: " + safeMessage(error), null)
                        }
                    }
                },
                onFailed = { error -> report("FUEL_CAP_ERROR: " + safeMessage(error), null) }
            )
        } catch (error: Exception) {
            report("FUEL_CAP_ERROR: " + safeMessage(error), null)
        }

        try {
            vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(
                onComplete = { capability ->
                    if (capability.accessType == AccessType.NONE) {
                        report("EV_UNSUPPORTED: 배터리 충전율 읽기 미지원", null)
                    } else {
                        if (!evSocRegistered) {
                            try {
                                vehicle.getEvBattery().registerEvBatteryStateOfCharge(evSocListener)
                                evSocRegistered = true
                                report("EV_LISTENER_READY: 실제 충전율 변경 구독 등록", null)
                            } catch (error: Exception) {
                                report("EV_LISTENER_ERROR: " + safeMessage(error), null)
                            }
                        }
                        try {
                            vehicle.getEvBattery().getEvBatteryStateOfCharge(
                                onComplete = { percent ->
                                    val value = percent?.toDouble()
                                    if (value != null && value in 0.0..100.0)
                                        report("EV_OK: percent=" + value, VehicleReadings(evBatteryLevel = value))
                                    else report("EV_EMPTY: 배터리 충전율 값 없음", null)
                                },
                                onFailed = { error -> report("EV_ERROR: " + safeMessage(error), null) }
                            )
                        } catch (error: Exception) {
                            report("EV_ERROR: " + safeMessage(error), null)
                        }
                    }
                },
                onFailed = { error -> report("EV_CAP_ERROR: " + safeMessage(error), null) }
            )
        } catch (error: Exception) {
            report("EV_CAP_ERROR: " + safeMessage(error), null)
        }

        // Additional public read-only Vehicle SDK sources. Never replace a failed
        // read with fabricated travel or warning values.
        try {
            vehicle.getOdometer().getTripCumulativeDrivingDistance(
                onComplete = { value ->
                    report("TRIP_DISTANCE: " + (value?.toString() ?: "EMPTY"),
                        value?.let { VehicleReadings(tripDistanceRaw = it.toString()) })
                },
                onFailed = { e -> report("TRIP_DISTANCE_ERROR: " + safeMessage(e), null) }
            )
        } catch (e: Exception) {
            report("TRIP_DISTANCE_ERROR: " + safeMessage(e), null)
        }
        try {
            vehicle.getOdometer().getTripCumulativeDrivingTime(
                onComplete = { value ->
                    report("TRIP_TIME: " + (value?.toString() ?: "EMPTY"),
                        value?.let { VehicleReadings(tripTimeRaw = it.toString()) })
                },
                onFailed = { e -> report("TRIP_TIME_ERROR: " + safeMessage(e), null) }
            )
        } catch (e: Exception) {
            report("TRIP_TIME_ERROR: " + safeMessage(e), null)
        }
        try {
            vehicle.getDrivingMode().getCurrentDrivingModeState(
                onComplete = { mode ->
                    report("DRIVE_MODE: " + (mode?.toString() ?: "EMPTY"),
                        mode?.let { VehicleReadings(drivingMode = it.toString()) })
                },
                onFailed = { e -> report("DRIVE_MODE_ERROR: " + safeMessage(e), null) }
            )
        } catch (e: Exception) {
            report("DRIVE_MODE_ERROR: " + safeMessage(e), null)
        }
        try {
            vehicle.getSafety().hasLowWasherFluidWarning(
                onComplete = { value ->
                    val observed = value?.toString()?.lowercase(java.util.Locale.ROOT)
                    val warning = when (observed) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                    report("WASHER_WARNING: " + (observed ?: "EMPTY"),
                        warning?.let { VehicleReadings(washerFluidWarning = it) })
                },
                onFailed = { e -> report("WASHER_WARNING_ERROR: " + safeMessage(e), null) }
            )
        } catch (e: Exception) {
            report("WASHER_WARNING_ERROR: " + safeMessage(e), null)
        }
        try {
            vehicle.getSafety().getEngineOilLevelState(
                onComplete = { state ->
                    report("ENGINE_OIL: " + (state?.toString() ?: "EMPTY"),
                        state?.let { VehicleReadings(engineOilState = it.toString()) })
                },
                onFailed = { e -> report("ENGINE_OIL_ERROR: " + safeMessage(e), null) }
            )
        } catch (e: Exception) {
            report("ENGINE_OIL_ERROR: " + safeMessage(e), null)
        }
        try {
            vehicle.getSafety().getBrakeFluidLevelState(
                onComplete = { state ->
                    report("BRAKE_FLUID: " + (state?.toString() ?: "EMPTY"),
                        state?.let { VehicleReadings(brakeFluidState = it.toString()) })
                },
                onFailed = { e -> report("BRAKE_FLUID_ERROR: " + safeMessage(e), null) }
            )
        } catch (e: Exception) {
            report("BRAKE_FLUID_ERROR: " + safeMessage(e), null)
        }

        // Read-only climate context; each getter is independent. The SDK callback
        // is the only source of truth. A missing/unsupported reading remains null.
        try {
            val climate = vehicle.getHVAC()
            try {
                if (!cabinListenerRegistered) {
                    climate.registerHvacTemperatureCurrent(driverSeat, cabinListener)
                    cabinListenerRegistered = true
                }
            } catch (e: Exception) {
                report("CABIN_LISTENER_ERROR: " + safeMessage(e), null)
            }
            try {
                if (!targetListenerRegistered) {
                    climate.registerHvacTemperature(driverSeat, targetListener)
                    targetListenerRegistered = true
                }
            } catch (e: Exception) {
                report("TARGET_LISTENER_ERROR: " + safeMessage(e), null)
            }
            try {
                if (!hvacPowerListenerRegistered) {
                    climate.registerHvacPowerState(driverSeat, hvacListener)
                    hvacPowerListenerRegistered = true
                }
            } catch (e: Exception) {
                report("HVAC_LISTENER_ERROR: " + safeMessage(e), null)
            }
            try {
                climate.getHvacTemperatureCurrent(driverSeat, TemperatureUnit.CELSIUS,
                    onComplete = { value ->
                        val c = value?.toDouble()
                        if (c != null && c.isFinite() && c in -30.0..60.0)
                            report("CABIN_OK: c=" + c, VehicleReadings(cabinTemperatureC = c))
                        else report("CABIN_EMPTY: 실제 실내온도 미제공", null)
                    },
                    onFailed = { e -> report("CABIN_ERROR: " + safeMessage(e), null) }
                )
            } catch (e: Exception) {
                report("CABIN_ERROR: " + safeMessage(e), null)
            }
            try {
                climate.getHvacTemperature(driverSeat, TemperatureUnit.CELSIUS,
                    onComplete = { value ->
                        val c = value?.toDouble()
                        if (c != null && c.isFinite() && c in 14.0..35.0)
                            report("TARGET_OK: c=" + c, VehicleReadings(targetTemperatureC = c))
                        else report("TARGET_EMPTY: 목표 온도 미제공", null)
                    },
                    onFailed = { e -> report("TARGET_ERROR: " + safeMessage(e), null) }
                )
            } catch (e: Exception) {
                report("TARGET_ERROR: " + safeMessage(e), null)
            }
            try {
                climate.getHvacPowerState(driverSeat,
                    onComplete = { value ->
                        if (value == null) report("HVAC_EMPTY: 공조 전원 미제공",null)
                        else report("HVAC_OK: on=" + value, VehicleReadings(hvacOn = value))
                    },
                    onFailed = { e -> report("HVAC_ERROR: " + safeMessage(e), null) }
                )
            } catch (e: Exception) {
                report("HVAC_ERROR: " + safeMessage(e), null)
            }
        } catch (e: Exception) {
            report("HVAC_DOMAIN_ERROR: " + safeMessage(e), null)
        }
        try {
            vehicle.getEvBattery().getEvChargeSwitchState(
                onComplete = { value ->
                    if (value == null) report("CHARGE_EMPTY: 충전 스위치 미제공", null)
                    else report("CHARGE_SWITCH_OK: on=" + value, VehicleReadings(charging = value))
                },
                onFailed = { e -> report("CHARGE_SWITCH_ERROR: " + safeMessage(e), null) }
            )
        } catch (e: Exception) {
            report("CHARGE_SWITCH_ERROR: " + safeMessage(e), null)
        }

        // VIN is preferred. If unavailable, try the SDK's verified CRN getter.
        // Neither raw identifier is written to logs or storage.
        fun readCRNFallback() {
            try {
                vehicle.getCarInfo().getCRNInfo(
                    onComplete = { crn ->
                        if (crn.isNullOrBlank())
                            report("IDENTITY_UNAVAILABLE: VIN/CRN 모두 미제공", null)
                        else
                            report("CRN_OK: 대체 차량 ID 확인 (원문 비저장)",
                                VehicleReadings(vehicleKey = fingerprint("CRN:" + crn)))
                    },
                    onFailed = { error ->
                        report("CRN_ERROR: " + safeMessage(error), null)
                    }
                )
            } catch (error: Exception) {
                report("CRN_ERROR: " + safeMessage(error), null)
            }
        }
        try {
            vehicle.getCarInfo().getVINInfo(
                onComplete = { vin ->
                    if (vin.isNullOrBlank()) {
                        report("VIN_EMPTY: VIN 미제공 · CRN 조회 시도", null)
                        readCRNFallback()
                    } else {
                        report("VIN_OK: 차량 ID 확인 (원문 비저장)",
                            VehicleReadings(vehicleKey = fingerprint("VIN:" + vin),
                                modelYear = inferModelYearFromVin(vin)))
                    }
                },
                onFailed = { error ->
                    report("VIN_ERROR: " + safeMessage(error), null)
                    readCRNFallback()
                }
            )
        } catch (error: Exception) {
            report("VIN_ERROR: " + safeMessage(error), null)
            readCRNFallback()
        }
    }

    /**
     * Decode a model-year candidate from an actual SDK VIN, not a generated fixture.
     * The 10th VIN character repeats every 30 years. This chooses the modern cycle;
     * it is a VIN-derived estimate and not a direct model-year property response.
     */
    private fun inferModelYearFromVin(rawVin: String): Int? {
        val vin = rawVin.trim().uppercase(java.util.Locale.ROOT)
        if (vin.length != 17 || vin.any { !(it in 'A'..'Z' || it in '0'..'9') || it in "IOQ" }) return null
        val yearCodes = "ABCDEFGHJKLMNPRSTVWXY123456789"
        val cycleIndex = yearCodes.indexOf(vin[9])
        if (cycleIndex < 0) return null
        val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return listOf(2010 + cycleIndex, 1980 + cycleIndex)
            .firstOrNull { it in 2010..thisYear }
    }

    private fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("vpe-local-v1:" + value.trim()).toByteArray(Charsets.UTF_8))
        return "vehicle:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun safeMessage(error: Exception): String {
        val message = error.message.orEmpty()
        return when {
            error is SecurityException || "permission" in message.lowercase() ->
                "PERMISSION_DENIED: " + message.take(120)
            "not supported" in message.lowercase() -> "UNSUPPORTED: 이 차량에서 지원되지 않는 데이터"
            else -> error.javaClass.simpleName + ": " + message.take(120)
        }
    }

    @Synchronized fun release() {
        listenerReporter = null
        if (initialized) {
            if (distanceRegistered) {
                runCatching { vehicle.getOdometer().unregisterDistanceDriven(distanceListener) }
                distanceRegistered = false
            }
            if (fuelRegistered) {
                runCatching { vehicle.getFuel().unregisterFuelPercent(fuelListener) }
                fuelRegistered = false
            }
            if (evSocRegistered) {
                runCatching { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(evSocListener) }
                evSocRegistered = false
            }
            if (cabinListenerRegistered) {
                runCatching { vehicle.getHVAC().unregisterHvacTemperatureCurrent(driverSeat,cabinListener) }
                cabinListenerRegistered = false
            }
            if (targetListenerRegistered) {
                runCatching { vehicle.getHVAC().unregisterHvacTemperature(driverSeat,targetListener) }
                targetListenerRegistered = false
            }
            if (hvacPowerListenerRegistered) {
                runCatching { vehicle.getHVAC().unregisterHvacPowerState(driverSeat,hvacListener) }
                hvacPowerListenerRegistered = false
            }
            vehicle.release()
            initialized = false
        }
    }
}
