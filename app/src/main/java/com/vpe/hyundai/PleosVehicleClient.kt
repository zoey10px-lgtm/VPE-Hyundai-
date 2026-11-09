package com.vpe.hyundai

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import android.util.Log
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
    @Volatile private var listenerReporter: ((String, VehicleReadings?) -> Unit)? = null

    private val distanceListener = object : DistanceDrivenListener {
        override fun onFailed(error: Exception) {
            emitUpdate("ODO_LISTENER_ERROR: " + safeMessage(error), null)
        }
        override fun onDistanceDrivenUpdated(value: Float?) {
            val km = value?.toDouble()
            if (km != null && km.isFinite() && km >= 0.0) {
                emitUpdate("ODO_UPDATE: 누적 주행거리 변경", VehicleReadings(odometerKm = km))
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
                emitUpdate("FUEL_UPDATE: 연료 비율 변경", VehicleReadings(fuelPercent = percent))
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
                emitUpdate("EV_SOC_UPDATE: 실제 배터리 잔량 변경", VehicleReadings(evBatteryLevel = value))
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
        if (!environment.readyForVehicleRequest) {
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
                                        report("ODO_OK: 누적 주행거리 수신", VehicleReadings(odometerKm = km))
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
                                        report("FUEL_OK: 연료 비율 수신", VehicleReadings(fuelPercent = value))
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
                                        report("EV_OK: 배터리 충전율 수신", VehicleReadings(evBatteryLevel = value))
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

        try {
            vehicle.getCarInfo().getVINInfo(
                onComplete = { vin ->
                    if (vin.isNullOrBlank()) {
                        report("VIN_EMPTY: 차량 고유 식별자 미제공", null)
                    } else {
                        report("VIN_OK: 차량 ID 확인 (원문 비저장)", VehicleReadings(vehicleKey = fingerprint(vin)))
                    }
                },
                onFailed = { error -> report("VIN_ERROR: ${safeMessage(error)}", null) }
            )
        } catch (error: Exception) {
            report("VIN_ERROR: ${safeMessage(error)}", null)
        }
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
            vehicle.release()
            initialized = false
        }
    }
}
