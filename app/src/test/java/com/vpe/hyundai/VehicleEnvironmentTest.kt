package com.vpe.hyundai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleEnvironmentTest {
    private fun grant(permission: String) =
        VehiclePermissionCheck(permission, declared = true, definedBySystem = true, granted = true)

    private val allGranted = VehicleEnvironmentInspector.requiredPermissions.map(::grant)

    @Test fun regularAndroidPhoneCannotAccessVehicleService() {
        val env = VehicleEnvironment(automotive = false, permissions = allGranted)
        assertFalse(env.readyForVehicleRequest)
        assertEquals("ANDROID_PHONE", env.code)
        assertTrue(env.summary.contains("일반 Android"))
    }

    @Test fun automotiveWithoutGrantedPermissionsMustNotCallVehicleApi() {
        val env = VehicleEnvironment(
            automotive = true,
            permissions = listOf(
                VehiclePermissionCheck("pleos.car.permission.CAR_DRIVE", true, true, false),
                grant("pleos.car.permission.CAR_ENERGY"),
                grant("pleos.car.permission.CAR_INFO")
            )
        )
        assertFalse(env.readyForVehicleRequest)
        assertEquals("PERMISSION_UNAVAILABLE", env.code)
        assertEquals("CAR_DRIVE", env.missingPermissions.single().shortName)
    }

    @Test fun permissionReadyDoesNotClaimServiceConnection() {
        val env = VehicleEnvironment(automotive = true, permissions = allGranted)
        assertTrue(env.readyForVehicleRequest)
        assertEquals("ENVIRONMENT_READY", env.code)
        assertTrue(env.summary.contains("Vehicle Service 응답"))
    }
}
