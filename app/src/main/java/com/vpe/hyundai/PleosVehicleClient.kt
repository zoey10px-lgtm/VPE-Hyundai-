package com.vpe.hyundai

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue

/**
 * Pleos 2.3.2 typed SDK smoke test.
 * Reads a real SDK property; does not change vehicle state.
 * Other persona inputs remain simulated until their typed adapters are implemented.
 */
class PleosVehicleClient(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)
    private var initialized = false

    fun readDriverDoor(onResult: (String) -> Unit) {
        try {
            if (!initialized) {
                vehicle.initialize()
                initialized = true
            }
            val door = vehicle.getDoor()
            door.checkDoorPositionCapabilities(
                onComplete = { _, areaBounds ->
                    if (!areaBounds.containsKey(driverDoor)) {
                        onResult("운전석 도어: 지원 영역 없음")
                    } else {
                        door.getCurrentDoorPosition(
                            area = driverDoor,
                            onComplete = { position ->
                                onResult("운전석 도어 SDK 응답: ${position ?: "값 없음"}")
                            },
                            onFailed = { error -> onResult("도어 조회 실패: ${error.message}") }
                        )
                    }
                },
                onFailed = { error -> onResult("기능 확인 실패: ${error.message}") }
            )
        } catch (error: Exception) {
            onResult("Pleos 연결 실패: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun release() {
        if (initialized) {
            vehicle.release()
            initialized = false
        }
    }
}
