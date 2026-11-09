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

    /**
     * Read-only SDK diagnostic for the VPE input domains.
     * Uses the SDK's actual public method signatures at runtime rather than assuming
     * a property name or converting an unknown unit to km/percent.
     */
    fun inspectPersonaSources(onResult: (String) -> Unit) {
        try {
            if (!initialized) {
                vehicle.initialize()
                initialized = true
            }
            val domains = listOf(
                "주행거리" to vehicle.getOdometer(),
                "연료" to vehicle.getFuel(),
                "차량정보" to vehicle.getCarInfo(),
                "전기차 배터리" to vehicle.getEvBattery()
            )
            for ((label, target) in domains) {
                val methods = target.javaClass.methods
                    .filter { it.name.startsWith("get") && it.name != "getClass" }
                    .filter { method -> method.parameterTypes.any {
                        kotlin.jvm.functions.Function1::class.java.isAssignableFrom(it) ||
                            kotlin.jvm.functions.Function2::class.java.isAssignableFrom(it)
                    } }
                    .sortedBy { it.name }
                onResult("$label: SDK 조회 API ${methods.joinToString { it.name }.ifBlank { "없음" }}")
                // Zero-argument async getters only; never call a setter or guess area parameters.
                for (method in methods.filter { m -> m.parameterTypes.all {
                    kotlin.jvm.functions.Function1::class.java.isAssignableFrom(it) ||
                        kotlin.jvm.functions.Function2::class.java.isAssignableFrom(it)
                } }) {
                    try {
                        val args = method.parameterTypes.map { type ->
                            if (kotlin.jvm.functions.Function2::class.java.isAssignableFrom(type)) {
                                { a: Any?, b: Any? ->
                                    onResult("$label.${method.name}: ${a ?: "null"}, ${b ?: "null"}")
                                    kotlin.Unit
                                }
                            } else {
                                { value: Any? ->
                                    onResult("$label.${method.name}: ${value ?: "null"}")
                                    kotlin.Unit
                                }
                            }
                        }.toTypedArray()
                        method.invoke(target, *args)
                    } catch (e: Exception) {
                        onResult("$label.${method.name} 호출 실패: ${e.cause?.message ?: e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            onResult("SDK 데이터 조회 초기화 실패: ${e.message}")
        }
    }

    fun release() {
        if (initialized) {
            vehicle.release()
            initialized = false
        }
    }
}
