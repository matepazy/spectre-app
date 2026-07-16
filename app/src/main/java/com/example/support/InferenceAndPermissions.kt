package com.matepazy.spectre.support

import android.content.Context
import android.content.pm.PackageManager
import com.matepazy.spectre.model.FingerprintSignal
import com.matepazy.spectre.model.SignalCategory
import java.security.MessageDigest

data class InferenceResult(
    val deviceSignature: String,       // SHA-256 Hash
    val trackingRiskScore: Int,        // 0 to 100 %
    val uniquenessIndex: Int,          // 0 to 100 %
    val activeVulnerabilities: Int,     // Count of high-threat signals exposed
    val fingerprintNarrative: String,  // Synthesized narrative
    val criticalExposures: List<FingerprintSignal> // High-threat exposed signals
)

object AppInferenceEngine {
    
    fun computeInference(signals: List<FingerprintSignal>): InferenceResult {
        // Concatenate non-empty raw values to generate a unique hash
        val concatenatedValues = signals
            .filter { it.rawValue != "Permission Blocked" && it.rawValue.isNotEmpty() }
            .joinToString(separator = "|") { "${it.id}:${it.getSignatureValue()}" }
            
        val deviceSignature = generateSha256(concatenatedValues)
        
        // Compute tracking risk score based on average threat scores of exposed signals
        val exposedSignals = signals.filter { it.rawValue != "Permission Blocked" && it.rawValue != "Empty" && !it.rawValue.contains("blocked", ignoreCase = true) }
        
        val totalThreat = exposedSignals.sumOf { it.threatScore }
        val maxPossibleThreat = signals.sumOf { it.threatScore }
        
        // Risk percentage: scale to 100%
        val trackingRiskScore = if (maxPossibleThreat > 0) {
            ((totalThreat.toFloat() / maxPossibleThreat.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        
        // Uniqueness index: higher if more advanced and permission signals are successfully read
        val passiveCount = exposedSignals.count { it.category == SignalCategory.PASSIVE }
        val permissionCount = exposedSignals.count { it.category == SignalCategory.NEEDS_PERMISSION }
        val advancedCount = exposedSignals.count { it.category == SignalCategory.ADVANCED }
        
        val totalSignalCount = signals.size
        val uniquenessIndex = if (totalSignalCount > 0) {
            val weight = (passiveCount * 1.0 + permissionCount * 2.5 + advancedCount * 2.0)
            val maxWeight = (signals.count { it.category == SignalCategory.PASSIVE } * 1.0 +
                             signals.count { it.category == SignalCategory.NEEDS_PERMISSION } * 2.5 +
                             signals.count { it.category == SignalCategory.ADVANCED } * 2.0)
            if (maxWeight > 0) {
                ((weight / maxWeight) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
        } else {
            0
        }
        
        // Critical exposures: exposed signals with threatScore >= 7
        val criticalExposures = exposedSignals.filter { it.threatScore >= 7 }
        val activeVulnerabilities = criticalExposures.size
        
        // Generate personalized narrative
        val narrative = FingerprintNarrative.synthesize(exposedSignals, trackingRiskScore)
        
        return InferenceResult(
            deviceSignature = deviceSignature,
            trackingRiskScore = trackingRiskScore,
            uniquenessIndex = uniquenessIndex,
            activeVulnerabilities = activeVulnerabilities,
            fingerprintNarrative = narrative,
            criticalExposures = criticalExposures
        )
    }
    
    private fun generateSha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }.take(24).uppercase() // Clean truncated 24-char signature for display
        } catch (e: Exception) {
            "3A9F8E12BDC46EF07A2B19D5" // fallback dummy hash
        }
    }
}

object FingerprintNarrative {
    
    fun synthesize(exposedSignals: List<FingerprintSignal>, riskScore: Int): String {
        val count = exposedSignals.size
        val sb = StringBuilder()
        
        sb.append("Your device exposes $count active system signals that are queryable by standard applications. ")
        
        val hasSocialMap = exposedSignals.any { it.id == "installed_apps_sidechannel" && !it.rawValue.contains("No common", ignoreCase = true) }
        val hasBluetoothDevices = exposedSignals.any { it.id == "bluetooth_bonded_devices" && it.rawValue != "Permission Blocked" }
        val hasContacts = exposedSignals.any { it.id == "contacts_exposure_threshold" && it.rawValue != "Permission Blocked" }
        
        sb.append("These metrics include passive side-channels like system uptime, display density settings, and build identifiers. ")
        
        if (hasContacts || hasBluetoothDevices || hasSocialMap) {
            sb.append("With current configurations, apps can also query peripheral metadata ")
            if (hasContacts) sb.append("and social exposure thresholds ")
            if (hasSocialMap) sb.append("along with package installation footprints ")
            sb.append("to link device activity across sessions.")
        } else {
            sb.append("Standard hardware identifiers are restricted, meaning apps rely on soft software-level signatures to distinguish your device.")
        }
        
        return sb.toString()
    }
}

object PermissionCenter {
    
    val permissionsToTrack = listOf(
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.GET_ACCOUNTS,
        android.Manifest.permission.BODY_SENSORS,
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.ACTIVITY_RECOGNITION,
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        "android.permission.QUERY_ALL_PACKAGES",
        android.Manifest.permission.ACCESS_NETWORK_STATE
    )
    
    fun getPermissionDisplayNames(permission: String): String {
        return when (permission) {
            android.Manifest.permission.READ_CONTACTS -> "Read Contacts List"
            android.Manifest.permission.READ_CALENDAR -> "Read System Calendars"
            android.Manifest.permission.CAMERA -> "Access Camera Hardware"
            android.Manifest.permission.BLUETOOTH_CONNECT -> "Query Paired Bluetooth Devices"
            android.Manifest.permission.ACCESS_COARSE_LOCATION -> "Access Coarse Location"
            android.Manifest.permission.ACCESS_FINE_LOCATION -> "Access Fine Location"
            android.Manifest.permission.RECORD_AUDIO -> "Access Microphone Sensor"
            android.Manifest.permission.READ_PHONE_STATE -> "Read Phone State & Identifiers"
            android.Manifest.permission.READ_CALL_LOG -> "Read Private Call Logs"
            android.Manifest.permission.READ_SMS -> "Read Private SMS Messages"
            android.Manifest.permission.GET_ACCOUNTS -> "Get Registered Accounts List"
            android.Manifest.permission.BODY_SENSORS -> "Access Body Sensors (Heart Rate, etc.)"
            android.Manifest.permission.POST_NOTIFICATIONS -> "Post System Notifications"
            android.Manifest.permission.ACTIVITY_RECOGNITION -> "Track Physical Motion/Steps"
            android.Manifest.permission.ACCESS_WIFI_STATE -> "Read WiFi Network SSID/Details"
            android.Manifest.permission.BLUETOOTH_SCAN -> "Scan for Nearby Beacons/BLE"
            android.Manifest.permission.READ_EXTERNAL_STORAGE -> "Access Media/Photo Libraries"
            "android.permission.QUERY_ALL_PACKAGES" -> "Query All Installed Packages"
            android.Manifest.permission.ACCESS_NETWORK_STATE -> "Read Network Connection State"
            else -> permission.substringAfterLast(".")
        }
    }
    
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        if (permission == "android.permission.POST_NOTIFICATIONS" && android.os.Build.VERSION.SDK_INT < 33) {
            return true
        }
        if ((permission == "android.permission.BLUETOOTH_CONNECT" || permission == "android.permission.BLUETOOTH_SCAN") && android.os.Build.VERSION.SDK_INT < 31) {
            return true
        }
        try {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            return false
        }
    }
    
    fun getSafePermissionsToRequest(permissionName: String): Array<String> {
        val sdk = android.os.Build.VERSION.SDK_INT
        return when (permissionName) {
            android.Manifest.permission.ACCESS_WIFI_STATE -> {
                arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN" -> {
                if (sdk >= 31) {
                    arrayOf(permissionName)
                } else {
                    arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
            "android.permission.POST_NOTIFICATIONS" -> {
                if (sdk >= 33) {
                    arrayOf(permissionName)
                } else {
                    emptyArray()
                }
            }
            android.Manifest.permission.READ_EXTERNAL_STORAGE -> {
                if (sdk >= 33) {
                    arrayOf(
                        "android.permission.READ_MEDIA_IMAGES",
                        "android.permission.READ_MEDIA_VIDEO"
                    )
                } else {
                    arrayOf(permissionName)
                }
            }
            "android.permission.QUERY_ALL_PACKAGES" -> {
                emptyArray()
            }
            "android.permission.ACCESS_NETWORK_STATE" -> {
                emptyArray()
            }
            else -> {
                arrayOf(permissionName)
            }
        }
    }
    
    fun getPermissionStatusMap(context: Context): Map<String, Boolean> {
        return permissionsToTrack.associateWith { isPermissionGranted(context, it) }
    }
}
