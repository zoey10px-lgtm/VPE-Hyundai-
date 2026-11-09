package com.vpe.hyundai

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import java.lang.reflect.Method

/** Read-only Pleos SDK adapter. No simulated readings are returned as real readings. */
class PleosVehicleClient(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private var initialized = false

    private fun ready() {
        if (!initialized) {
            vehicle.initialize()
            initialized = true
        }
    }

    /**
     * Discovers the SDK's installed getter signatures and invokes safe zero-argument
     * asynchronous getters. Callback data is exposed with the originating method name.
     * Units are not guessed: unrecognized data stays diagnostic-only.
     */
    fun readVehicleData(onReading: (String, VehicleReadings?) -> Unit) {
        try {
            ready()
            val sources = listOf(
                "Odometer" to vehicle.getOdometer(),
                "Fuel" to vehicle.getFuel(),
                "CarInfo" to vehicle.getCarInfo(),
                "EvBattery" to vehicle.getEvBattery()
            )
            var requested = 0
            sources.forEach { (domain, target) ->
                val methods = target.javaClass.methods.filter { method ->
                    method.name.startsWith("get") && method.name != "getClass" &&
                        method.parameterTypes.isNotEmpty() &&
                        method.parameterTypes.all { type ->
                            kotlin.jvm.functions.Function1::class.java.isAssignableFrom(type) ||
                                kotlin.jvm.functions.Function2::class.java.isAssignableFrom(type)
                        }
                }.distinctBy { it.name + it.parameterCount }
                onReading("$domain: 조회 가능 메서드 ${methods.joinToString { it.name }.ifBlank { "없음" }}", null)
                methods.forEach { method ->
                    requested++
                    try {
                        val args = method.parameterTypes.mapIndexed { index, type ->
                            val isFailure = index > 0 && index == method.parameterTypes.lastIndex
                            if (kotlin.jvm.functions.Function2::class.java.isAssignableFrom(type)) {
                                { a: Any?, b: Any? ->
                                    receive(domain, method, if (a is Number) a else b, isFailure, onReading)
                                    Unit
                                }
                            } else {
                                { value: Any? ->
                                    receive(domain, method, value, isFailure, onReading)
                                    Unit
                                }
                            }
                        }.toTypedArray()
                        method.invoke(target, *args)
                    } catch (e: Exception) {
                        onReading("$domain.${method.name}: ${e.cause?.message ?: e.message}", null)
                    }
                }
            }
            if (requested == 0) onReading("자동 호출 가능한 SDK getter가 없습니다. 영역·인자가 필요한 API는 별도 구현이 필요합니다.", null)
        } catch (e: Exception) {
            onReading("Pleos SDK 초기화/조회 실패: ${e.message ?: e.javaClass.simpleName}", null)
        }
    }

    private fun receive(
        domain: String, method: Method, value: Any?, failed: Boolean,
        onReading: (String, VehicleReadings?) -> Unit
    ) {
        val name = method.name.lowercase()
        val number = value as? Number
        val n = number?.toDouble()
        val reading = if (!failed && n != null && n.isFinite()) {
            when {
                domain == "Odometer" && ("km" in name || "kilometer" in name) &&
                    ("total" in name || "odometer" in name || "accumulated" in name) ->
                    VehicleReadings(odometerKm = n)
                domain == "Fuel" && ("percent" in name || "percentage" in name) ->
                    VehicleReadings(fuelPercent = n)
                else -> null
            }
        } else null
        onReading("$domain.${method.name}: ${value ?: "null"}" +
            if (reading != null) " (VPE 입력 반영 가능)" else " (단위 확인 필요/진단)", reading)
    }

    fun release() {
        if (initialized) {
            vehicle.release()
            initialized = false
        }
    }
}
