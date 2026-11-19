package com.okaleb.phoneguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object Locator {
    @SuppressLint("MissingPermission")
    fun getLocation(ctx: Context, onResult: (Location?) -> Unit) {
        val ok = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!ok) { onResult(null); return }

        val client = LocationServices.getFusedLocationProviderClient(ctx)
        client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token
        ).addOnSuccessListener { loc ->
            if (loc != null) onResult(loc)
            else client.lastLocation
                .addOnSuccessListener { onResult(it) }
                .addOnFailureListener { onResult(null) }
        }.addOnFailureListener { onResult(null) }
    }
}
