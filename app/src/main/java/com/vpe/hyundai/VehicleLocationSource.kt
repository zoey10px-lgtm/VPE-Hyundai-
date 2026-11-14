package com.vpe.hyundai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock

/** Foreground Android location fallback. No fabricated location if Pleos exposes no provider. */
class VehicleLocationSource(private val context: Context, private val emit: (VehicleReadings) -> Unit) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var started = false
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val ageMs=(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos)/1_000_000
            if (ageMs !in 0..30000 || !location.hasAccuracy() || location.accuracy>150) return
            emit(VehicleReadings(latitude=location.latitude,longitude=location.longitude,
                locationObservedAtMs=System.currentTimeMillis()-ageMs))
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    @Synchronized fun start() {
        if (started || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return
        for (provider in listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER)) {
            if (manager.allProviders.contains(provider)) runCatching {
                manager.requestLocationUpdates(provider,10000L,30f,listener,Looper.getMainLooper())
                started=true
            }
        }
    }
    @Synchronized fun stop() { runCatching { manager.removeUpdates(listener) }; started=false }
}
