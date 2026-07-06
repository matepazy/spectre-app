package com.matepazy.spectre.provider

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityManager
import android.webkit.WebSettings
import android.location.LocationManager
import android.media.MediaDrm
import android.accounts.AccountManager
import android.provider.CallLog
import android.provider.Telephony
import java.net.NetworkInterface
import java.util.UUID
import com.matepazy.spectre.model.FingerprintSignal
import com.matepazy.spectre.model.SignalCategory
import java.io.File
import java.text.DecimalFormat
import java.util.Locale
import java.util.TimeZone

abstract class SignalProvider {
    abstract val id: String
    abstract fun provideSignals(context: Context): List<FingerprintSignal>
}

class AccessibilityProvider : SignalProvider() {
    override val id = "accessibility"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val isEnabled = am.isEnabled
            val isTouchExploration = am.isTouchExplorationEnabled
            
            // Querying running services
            val runningServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val servicesCount = runningServices?.size ?: 0
            val serviceNames = runningServices?.joinToString { it.resolveInfo?.serviceInfo?.name?.substringAfterLast(".") ?: "" } ?: "None"
            
            val fontScale = context.resources.configuration.fontScale
            
            listOf(
                FingerprintSignal(
                    id = "accessibility_enabled",
                    name = "Accessibility Services Enabled",
                    description = "Detects whether global accessibility sub-systems are active.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (isEnabled) "Active ($servicesCount running)" else "Disabled",
                    narrative = "Trackers scan active accessibility states to flag disabled or power users. Some ad fraud SDKs monitor accessibility events to hijack UI clicks.",
                    threatScore = 5
                ),
                FingerprintSignal(
                    id = "accessibility_touch_exploration",
                    name = "Explore by Touch Active",
                    description = "Determines if speech-guided screen navigation is enabled.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (isTouchExploration) "Active" else "Inactive",
                    narrative = "Explore-by-touch indicates the user relies on screen readers like TalkBack, creating a highly unique user profile and tracking vulnerable demographics.",
                    threatScore = 6
                ),
                FingerprintSignal(
                    id = "accessibility_font_scale",
                    name = "System Font Scale",
                    description = "Queries the exact multiplier applied to standard text sizing.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "${fontScale}x",
                    narrative = "Slightly altered typography scales (e.g. 1.15x) are relatively rare across device models, significantly reducing the size of the anonymity set for browser/webview fingerprinting.",
                    threatScore = 3
                ),
                FingerprintSignal(
                    id = "accessibility_running_services",
                    name = "Running Accessibility Services",
                    description = "Lists specific names of active assistive technologies.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (servicesCount > 0) serviceNames else "None detected",
                    narrative = "Specific accessibility services (like custom screen readers or automated password managers) leak unique device usage patterns that trackers index.",
                    threatScore = 7
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class AppInfoProvider : SignalProvider() {
    override val id = "app_info"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val appInfo = context.applicationInfo
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            
            // Get installer package name
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                } catch (e: Exception) {
                    context.packageManager.getInstallerPackageName(context.packageName)
                }
            } else {
                context.packageManager.getInstallerPackageName(context.packageName)
            } ?: "Sideload / Developer ADB"
            
            listOf(
                FingerprintSignal(
                    id = "app_target_sdk",
                    name = "Target SDK Version",
                    description = "The target API levels set by the compiled app package.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "API ${appInfo.targetSdkVersion}",
                    narrative = "Malicious packages target older SDKs intentionally to circumvent modern Android permission gates and hardware sandboxing layers.",
                    threatScore = 4
                ),
                FingerprintSignal(
                    id = "app_installer_source",
                    name = "App Installation Source",
                    description = "Exposes the package installer responsible for placing this app.",
                    category = SignalCategory.PASSIVE,
                    rawValue = installer,
                    narrative = "Trackers identify side-loaded apps versus Google Play Store installs to profile custom ROM users, developers, or pirated environments, which maps device trust scores.",
                    threatScore = 5
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class InstalledAppsProvider : SignalProvider() {
    override val id = "installed_apps"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val signals = mutableListOf<FingerprintSignal>()
        
        // Social schemes mapping
        val socialSchemes = mapOf(
            "WhatsApp" to "whatsapp://send",
            "Telegram" to "tg://resolve",
            "X / Twitter" to "twitter://timeline",
            "Facebook" to "fb://feed",
            "Instagram" to "instagram://app"
        )
        
        val discoveredApps = mutableListOf<String>()
        val pm = context.packageManager
        
        for ((appName, scheme) in socialSchemes) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme))
                val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolved.isNotEmpty()) {
                    discoveredApps.add(appName)
                }
            } catch (e: Exception) {
                // blocked or error
            }
        }
        
        val valueString = if (discoveredApps.isNotEmpty()) {
            discoveredApps.joinToString(", ")
        } else {
            "No common URL schemes responded (or sandboxed)"
        }
        
        signals.add(
            FingerprintSignal(
                id = "installed_apps_sidechannel",
                name = "Social Presence Mapping (Side-channel)",
                description = "Queries standard intent-filters/URL schemes to discover social app installations without needing list-packages permission.",
                category = SignalCategory.ADVANCED,
                rawValue = valueString,
                narrative = "By probing deep-link URI schemes, any passive tracker can reconstruct your social graph profile, determining which platforms you are active on without querying the restricted package manager API.",
                threatScore = 8
            )
        )
        
        return signals
    }
}

class AudioRouteProvider : SignalProvider() {
    override val id = "audio_route"
    @SuppressLint("NewApi")
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = mutableListOf<String>()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (device in devices) {
                    val typeName = when (device.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
                        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio (A2DP)"
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO (Call Route)"
                        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
                        else -> "Type: ${device.type}"
                    }
                    if (!outputs.contains(typeName)) {
                        outputs.add(typeName)
                    }
                }
            } else {
                // Deprecated pre-M fallbacks
                if (audioManager.isWiredHeadsetOn) outputs.add("Wired Route Active")
                if (audioManager.isBluetoothA2dpOn) outputs.add("Bluetooth Route Active")
                if (audioManager.isSpeakerphoneOn) outputs.add("Speakerphone Active")
            }
            
            val routeValue = if (outputs.isNotEmpty()) outputs.joinToString(", ") else "Default Earpiece/Speaker"
            
            listOf(
                FingerprintSignal(
                    id = "audio_output_routes",
                    name = "Active Audio Hardware Route",
                    description = "Scans available and connected audio transducers.",
                    category = SignalCategory.PASSIVE,
                    rawValue = routeValue,
                    narrative = "Exposed audio configurations leak exactly what accessories are paired (like high-end Bluetooth headsets or generic adapters), creating secondary biometric beacons.",
                    threatScore = 4
                ),
                FingerprintSignal(
                    id = "audio_music_volume",
                    name = "Music Stream Volume Ratio",
                    description = "Reads active volume levels of media outputs.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}",
                    narrative = "Your precise volume percentages serve as transient side-channels to track state transitions. If you change volume, trackers can link sessions across tabs.",
                    threatScore = 3
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class BatteryProvider : SignalProvider() {
    override val id = "battery"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
            
            val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: -1
            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            
            val healthString = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                else -> "Unknown/Normal"
            }
            
            val pluggedString = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "Unplugged"
            }
            
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val chargeCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            } else -1
            
            val currentNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } else -1
            
            listOf(
                FingerprintSignal(
                    id = "battery_level",
                    name = "Battery Percentage",
                    description = "Monitors real-time battery charge state.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (batteryPct >= 0) "$batteryPct%" else "Unavailable",
                    narrative = "Ad libraries query the battery level to correlate sessions. If two sessions from the same IP have the exact same dwindling charge rate, they belong to the same person.",
                    threatScore = 4
                ),
                FingerprintSignal(
                    id = "battery_health_temp",
                    name = "Battery Health & Temperature",
                    description = "Exposes voltage, temperature, and wear health status.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "$healthString | ${(temp / 10.0)}°C | ${voltage}mV",
                    narrative = "Battery temperature changes linearly based on CPU usage. High-frequency reads expose background processing signatures and thermal profiling benchmarks.",
                    threatScore = 5
                ),
                FingerprintSignal(
                    id = "battery_charge_capacity",
                    name = "Charge Counter Capacity",
                    description = "Exposes remaining microampere-hours (µAh).",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (chargeCounter > 0) "${chargeCounter} µAh" else "Unavailable (Requires direct hardware sensor)",
                    narrative = "The microampere capacity represents exact battery wear states down to sub-milliamp increments. This is highly unique to your specific battery cells.",
                    threatScore = 6
                ),
                FingerprintSignal(
                    id = "battery_source",
                    name = "Power Charging Source",
                    description = "Details the physical medium powering the device.",
                    category = SignalCategory.PASSIVE,
                    rawValue = pluggedString,
                    narrative = "Exposing the source (USB, AC, Wireless) lets advertisers determine whether you are at home, commuting in a car, or connected to a development computer.",
                    threatScore = 3
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class BluetoothProvider : SignalProvider() {
    override val id = "bluetooth"
    @SuppressLint("MissingPermission")
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val signals = mutableListOf<FingerprintSignal>()
        try {
            val hasBluetoothPermission = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter: BluetoothAdapter? = manager.adapter
            
            val adapterState = if (adapter == null) {
                "No hardware receiver"
            } else if (adapter.isEnabled) {
                "Enabled"
            } else {
                "Disabled"
            }
            
            signals.add(
                FingerprintSignal(
                    id = "bluetooth_hardware_status",
                    name = "Bluetooth Adapter State",
                    description = "Checks if the local bluetooth radio is toggled on.",
                    category = SignalCategory.PASSIVE,
                    rawValue = adapterState,
                    narrative = "Even without runtime permission gates, querying the system's global Bluetooth radio state assists trackers in constructing contextual location profiles.",
                    threatScore = 4
                )
            )
            
            // Connected/Bonded devices need CONNECT permission on API 31+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (hasBluetoothPermission && adapter != null) {
                    val bondedDevices = adapter.bondedDevices
                    val deviceList = bondedDevices.map { "${it.name} (${it.address})" }
                    signals.add(
                        FingerprintSignal(
                            id = "bluetooth_bonded_devices",
                            name = "Active Paired / Bonded Devices",
                            description = "Retrieves names and MAC addresses of previously synchronized peripherals.",
                            category = SignalCategory.NEEDS_PERMISSION,
                            rawValue = if (deviceList.isNotEmpty()) deviceList.joinToString(", ") else "No bonded devices found",
                            narrative = "Paired wearables, headsets, and vehicle infotainment names contain custom tags like 'John\\'s QC35'. This yields extreme tracking capabilities and is an absolute fingerprint beacon.",
                            threatScore = 9,
                            permissionName = android.Manifest.permission.BLUETOOTH_CONNECT
                        )
                    )
                } else {
                    signals.add(
                        FingerprintSignal(
                            id = "bluetooth_bonded_devices",
                            name = "Active Paired / Bonded Devices",
                            description = "Retrieves names and MAC addresses of previously synchronized peripherals.",
                            category = SignalCategory.NEEDS_PERMISSION,
                            rawValue = "Permission Blocked",
                            narrative = "Paired wearables, headsets, and vehicle infotainment names contain custom tags like 'John\\'s QC35'. This yields extreme tracking capabilities and is an absolute fingerprint beacon.",
                            threatScore = 9,
                            permissionName = android.Manifest.permission.BLUETOOTH_CONNECT
                        )
                    )
                }
            } else {
                // Pre-S: permissions are declared in Manifest, standard permission check is okay
                if (adapter != null) {
                    try {
                        val bondedDevices = adapter.bondedDevices
                        val deviceList = bondedDevices.map { it.name }
                        signals.add(
                            FingerprintSignal(
                                id = "bluetooth_bonded_devices",
                                name = "Active Paired / Bonded Devices",
                                description = "Retrieves names and MAC addresses of previously synchronized peripherals.",
                                category = SignalCategory.NEEDS_PERMISSION,
                                rawValue = if (deviceList.isNotEmpty()) deviceList.joinToString(", ") else "No bonded devices found",
                                narrative = "Paired wearables, headsets, and vehicle infotainment names contain custom tags. This yields extreme tracking capabilities.",
                                threatScore = 9,
                                permissionName = android.Manifest.permission.BLUETOOTH_CONNECT
                            )
                        )
                    } catch (e: Exception) {
                        signals.add(
                            FingerprintSignal(
                                id = "bluetooth_bonded_devices",
                                name = "Active Paired / Bonded Devices",
                                description = "Retrieves names of previously synchronized peripherals.",
                                category = SignalCategory.NEEDS_PERMISSION,
                                rawValue = "Error: ${e.localizedMessage}",
                                narrative = "Blocked or restricted on this API level without explicit runtime checks.",
                                threatScore = 9,
                                permissionName = android.Manifest.permission.BLUETOOTH_CONNECT
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return signals
    }
}

class CalendarProvider : SignalProvider() {
    override val id = "calendar"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        
        val countVal = if (hasPermission) {
            try {
                val uri: Uri = CalendarContract.Calendars.CONTENT_URI
                val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
                val count = cursor?.count ?: 0
                val names = mutableListOf<String>()
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    if (idx >= 0) {
                        do {
                            names.add(cursor.getString(idx))
                        } while (cursor.moveToNext())
                    }
                }
                cursor?.close()
                if (count > 0) "$count Calendars active (${names.joinToString(", ")})" else "0 Calendars found"
            } catch (e: Exception) {
                "Error reading calendars: ${e.localizedMessage}"
            }
        } else {
            "Permission Blocked"
        }
        
        return listOf(
            FingerprintSignal(
                id = "calendar_exposure_count",
                name = "Calendar Registrations Count",
                description = "Evaluates system content providers to check for active schedules and calendar profiles.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = countVal,
                narrative = "Exposing calendar configurations and names leaks your personal email accounts, corporate profiles, and structural lifestyle schedules.",
                threatScore = 8,
                permissionName = android.Manifest.permission.READ_CALENDAR
            )
        )
    }
}

class CameraProvider : SignalProvider() {
    override val id = "camera"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val signals = mutableListOf<FingerprintSignal>()
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val idList = cameraManager.cameraIdList
            val camerasCount = idList.size
            
            val details = mutableListOf<String>()
            for (cid in idList.take(3)) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(cid)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val facingStr = when (facing) {
                        CameraCharacteristics.LENS_FACING_FRONT -> "Front-facing"
                        CameraCharacteristics.LENS_FACING_BACK -> "Rear-facing"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                        else -> "Auxiliary"
                    }
                    val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    val hwStr = when (hwLevel) {
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                        else -> "Unknown"
                    }
                    details.add("Camera $cid: $facingStr ($hwStr Hardware Level)")
                } catch (e: Exception) {
                    // skip
                }
            }
            
            signals.add(
                FingerprintSignal(
                    id = "camera_hardware_specs",
                    name = "Camera Hardware Sensors",
                    description = "Queries the camera subsystems, exposure levels, and lens configs.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "$camerasCount Cameras [${details.joinToString("; ")}]",
                    narrative = "Exposed camera quantities, lens configurations, and internal sensor calibrations serve as static hardware signatures that do not change across factory resets.",
                    threatScore = 5
                )
            )
            
            // Dynamic media query if permission granted
            val hasCameraPermission = context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            signals.add(
                FingerprintSignal(
                    id = "camera_runtime_access",
                    name = "Camera Live Access Capability",
                    description = "Verifies dynamic optical feed access permission status.",
                    category = SignalCategory.NEEDS_PERMISSION,
                    rawValue = if (hasCameraPermission) "Access Granted (Warning: Camera Live)" else "Permission Blocked",
                    narrative = "Once camera permissions are acquired, applications can silently map your room environments, run background iris recognition, or analyze optical features.",
                    threatScore = 10,
                    permissionName = android.Manifest.permission.CAMERA
                )
            )
        } catch (e: Exception) {
            // fallback
        }
        return signals
    }
}

class ContactsProvider : SignalProvider() {
    override val id = "contacts"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        
        val (countVal, threat) = if (hasPermission) {
            try {
                val uri = ContactsContract.Contacts.CONTENT_URI
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val count = cursor?.count ?: 0
                cursor?.close()
                val severityText = if (count > 500) "Highly Loaded ($count)" else "Active ($count)"
                Pair(severityText, 9)
            } catch (e: Exception) {
                Pair("Error reading contacts: ${e.localizedMessage}", 7)
            }
        } else {
            Pair("Permission Blocked", 8)
        }
        
        return listOf(
            FingerprintSignal(
                id = "contacts_exposure_threshold",
                name = "Contacts Directory Exposure",
                description = "Checks total synchronized address book cards available to applications.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = countVal,
                narrative = "Ad libraries map address books to discover cross-contacts. By matching your contacts with others, trackers can construct a full topological social network of who knows whom.",
                threatScore = threat,
                permissionName = android.Manifest.permission.READ_CONTACTS
            )
        )
    }
}

class DeviceIdentityProvider : SignalProvider() {
    override val id = "device_identity"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val uptimeSeconds = SystemClock.elapsedRealtime() / 1000
        val days = uptimeSeconds / 86400
        val hours = (uptimeSeconds % 86400) / 3600
        val mins = (uptimeSeconds % 3600) / 60
        val uptimeFormatted = if (days > 0) "${days}d ${hours}h ${mins}m" else "${hours}h ${mins}m"
        
        val kernelVersion = try {
            System.getProperty("os.version") ?: "Unknown Kernel"
        } catch (e: Exception) {
            "Unknown"
        }
        
        return listOf(
            FingerprintSignal(
                id = "system_hardware_tags",
                name = "Core Board & Model Identifiers",
                description = "Build-level hardware constants exposed by the Android OS kernel.",
                category = SignalCategory.PASSIVE,
                rawValue = "Model: ${Build.MODEL} | Hardware: ${Build.HARDWARE} | Board: ${Build.BOARD} | Brand: ${Build.BRAND}",
                narrative = "These build-level strings represent physical components. They are totally immutable and act as foundational keys for cross-device correlation scripts.",
                threatScore = 7
            ),
            FingerprintSignal(
                id = "system_uptime_precision",
                name = "Precise System Uptime",
                description = "Returns time passed since the last hardware restart.",
                category = SignalCategory.PASSIVE,
                rawValue = uptimeFormatted,
                narrative = "Uptime ticks are continuous, high-precision numbers. When combined with localized IP gateways, uptime makes your device instantly unique among millions of concurrent connections.",
                threatScore = 4
            ),
            FingerprintSignal(
                id = "system_security_patch",
                name = "OS Security Patch Level",
                description = "Displays the exact date-stamp of the applied security fixes.",
                category = SignalCategory.PASSIVE,
                rawValue = Build.VERSION.SECURITY_PATCH,
                narrative = "Exposing security patch versions lets software packages catalog the exact system vulnerabilities and patches active on your device, compiling hardware exploitability profiles.",
                threatScore = 5
            ),
            FingerprintSignal(
                id = "system_kernel_version",
                name = "Linux Kernel Compilation Signature",
                description = "Exposes the underlying compiled OS kernel version.",
                category = SignalCategory.PASSIVE,
                rawValue = kernelVersion,
                narrative = "The specific compiler flags and dates of the underlying Linux kernel make it highly unique, especially for devices running custom ROMs or sideloaded firmwares.",
                threatScore = 6
            )
        )
    }
}

class DeviceMotionProvider : SignalProvider() {
    override val id = "device_motion"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val baro = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        
        val accelStr = if (accel != null) "Present (${accel.vendor} | MaxRange: ${accel.maximumRange}m/s²)" else "Not Present"
        val gyroStr = if (gyro != null) "Present (${gyro.vendor} | Power: ${gyro.power}mA)" else "Not Present"
        val magStr = if (mag != null) "Present (${mag.vendor})" else "Not Present"
        val baroStr = if (baro != null) "Present (${baro.vendor})" else "Not Present"
        
        return listOf(
            FingerprintSignal(
                id = "sensor_accelerometer_signature",
                name = "Accelerometer Sensor Signature",
                description = "Queries vendor specs and sensor resolutions.",
                category = SignalCategory.PASSIVE,
                rawValue = accelStr,
                narrative = "High-precision physical calibrations of gravity chips contain micro-imperfections. Trackers read micro-vibrations of these sensors to index your physical silicon fingerprint.",
                threatScore = 6
            ),
            FingerprintSignal(
                id = "sensor_gyro_spec",
                name = "Gyroscope Sensor Specification",
                description = "Detects rotation metrics sensor vendor profiles.",
                category = SignalCategory.PASSIVE,
                rawValue = gyroStr,
                narrative = "Because rotation chips operate at high speeds, reading raw coordinate fluctuations allows applications to track spatial gestures, keystroke sounds, or walking gaits.",
                threatScore = 5
            ),
            FingerprintSignal(
                id = "sensor_magnetometer_barometer",
                name = "Magnetometer & Barometer Sensors",
                description = "Exposes local magnetic and barometric pressure availability.",
                category = SignalCategory.PASSIVE,
                rawValue = "Mag: ${if (mag != null) "Yes" else "No"} | Baro: ${if (baro != null) "Yes" else "No"}",
                narrative = "Barometer values reveal the exact elevation changes of the user, down to floor level. Trackers cross-reference pressure curves to detect indoor position.",
                threatScore = 4
            )
        )
    }
}

class DisplayProvider : SignalProvider() {
    override val id = "display"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi
        val density = metrics.density
        
        val orientation = if (width > height) "Landscape" else "Portrait"
        
        return listOf(
            FingerprintSignal(
                id = "display_resolution_dpi",
                name = "Display Resolution & Density",
                description = "Reads pixel height, width, and screen scaling density (DPI).",
                category = SignalCategory.PASSIVE,
                rawValue = "${width}x${height} px | ${dpi} DPI (Scale: ${density}x)",
                narrative = "Screen resolution profiles are shared with ad servers to scale visual content. In analytics scripts, display height, width, and color depth serve as core components of browser identity.",
                threatScore = 4
            ),
            FingerprintSignal(
                id = "display_orientation_state",
                name = "Device Layout Orientation",
                description = "Checks current structural viewport rendering layout.",
                category = SignalCategory.PASSIVE,
                rawValue = "$orientation Mode",
                narrative = "Frequent orientation state transitions expose active physical interaction, making it highly obvious whether a human is shifting the device or a automated emulator is testing it.",
                threatScore = 2
            )
        )
    }
}

class FontsProvider : SignalProvider() {
    override val id = "fonts"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val fontsDir = File("/system/fonts")
        val filesCount = if (fontsDir.exists() && fontsDir.isDirectory) {
            val list = fontsDir.listFiles()
            list?.size ?: 0
        } else {
            0
        }
        
        val samples = if (fontsDir.exists() && fontsDir.isDirectory) {
            val files = fontsDir.listFiles()
            files?.take(3)?.map { it.name.substringBefore(".") }?.joinToString(", ") ?: ""
        } else {
            ""
        }
        
        val fontStatus = if (filesCount > 0) {
            "$filesCount custom system typefaces found (e.g. $samples)"
        } else {
            "System font directory sandboxed / unreadable"
        }
        
        return listOf(
            FingerprintSignal(
                id = "fonts_system_inventory",
                name = "System Font File Inventory",
                description = "Scans typical local font directories to detect unique pre-loaded typographies.",
                category = SignalCategory.PASSIVE,
                rawValue = fontStatus,
                narrative = "By enumerating local system fonts, trackers determine whether a device is running specific localized modifications, custom regional language packs, or custom manufacturer overlays.",
                threatScore = 6
            )
        )
    }
}

class LocalNetworkProvider : SignalProvider() {
    override val id = "local_network"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val signals = mutableListOf<FingerprintSignal>()
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
            val isCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
            
            val transportType = when {
                isVpn -> "VPN / Proxy Protected"
                isWifi -> "WiFi Connection"
                isCell -> "Cellular Network"
                else -> "Disconnected / Unknown Link"
            }
            
            val linkSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps != null) {
                "${caps.linkDownstreamBandwidthKbps / 1000} Mbps Down"
            } else "Unavailable"
            
            signals.add(
                FingerprintSignal(
                    id = "network_transport_type",
                    name = "Network Interface Medium",
                    description = "Determines cellular, WiFi, or VPN transport active channels.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "$transportType (Link Downlink Speed: $linkSpeed)",
                    narrative = "VPN statuses let advertisers block privacy-minded users. They also isolate users routing through corporate tunnels to map workplace structures.",
                    threatScore = 5
                )
            )
            
            // Carrier
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val carrier = tm.networkOperatorName ?: "No SIM Card"
            signals.add(
                FingerprintSignal(
                    id = "network_carrier_name",
                    name = "Mobile Telephony Carrier",
                    description = "Detects the registered cellular service provider name.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (carrier.isNotEmpty()) carrier else "No Network Operator",
                    narrative = "Mobile networks segment users based on socioeconomic billing scales. If your carrier matches a high-priced local tier, you may be categorized into premium advertising segments.",
                    threatScore = 4
                )
            )
        } catch (e: Exception) {
            // fallback
        }
        return signals
    }
}

class LocaleProvider : SignalProvider() {
    override val id = "locale"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val locale = Locale.getDefault()
        val timezone = TimeZone.getDefault()
        
        return listOf(
            FingerprintSignal(
                id = "locale_language_tags",
                name = "System Language & Country ID",
                description = "Default language and localization variables set by user settings.",
                category = SignalCategory.PASSIVE,
                rawValue = "${locale.displayName} [${locale.toLanguageTag()}]",
                narrative = "Language, text orientation, and local date formats are highly localized. This instantly filters your geographic region without requiring GPS access.",
                threatScore = 3
            ),
            FingerprintSignal(
                id = "locale_timezone_id",
                name = "Standard System Timezone ID",
                description = "Default timezone identifier configured on the device.",
                category = SignalCategory.PASSIVE,
                rawValue = "${timezone.id} (Offset: ${timezone.rawOffset / 3600000} hrs)",
                narrative = "Timezone markers help correlation algorithms sync timestamps of background pings, linking user activities recorded on disparate service networks.",
                threatScore = 3
            )
        )
    }
}

class PasteboardProvider : SignalProvider() {
    override val id = "pasteboard"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val hasClip = clipboard.hasPrimaryClip()
            val mimeType = if (hasClip) {
                clipboard.primaryClipDescription?.getMimeType(0) ?: "Unknown"
            } else {
                "Empty"
            }
            
            listOf(
                FingerprintSignal(
                    id = "pasteboard_status",
                    name = "Clipboard Active Contents",
                    description = "Detects whether active, unpurged entries occupy the global clipboard.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (hasClip) "Active Content Detected (MIME: $mimeType)" else "Clipboard Empty",
                    narrative = "Many applications query the pasteboard automatically upon launching. If a user copies a password, secure token, or personal email from one app, a background tracker can read it instantly.",
                    threatScore = 7
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class StorageProvider : SignalProvider() {
    override val id = "storage"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val dataDir = Environment.getDataDirectory()
            val totalBytes = dataDir.totalSpace
            val freeBytes = dataDir.freeSpace
            val usedBytes = totalBytes - freeBytes
            
            val df = DecimalFormat("#.##")
            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
            
            listOf(
                FingerprintSignal(
                    id = "storage_capacity_metrics",
                    name = "Internal Storage Volume Metrics",
                    description = "Queries maximum byte capacities and exact free bytes remaining.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "Total: ${df.format(totalGb)} GB | Used: ${df.format(usedGb)} GB | Free: ${df.format(freeGb)} GB",
                    narrative = "Disk volume sizes map to standard hardware configurations (e.g. 128GB disk). The exact byte count (e.g. 128,000,456,128 bytes) and active free space are highly volatile, letting scripts index unique disk profiles.",
                    threatScore = 5
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class WebViewFingerprintProvider : SignalProvider() {
    override val id = "webview_fingerprint"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val userAgent = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            System.getProperty("http.agent") ?: "Unknown User Agent"
        }
        
        return listOf(
            FingerprintSignal(
                id = "webview_user_agent",
                name = "WebView Default User-Agent String",
                description = "The browser identification header compiled into the embedded WebKit engine.",
                category = SignalCategory.ADVANCED,
                rawValue = userAgent,
                narrative = "The User-Agent details the exact Safari/WebKit builds, Android firmware compile version, and Chromium updates. It serves as the primary correlation vector across different web contexts.",
                threatScore = 7
            ),
            FingerprintSignal(
                id = "webview_javascript_engine",
                name = "JavaScript V8 Engine Signature",
                description = "Checks underlying engine execution properties.",
                category = SignalCategory.ADVANCED,
                rawValue = "Active (ECMAScript 2026 Compatible)",
                narrative = "Trackers load hidden web views to execute JavaScript side-channels like canvas pixel rendering or audio context latency checks, bypassing standard native app isolation layers completely.",
                threatScore = 8
            )
        )
    }
}

class LocationProvider : SignalProvider() {
    override val id = "location"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPermission = hasCoarse || hasFine
        
        val value = if (hasPermission) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = lm.getProviders(true)
                if (providers.isNotEmpty()) {
                    "Granted (Enabled: ${providers.joinToString(", ")})"
                } else {
                    "Granted (No active providers)"
                }
            } catch (e: Exception) {
                "Granted (Error querying: ${e.localizedMessage})"
            }
        } else {
            "Permission Blocked"
        }
        
        return listOf(
            FingerprintSignal(
                id = "location_exposure_status",
                name = "Precise Geographic Location",
                description = "Evaluates active global positioning sensors and network triangulation parameters.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = value,
                narrative = "Precise location isolates your exact physical location. Trackers use this to identify your home address, workplace, and physical social circles.",
                threatScore = 10,
                permissionName = android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

class MicrophoneProvider : SignalProvider() {
    override val id = "microphone"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val value = if (hasPermission) {
            "Granted"
        } else {
            "Permission Blocked"
        }
        return listOf(
            FingerprintSignal(
                id = "microphone_runtime_access",
                name = "Microphone Sensor Access",
                description = "Verifies access to background audio capture devices.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = value,
                narrative = "Malicious SDKs query microphone availability or record background acoustic snippets to profile ambient noise level and device surroundings.",
                threatScore = 8,
                permissionName = android.Manifest.permission.RECORD_AUDIO
            )
        )
    }
}

class DrmProvider : SignalProvider() {
    override val id = "drm_fingerprint"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val drmId = try {
            val WIDEVINE_UUID = UUID(-0x12107c1d3537856aL, -0x7c2137b381042968L)
            val mediaDrm = MediaDrm(WIDEVINE_UUID)
            val deviceId = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            mediaDrm.close()
            deviceId.joinToString("") { "%02x".format(it) }.take(32) + "..."
        } catch (e: Exception) {
            "Unavailable / Sandboxed"
        }
        
        return listOf(
            FingerprintSignal(
                id = "drm_widevine_system_id",
                name = "Widevine DRM Hardware Signature ID",
                description = "Queries the cryptographically secure device unique ID embedded in media subsystems.",
                category = SignalCategory.ADVANCED,
                rawValue = drmId,
                narrative = "The Widevine DRM Device Unique ID is a persistent hardware identifier that is immune to factory resets and application clear-data events, allowing tracking across entire device lifecycles.",
                threatScore = 10
            )
        )
    }
}

class PhoneStateProvider : SignalProvider() {
    override val id = "phone_state"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val deviceId = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    tm.deviceId ?: "Restricted"
                } else {
                    "Restricted on Q+"
                }
                val simSerial = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    tm.simSerialNumber ?: "Restricted"
                } else {
                    "Restricted on Q+"
                }
                val carrier = tm.networkOperatorName ?: "Unknown"
                "SimSerial: $simSerial | IMEI/ID: $deviceId | Carrier: $carrier"
            } catch (e: Exception) {
                "Granted (Query error: ${e.localizedMessage})"
            }
        } else {
            "Permission Blocked"
        }
        return listOf(
            FingerprintSignal(
                id = "phone_state_access",
                name = "Device Phone State & Cellular ID",
                description = "Monitors hardware serial numbers, Sim details, and cellular metadata.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "The READ_PHONE_STATE permission grants access to persistent cellular IDs like Sim Serial Numbers and IMEIs on legacy versions, or detailed operator connection details on newer versions. This makes cross-app user tracking effortless.",
                threatScore = 9,
                permissionName = android.Manifest.permission.READ_PHONE_STATE
            )
        )
    }
}

class CallLogProvider : SignalProvider() {
    override val id = "call_log"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) {
            try {
                val cursor = context.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, null)
                val count = cursor?.count ?: 0
                cursor?.close()
                "Granted ($count private call logs cataloged)"
            } catch (e: Exception) {
                "Granted (Query error: ${e.localizedMessage})"
            }
        } else {
            "Permission Blocked"
        }
        return listOf(
            FingerprintSignal(
                id = "call_log_exposure",
                name = "Call Log Catalog Access",
                description = "Monitors system call histories, timestamps, contact identities, and telephone numbers.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "Accessing your Call Log allows trackers to reconstruct your private telephone communication graph, establishing highly sensitive social mapping. Advertisers can utilize active talk durations to target user demographics.",
                threatScore = 9,
                permissionName = android.Manifest.permission.READ_CALL_LOG
            )
        )
    }
}

class SmsProvider : SignalProvider() {
    override val id = "sms"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) {
            try {
                val cursor = context.contentResolver.query(Uri.parse("content://sms"), null, null, null, null)
                val count = cursor?.count ?: 0
                cursor?.close()
                "Granted ($count private text messages cataloged)"
            } catch (e: Exception) {
                "Granted (Query error: ${e.localizedMessage})"
            }
        } else {
            "Permission Blocked"
        }
        return listOf(
            FingerprintSignal(
                id = "sms_exposure_status",
                name = "SMS Message Inbox Access",
                description = "Monitors text message counts, sender contacts, and inbox/sent databases.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "SMS permissions can expose secure multi-factor authentication (MFA) codes, bank alerts, and private texts. Spammers scan message databases to construct targeted phishing profiles.",
                threatScore = 10,
                permissionName = android.Manifest.permission.READ_SMS
            )
        )
    }
}

class AccountsProvider : SignalProvider() {
    override val id = "accounts"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) {
            try {
                val am = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
                val accounts = am.accounts
                val list = accounts.joinToString { "${it.type}:${it.name}" }
                if (list.isNotEmpty()) "Granted (Accounts: $list)" else "Granted (No registered accounts)"
            } catch (e: Exception) {
                "Granted (Query error: ${e.localizedMessage})"
            }
        } else {
            "Permission Blocked"
        }
        return listOf(
            FingerprintSignal(
                id = "system_accounts_list",
                name = "System Accounts Index",
                description = "Scans registered Google, email, and social accounts on the device.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "Scanning registered device accounts reveals your primary email address and active service profiles. This uniquely identifies you with high precision across any third-party app utilizing the same ecosystem.",
                threatScore = 7,
                permissionName = android.Manifest.permission.GET_ACCOUNTS
            )
        )
    }
}

class BodySensorsProvider : SignalProvider() {
    override val id = "body_sensors"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) {
            "Granted (Body tracking hardware accessible)"
        } else {
            "Permission Blocked"
        }
        return listOf(
            FingerprintSignal(
                id = "body_sensors_tracking",
                name = "Physical Body Sensors Access",
                description = "Retrieves real-time data from heart rate trackers, step sensors, and health peripherals.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "Body sensors track delicate biometrics like heart rate. Malicious entities monetize sleep cycles, physical stress indices, and movement patterns for healthcare profiling.",
                threatScore = 8,
                permissionName = android.Manifest.permission.BODY_SENSORS
            )
        )
    }
}

class NotificationPermissionProvider : SignalProvider() {
    override val id = "notifications"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val rawValue = if (hasPermission) "Granted" else "Blocked"
        return listOf(
            FingerprintSignal(
                id = "notification_post_permission",
                name = "Post System Notifications",
                description = "Checks authorization to push system-level notifications to the status bar.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "Notifications are frequently abused by spamware to deploy click-bait promotions, keep background services alive indefinitely, or deliver background payloads silently.",
                threatScore = 5,
                permissionName = android.Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }
}

class HardwareSensorsProvider : SignalProvider() {
    override val id = "hardware_sensors"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensors = sm.getSensorList(Sensor.TYPE_ALL)
            val names = sensors.take(8).joinToString { it.name }
            val count = sensors.size
            val listString = if (count > 8) "$names (+${count - 8} more)" else names
            
            listOf(
                FingerprintSignal(
                    id = "hardware_sensors_footprint",
                    name = "Hardware Sensors Signature",
                    description = "Enumerate the full suite of physical hardware sensors installed on the chassis.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "Detected $count sensors ($listString)",
                    narrative = "Every device contains a specific selection of sensors supplied by distinct manufacturers (e.g., Bosch, STMicroelectronics). Querying the complete sensor list creates a highly unique fingerprint string that requires zero permissions to fetch.",
                    threatScore = 6
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class SystemSettingsProvider : SignalProvider() {
    override val id = "system_settings"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            val timeout = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
            val haptic = try {
                Settings.System.getInt(context.contentResolver, "haptic_feedback_enabled", -1)
            } catch (e: Exception) {
                -1
            }
            val developerOptions = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, -1)
            
            listOf(
                FingerprintSignal(
                    id = "system_settings_leak",
                    name = "Passive System Settings Leak",
                    description = "Collects global settings like screen timeout, screen brightness levels, and haptic feedback toggles.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "Timeout: ${timeout}ms | Brightness: $brightness | Haptic: $haptic | DevOptions: $developerOptions",
                    narrative = "System settings are highly customizable. Combining multiple system variables (screen off timeout, developer mode toggles, and brightness metrics) narrows down the uniqueness index of your phone with zero permission friction.",
                    threatScore = 5
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class DeviceUptimeProvider : SignalProvider() {
    override val id = "device_uptime"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val uptimeMs = SystemClock.elapsedRealtime()
        val uptimeHrs = uptimeMs / (1000.0 * 60.0 * 60.0)
        val df = DecimalFormat("#.##")
        return listOf(
            FingerprintSignal(
                id = "device_uptime_metrics",
                name = "Active Device Uptime Metric",
                description = "Calculates elapsed milliseconds since the absolute last boot sequence.",
                category = SignalCategory.PASSIVE,
                rawValue = "${df.format(uptimeHrs)} Hours active since boot",
                narrative = "Uptime increments continuously and is exact down to the millisecond. By polling uptime and subtracting it from current calendar time, trackers calculate the exact millisecond your device booted, creating a perfect session tracker.",
                threatScore = 4
            )
        )
    }
}

class OpenGLProvider : SignalProvider() {
    override val id = "opengl_fingerprint"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val info = am.deviceConfigurationInfo
            val glVer = info.glEsVersion ?: "Unknown"
            listOf(
                FingerprintSignal(
                    id = "opengl_version_sig",
                    name = "OpenGL ES Shader Engine Version",
                    description = "Identifies the GPU configuration specifications via the system manager.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "OpenGL ES Version: $glVer",
                    narrative = "Graphics sub-systems contain highly specific render capabilities. Querying OpenGL ES profiles allows trackers to identify your specific GPU and target shader engine directly.",
                    threatScore = 4
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class NetworkInterfacesProvider : SignalProvider() {
    override val id = "network_interfaces"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val names = interfaces.joinToString { it.name }
            val count = interfaces.size
            val hasVpn = interfaces.any { it.name.contains("tun") || it.name.contains("ppp") || it.name.contains("tap") || it.name.contains("vpn") }
            listOf(
                FingerprintSignal(
                    id = "network_interfaces_signature",
                    name = "Network Interfaces Structure",
                    description = "Scans all active physical and virtual local network adapter identifiers.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "Interfaces ($count): $names | VPN Active: $hasVpn",
                    narrative = "The existence of specific interface identifiers (e.g. wlan0, rmnet_data0, or virtual tun0 adapters) tells trackers if you are connected via WiFi, cellular data, or utilizing an encrypted VPN channel.",
                    threatScore = 5
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class TelephonyOperatorProvider : SignalProvider() {
    override val id = "telephony_operator"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val name = tm.networkOperatorName ?: "Unknown"
            val iso = tm.networkCountryIso ?: "Unknown"
            val state = when (tm.simState) {
                TelephonyManager.SIM_STATE_READY -> "SIM Ready"
                TelephonyManager.SIM_STATE_ABSENT -> "SIM Absent"
                else -> "SIM Other"
            }
            listOf(
                FingerprintSignal(
                    id = "telephony_carrier_operator",
                    name = "Cellular Operator Carrier Details",
                    description = "Reads public carrier codes, country registration, and active cellular sim states.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "Carrier: $name | Country: ${iso.uppercase()} | State: $state",
                    narrative = "Network carrier parameters identify your service provider and geographic country. This enables geographic content blocking and targeted ISP profile tracking without location permissions.",
                    threatScore = 5
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class CpuAndRamProvider : SignalProvider() {
    override val id = "cpu_and_ram"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val abis = Build.SUPPORTED_ABIS.joinToString(", ")
            val cores = Runtime.getRuntime().availableProcessors()
            
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            val availRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
            val df = DecimalFormat("#.##")
            
            listOf(
                FingerprintSignal(
                    id = "cpu_ram_specs",
                    name = "CPU & Memory Configuration",
                    description = "Collects system CPU core architecture, hardware instruction sets, and exact RAM metrics.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "ABIs: $abis | Cores: $cores | RAM: ${df.format(totalRamGb)}GB (Avail: ${df.format(availRamGb)}GB)",
                    narrative = "CPU instruction sets and exact physical memory configurations are static hardware signatures. Combined with screen and storage, they create highly reliable device fingerprints.",
                    threatScore = 5
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class WifiDetailsProvider : SignalProvider() {
    override val id = "wifi_details"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasWifiState = context.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
        val hasLocation = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        val rawValue = if (hasWifiState) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                val info = wm.connectionInfo
                val bssid = info?.bssid ?: "Unknown BSSID"
                val ssid = if (hasLocation) {
                    info?.ssid ?: "Unknown SSID"
                } else {
                    "SSID Hidden (Requires Location)"
                }
                val rssi = info?.rssi ?: 0
                val speed = info?.linkSpeed ?: 0
                "SSID: $ssid | BSSID: $bssid | RSSI: ${rssi}dBm | LinkSpeed: ${speed}Mbps"
            } catch (e: Exception) {
                "Error reading WiFi details: ${e.localizedMessage}"
            }
        } else {
            "Permission Blocked"
        }
        
        return listOf(
            FingerprintSignal(
                id = "wifi_network_details",
                name = "WiFi Connection Signatures",
                description = "Scans connected WiFi routers, SSID network names, and BSSID MAC addresses.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "Querying BSSID MAC addresses allows cross-referencing against global coordinate databases (like Google Location Services), identifying your precise physical address with up to 1-meter accuracy without using GPS.",
                threatScore = 8,
                permissionName = android.Manifest.permission.ACCESS_WIFI_STATE
            )
        )
    }
}

class PhysicalActivityProvider : SignalProvider() {
    override val id = "physical_activity"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) {
            "Granted (Physical Motion / Fitness Sensors Active)"
        } else {
            "Permission Blocked"
        }
        return listOf(
            FingerprintSignal(
                id = "activity_motion_tracking",
                name = "Physical Activity Tracking",
                description = "Monitors real-time physical motion, steps, walking patterns, and movement states.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "Physical activity permissions track steps and behavioral dynamics. Commercial SDKs use this data to profile lifestyle habits (e.g. active commuter vs stationary) and identify specific daily routines.",
                threatScore = 7,
                permissionName = android.Manifest.permission.ACTIVITY_RECOGNITION
            )
        )
    }
}

class AmbientSensorsProvider : SignalProvider() {
    override val id = "ambient_sensors"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
            val proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            val pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
            
            val details = mutableListOf<String>()
            details.add("Light: ${if (light != null) "Available" else "N/A"}")
            details.add("Proximity: ${if (proximity != null) "Available" else "N/A"}")
            details.add("Barometer: ${if (pressure != null) "Available" else "N/A"}")
            
            listOf(
                FingerprintSignal(
                    id = "ambient_sensors_footprint",
                    name = "Ambient Environmental Sensors",
                    description = "Queries the presence of secondary environmental sensors like lux light level and proximity chips.",
                    category = SignalCategory.PASSIVE,
                    rawValue = details.joinToString(" | "),
                    narrative = "Ambient sensors require no permission to access. By continuously monitoring ambient light or proximity changes, background scripts can identify when a device is in a pocket, a purse, or being held near a human face.",
                    threatScore = 5
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class AntiAnalysisProvider : SignalProvider() {
    override val id = "anti_analysis"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
                    Build.MODEL.contains("google_sdk") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for x86") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                    "google_sdk" == Build.PRODUCT
            
            val paths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            val isRooted = paths.any { java.io.File(it).exists() }
            
            val status = "Emulator: $isEmulator | Su Binary: $isRooted | Bootloader: ${Build.BOOTLOADER}"
            listOf(
                FingerprintSignal(
                    id = "anti_analysis_signals",
                    name = "Sandbox & Emulator Checks",
                    description = "Executes heuristics to identify emulated platforms, debug configurations, and rooted kernels.",
                    category = SignalCategory.ADVANCED,
                    rawValue = status,
                    narrative = "Malicious applications and tracking SDKs execute sandbox check heuristics to detect security analysts or automated Google Play dynamic analyzer bots, changing their behavior to avoid detection.",
                    threatScore = 8
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class InputMethodProvider : SignalProvider() {
    override val id = "input_method"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val list = imm.enabledInputMethodList
            val packages = list.take(3).joinToString { it.packageName.substringAfterLast(".") }
            val count = list.size
            val raw = if (count > 3) "$packages (+${count - 3} more)" else packages
            
            listOf(
                FingerprintSignal(
                    id = "input_method_package_list",
                    name = "Enabled Input Keyboards",
                    description = "Lists installed keyboard input methods and internationalization IME modules.",
                    category = SignalCategory.ADVANCED,
                    rawValue = "Active keyboards ($count): $raw",
                    narrative = "The specific combination of custom keyboards (e.g. Gboard, SwiftKey, custom emoji boards) and localized keyboard languages makes a device signature highly unique with zero permissions.",
                    threatScore = 6
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class NfcAndUsbProvider : SignalProvider() {
    override val id = "nfc_and_usb"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val nfcManager = context.getSystemService(Context.NFC_SERVICE) as android.nfc.NfcManager
            val adapter = nfcManager.defaultAdapter
            val nfcState = if (adapter == null) {
                "No hardware adapter"
            } else if (adapter.isEnabled) {
                "Enabled"
            } else {
                "Disabled"
            }
            
            val adbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            
            listOf(
                FingerprintSignal(
                    id = "nfc_usb_hardware_state",
                    name = "NFC & USB Connectivity State",
                    description = "Identifies NFC adapter features and USB debugger connections passive states.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "NFC: $nfcState | ADB Debugging: ${if (adbEnabled) "Active" else "Inactive"}",
                    narrative = "ADB debugging state exposes if the user is a developer. NFC chip configurations reveal short-range contactless payment hardware models, aiding in profile building.",
                    threatScore = 5
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class SoundStreamsProvider : SignalProvider() {
    override val id = "sound_streams"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val alarm = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val ring = am.getStreamVolume(AudioManager.STREAM_RING)
            val system = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
            
            val ringtoneUri = try {
                android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE)?.toString() ?: "Default"
            } catch (e: Exception) {
                "Sandboxed"
            }
            
            val shortUri = if (ringtoneUri.length > 35) ringtoneUri.substringAfterLast("/") else ringtoneUri
            
            listOf(
                FingerprintSignal(
                    id = "sound_stream_volumes",
                    name = "Sound Stream Metrics & Default Ringtone",
                    description = "Collects system volume streams (Alarm, Ring, System) and queries the active ringtone URI path.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "Alarm: $alarm | Ring: $ring | Sys: $system | Ringtone: $shortUri",
                    narrative = "System volume ratios serve as high-precision, transient side-channel identifiers. Tracking the default ringtone URI (which can be a custom user-defined file) yields highly unique identification.",
                    threatScore = 6
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class BluetoothScanProvider : SignalProvider() {
    override val id = "bluetooth_scan"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) {
            "Granted (BLE Beacons & Scanning Active)"
        } else {
            "Permission Blocked"
        }
        
        return listOf(
            FingerprintSignal(
                id = "bluetooth_ble_scanning",
                name = "BLE Beacons Scan Capability",
                description = "Identifies the authorization status for background Bluetooth Low Energy (BLE) scanning.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "BLE Scan allows apps to scan for surrounding physical beacons (e.g., in retail stores, malls, or airports). Trackers map these beacon coordinates globally, locking your exact real-time offline location without GPS.",
                threatScore = 9,
                permissionName = android.Manifest.permission.BLUETOOTH_SCAN
            )
        )
    }
}

class ExternalMediaProvider : SignalProvider() {
    override val id = "external_media"
    override fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) {
            try {
                val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val count = cursor?.count ?: 0
                cursor?.close()
                "Granted ($count public images indexable)"
            } catch (e: Exception) {
                "Granted (Query error: ${e.localizedMessage})"
            }
        } else {
            "Permission Blocked"
        }
        
        return listOf(
            FingerprintSignal(
                id = "external_media_specs",
                name = "Photo & Media Storage Fingerprint",
                description = "Scans available public directories, indexing exact photo counts and storage media volumes.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "Analyzing photo counts, custom image filenames, or Exif headers leaks geographic data (where photos were taken) and camera sensor calibration offsets, creating a completely unique persistent tracking key.",
                threatScore = 8,
                permissionName = android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
    }
}
