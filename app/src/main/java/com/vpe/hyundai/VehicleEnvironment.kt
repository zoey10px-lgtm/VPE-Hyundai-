package com.vpe.hyundai

import android.content.Context
import android.content.pm.PackageManager

/** Diagnostic of host OS and manifest permissions; never claims Vehicle Service is reachable. */
data class VehiclePermissionCheck(
    val permission: String,
    val declared: Boolean,
    val definedBySystem: Boolean,
    val granted: Boolean
) {
    val shortName: String get() = permission.substringAfterLast('.')
    val description: String get() = when {
        !declared -> "매니페스트 미선언"
        !definedBySystem -> "이 기기에 권한 정의 없음"
        !granted -> "권한 미부여"
        else -> "권한 부여"
    }
}

data class VehicleEnvironment(
    val automotive: Boolean,
    val permissions: List<VehiclePermissionCheck>
) {
    val missingPermissions: List<VehiclePermissionCheck>
        get() = permissions.filter { !it.granted }

    val readyForVehicleRequest: Boolean
        get() = automotive && missingPermissions.isEmpty()

    val summary: String
        get() = when {
            !automotive -> "일반 Android 기기입니다. Pleos 차량 데이터를 제공하는 Automotive 환경이 아닙니다."
            missingPermissions.isNotEmpty() ->
                "차량 데이터 접근 권한이 확인되지 않았습니다. Pleos 시스템 이미지와 앱 권한을 점검하세요."
            else -> "Automotive 환경과 차량 권한이 확인됐습니다. Vehicle Service 응답은 실제 조회로 검증합니다."
        }

    val code: String
        get() = when {
            !automotive -> "ANDROID_PHONE"
            missingPermissions.isNotEmpty() -> "PERMISSION_UNAVAILABLE"
            else -> "ENVIRONMENT_READY"
        }
}

object VehicleEnvironmentInspector {
    // Read-only Vehicle SDK domains used by VPE.
    val requiredPermissions = listOf(
        "pleos.car.permission.CAR_DRIVE",
        "pleos.car.permission.CAR_ENERGY",
        "pleos.car.permission.CAR_INFO"
    )

    fun inspect(context: Context): VehicleEnvironment {
        val manager = context.packageManager
        val declared = try {
            @Suppress("DEPRECATION")
            val info = manager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            info.requestedPermissions?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
        val checks = requiredPermissions.map { name ->
            val defined = try {
                @Suppress("DEPRECATION")
                manager.getPermissionInfo(name, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
            VehiclePermissionCheck(
                permission = name,
                declared = name in declared,
                definedBySystem = defined,
                granted = context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
            )
        }
        return VehicleEnvironment(
            automotive = manager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
            permissions = checks
        )
    }
}
