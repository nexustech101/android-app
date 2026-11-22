package com.sitesnap.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** One address the user can pick: "1420 Oak St" over "Springfield, IL 62704". */
data class AddressSuggestion(val primary: String, val secondary: String, val full: String)

/**
 * Street-address suggestions and "use my location", built on the phone's own Geocoder (Google Play
 * services on most phones), so there's no API key or billing. Needs a network connection.
 */
class AddressLookup(private val context: Context) {
    private val geocoder = Geocoder(context, Locale.getDefault())
    private val locations = context.getSystemService(LocationManager::class.java)

    val available: Boolean = Geocoder.isPresent()

    fun hasLocationPermission(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Addresses matching what the user has typed so far, nearest area first when we know where the phone is. */
    suspend fun search(query: String, max: Int = 5): List<AddressSuggestion> {
        if (!available) return emptyList()
        val near = lastKnownLocation()
        val nearby = near?.let {
            // About 100 km around the phone, so "12 Main St" finds the Main St in this town first.
            byName(query, max, doubleArrayOf(it.latitude - 1.0, it.longitude - 1.0, it.latitude + 1.0, it.longitude + 1.0))
        }.orEmpty()
        val results = nearby.ifEmpty { byName(query, max, null) }
        return results.mapNotNull { it.toSuggestion() }.distinctBy { it.full }
    }

    /** Street address at the phone's current position, or null if location is off or unavailable. */
    suspend fun addressHere(): AddressSuggestion? {
        val location = currentLocation() ?: return null
        return byLocation(location)?.toSuggestion()
    }

    private suspend fun byName(query: String, max: Int, box: DoubleArray?): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                        override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(emptyList()) }
                    }
                    if (box != null) geocoder.getFromLocationName(query, max, box[0], box[1], box[2], box[3], listener)
                    else geocoder.getFromLocationName(query, max, listener)
                }
            }.orEmpty()
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    if (box != null) geocoder.getFromLocationName(query, max, box[0], box[1], box[2], box[3])
                    else geocoder.getFromLocationName(query, max)
                }.getOrNull().orEmpty()
            }
        }

    private suspend fun byLocation(location: Location): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses.firstOrNull()) }
                        override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return locations.getProviders(true)
            .mapNotNull { runCatching { locations.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return lastKnownLocation()
        val enabled = locations.getProviders(true)
        val provider = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { it in enabled }
            ?: return lastKnownLocation()
        val fresh = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                locations.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { if (cont.isActive) cont.resume(it) }
            }
        }
        return fresh ?: lastKnownLocation()
    }

    private fun Address.toSuggestion(): AddressSuggestion? {
        var line = getAddressLine(0)?.takeIf { it.isNotBlank() } ?: return null
        // Geocoders sometimes put a place name first ("Google Building 41, 1600 Amphitheatre Pkwy, …").
        // Start at the street so job addresses and photo names read "1600 Amphitheatre Pkwy".
        // Match on the house number: the line may abbreviate the street ("Pkwy") where thoroughfare doesn't.
        val parts = line.split(", ")
        val number = subThoroughfare?.takeIf { it.isNotBlank() }
        val streetAt = if (number != null) parts.indexOfFirst { it.startsWith("$number ") } else -1
        if (streetAt > 0) line = parts.drop(streetAt).joinToString(", ")
        // Drop the country when it's the phone's own country: "…, IL 62704, USA" → "…, IL 62704".
        val full = if (countryCode != null && countryCode.equals(Locale.getDefault().country, ignoreCase = true) && countryName != null) {
            line.removeSuffix(", $countryName").removeSuffix(", USA")
        } else line
        return AddressSuggestion(
            primary = full.substringBefore(", "),
            secondary = full.substringAfter(", ", ""),
            full = full,
        )
    }
}
