package com.vpe.hyundai

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import java.security.MessageDigest

/** Typed, read-only Pleos Vehicle SDK 2.3.2 integration, confirmed from actual AAR. */
class PleosVehicleClient(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private var initialized = false

    @Synchronized private fun ready() {
        if (!initialized) {
            vehicle.initialize()
            initialized = true
        }
    }

    fun readVehicleData(onReading: (String, VehicleReadings?) -> Unit) {
        try {
            ready()
            onReading("Pleos Vehicle Service 초기화 요청 완료", null)
        } catch (error: Exception) {
            onReading("SERVICE_ERROR: ${safeMessage(error)}", null)
            return
        }

        try {
            vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(
                onComplete = { capability ->
                    if (capability.accessType == AccessType.NONE) {
                        onReading("ODO_UNSUPPORTED: 주행거리 읽기 미지원", null)
                    } else {
                        try {
                            vehicle.getOdometer().getCurrentDistanceDriven(
                                onComplete = { distance ->
                                    val km = distance?.toDouble()
                                    if (km != null && km.isFinite() && km >= 0.0) {
                                        onReading("ODO_OK: 누적 주행거리 수신", VehicleReadings(odometerKm = km))
                                    } else {
                                        onReading("ODO_EMPTY: 주행거리 값 없음", null)
                                    }
                                },
                                onFailed = { error -> onReading("ODO_ERROR: " + safeMessage(error), null) }
                            )
                        } catch (error: Exception) {
                            onReading("ODO_ERROR: " + safeMessage(error), null)
                        }
                    }
                },
                onFailed = { error -> onReading("ODO_CAP_ERROR: " + safeMessage(error), null) }
            )
        } catch (error: Exception) {
            onReading("ODO_CAP_ERROR: " + safeMessage(error), null)
        }

        try {
            vehicle.getFuel().checkFuelPercentCapabilities(
                onComplete = { capability ->
                    if (capability.accessType == AccessType.NONE) {
                        onReading("FUEL_UNSUPPORTED: 연료 비율 읽기 미지원", null)
                    } else {
                        try {
                            vehicle.getFuel().getFuelPercent(
                                onComplete = { percent ->
                                    val value = percent?.toDouble()
                                    if (value != null && value in 0.0..100.0) {
                                        onReading("FUEL_OK: 연료 비율 수신", VehicleReadings(fuelPercent = value))
                                    } else {
                                        onReading("FUEL_EMPTY: 연료 비율 확인 불가", null)
                                    }
                                },
                                onFailed = { error -> onReading("FUEL_ERROR: " + safeMessage(error), null) }
                            )
                        } catch (error: Exception) {
                            onReading("FUEL_ERROR: " + safeMessage(error), null)
                        }
                    }
                },
                onFailed = { error -> onReading("FUEL_CAP_ERROR: " + safeMessage(error), null) }
            )
        } catch (error: Exception) {
            onReading("FUEL_CAP_ERROR: " + safeMessage(error), null)
        }

        try {
            vehicle.getEvBattery().getEvBatteryLevel(
                onComplete = { level ->
                    val value = level?.toDouble()
                    if (value != null && value > 1.0 && value <= 100.0) {
                        onReading("EV_OK: 배터리 잔량 수신", VehicleReadings(evBatteryLevel = value))
                    } else {
                        onReading("EV_INFO: 배터리 원시값 ${value ?: "없음"} (단위 검증 필요)", null)
                    }
                },
                onFailed = { error -> onReading("EV_ERROR: ${safeMessage(error)}", null) }
            )
        } catch (error: Exception) {
            onReading("EV_ERROR: ${safeMessage(error)}", null)
        }

        try {
            vehicle.getCarInfo().getVINInfo(
                onComplete = { vin ->
                    if (vin.isNullOrBlank()) {
                        onReading("VIN_EMPTY: 차량 고유 식별자 미제공", null)
                    } else {
                        onReading("VIN_OK: 차량 ID 확인 (원문 비저장)", VehicleReadings(vehicleKey = fingerprint(vin)))
                    }
                },
                onFailed = { error -> onReading("VIN_ERROR: ${safeMessage(error)}", null) }
            )
        } catch (error: Exception) {
            onReading("VIN_ERROR: ${safeMessage(error)}", null)
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
                "PERMISSION_DENIED: Pleos 차량 데이터 권한을 확인하세요."
            "not supported" in message.lowercase() -> "UNSUPPORTED: 이 차량에서 지원되지 않는 데이터"
            else -> error.javaClass.simpleName + ": " + message.take(120)
        }
    }

    @Synchronized fun release() {
        if (initialized) {
            vehicle.release()
            initialized = false
        }
    }
}
