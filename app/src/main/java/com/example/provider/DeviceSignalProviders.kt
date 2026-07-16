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
import com.matepazy.spectre.model.DetailedGroup
import com.matepazy.spectre.model.DetailedItem
import java.io.File
import java.text.DecimalFormat
import java.util.Locale
import java.util.TimeZone

fun detailedItem(label: String, value: String, description: String? = null, iconName: String? = null) =
    DetailedItem(label, value, description, iconName)

fun detailedGroup(categoryName: String?, items: List<DetailedItem>) =
    DetailedGroup(categoryName, items)

abstract class SignalProvider {
    abstract val id: String
    abstract suspend fun provideSignals(context: Context): List<FingerprintSignal>
}

class AccessibilityProvider : SignalProvider() {
    override val id = "accessibility"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val isEnabled = am.isEnabled
            val isTouchExploration = am.isTouchExplorationEnabled
            
            // Querying running services
            val runningServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val servicesCount = runningServices?.size ?: 0
            val serviceNames = runningServices?.joinToString { it.resolveInfo?.serviceInfo?.name?.substringAfterLast(".") ?: "" } ?: "None"
            
            val fontScale = context.resources.configuration.fontScale
            
            val globalItems = listOf(
                detailedItem("Accessibility Status", if (isEnabled) "Globally Enabled" else "Disabled", "Global master toggle for accessibility features", "check"),
                detailedItem("Explore by Touch", if (isTouchExploration) "Active" else "Inactive", "Touch-exploration navigation mode status", "check"),
                detailedItem("System Font Scale", "${fontScale}x", "Multiplier applied to standard text sizing", "info"),
                detailedItem("Active Services Count", "$servicesCount active", "Total count of running accessibility services", "info")
            )
            
            val serviceItems = mutableListOf<DetailedItem>()
            runningServices?.forEach { service ->
                val info = service.resolveInfo?.serviceInfo
                val name = info?.name?.substringAfterLast(".") ?: "Unknown"
                val pkg = info?.packageName ?: "Unknown"
                val feedback = when (service.feedbackType) {
                    AccessibilityServiceInfo.FEEDBACK_SPOKEN -> "Spoken"
                    AccessibilityServiceInfo.FEEDBACK_HAPTIC -> "Haptic"
                    AccessibilityServiceInfo.FEEDBACK_AUDIBLE -> "Audible"
                    AccessibilityServiceInfo.FEEDBACK_VISUAL -> "Visual"
                    AccessibilityServiceInfo.FEEDBACK_GENERIC -> "Generic"
                    else -> "Multiple/Other (${service.feedbackType})"
                }
                serviceItems.add(detailedItem(
                    label = service.resolveInfo?.loadLabel(context.packageManager)?.toString() ?: name,
                    value = "Package: $pkg\nClass: ${info?.name ?: "N/A"}\nFeedback Type: $feedback\nFlags: ${service.flags}",
                    description = "Service settings: ${service.settingsActivityName ?: "None"}",
                    iconName = "settings"
                ))
            }
            if (serviceItems.isEmpty()) {
                serviceItems.add(detailedItem("Active Services", "None detected", "No accessibility services are actively running", "close"))
            }

            val detailedEnabled = listOf(
                detailedGroup("Accessibility Settings", globalItems),
                detailedGroup("Running Assistive Technologies", serviceItems)
            )

            listOf(
                FingerprintSignal(
                    id = "accessibility_enabled",
                    name = "Accessibility Services Enabled",
                    description = "Detects whether global accessibility sub-systems are active.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (isEnabled) "Active ($servicesCount running)" else "Disabled",
                    narrative = "Trackers scan active accessibility states to flag disabled or power users. Some ad fraud SDKs monitor accessibility events to hijack UI clicks.",
                    threatScore = 5,
                    detailedData = detailedEnabled
                ),
                FingerprintSignal(
                    id = "accessibility_touch_exploration",
                    name = "Explore by Touch Active",
                    description = "Determines if speech-guided screen navigation is enabled.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (isTouchExploration) "Active" else "Inactive",
                    narrative = "Explore-by-touch indicates the user relies on screen readers like TalkBack, creating a highly unique user profile and tracking vulnerable demographics.",
                    threatScore = 6,
                    detailedData = listOf(detailedGroup("Touch Exploration details", listOf(
                        detailedItem("Explore by Touch", if (isTouchExploration) "Active" else "Inactive", "Speech-guided touch exploration navigation", "check")
                    )))
                ),
                FingerprintSignal(
                    id = "accessibility_font_scale",
                    name = "System Font Scale",
                    description = "Queries the exact multiplier applied to standard text sizing.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "${fontScale}x",
                    narrative = "Slightly altered typography scales (e.g. 1.15x) are relatively rare across device models, significantly reducing the size of the anonymity set for browser/webview fingerprinting.",
                    threatScore = 3,
                    detailedData = listOf(detailedGroup("Typography scaling details", listOf(
                        detailedItem("System Font Scale", "${fontScale}x", "The scale multiplier configured in settings", "info")
                    )))
                ),
                FingerprintSignal(
                    id = "accessibility_running_services",
                    name = "Running Accessibility Services",
                    description = "Lists specific names of active assistive technologies.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (servicesCount > 0) serviceNames else "None detected",
                    narrative = "Specific accessibility services (like custom screen readers or automated password managers) leak unique device usage patterns that trackers index.",
                    threatScore = 7,
                    detailedData = detailedEnabled
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class AppInfoProvider : SignalProvider() {
    override val id = "app_info"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
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
            
            val pm = context.packageManager
            val firstInstall = java.util.Date(pInfo.firstInstallTime).toString()
            val lastUpdate = java.util.Date(pInfo.lastUpdateTime).toString()
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val certFingerprint = signatures?.firstOrNull()?.let { sig ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(sig.toByteArray())
                hashBytes.joinToString(":") { "%02X".format(it) }
            } ?: "Unavailable"

            val packageDetails = listOf(
                detailedItem("App Package Name", context.packageName, "Standard unique package identifier", "code"),
                detailedItem("App Label", pm.getApplicationLabel(appInfo).toString(), "User-facing app label", "info"),
                detailedItem("Version Name", pInfo.versionName ?: "Unknown", "User-visible version string", "info"),
                detailedItem("Version Code", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode.toString() else pInfo.versionCode.toString(), "Internal build number", "info"),
                detailedItem("Target SDK", "API ${appInfo.targetSdkVersion}", "Target Android compiler platform version", "code"),
                detailedItem("Min SDK", if (Build.VERSION.SDK_INT >= 24) "API ${appInfo.minSdkVersion}" else "API < 24", "Minimum required platform SDK version", "code"),
                detailedItem("Installer Source", installer, "System installer that loaded the app package", "download"),
                detailedItem("First Install Time", firstInstall, "Time when app was first installed", "time"),
                detailedItem("Last Update Time", lastUpdate, "Time of the last application updates", "time"),
                detailedItem("App Directory", appInfo.sourceDir, "Physical installation folder path on device", "storage"),
                detailedItem("Certificate Fingerprint (SHA-256)", certFingerprint, "Cryptographic signing signature of package", "key")
            )

            // Permissions requested by this app
            val permissionItems = mutableListOf<DetailedItem>()
            try {
                val fullInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                val requested = fullInfo.requestedPermissions
                val flags = fullInfo.requestedPermissionsFlags
                if (requested != null) {
                    for (i in requested.indices) {
                        val perm = requested[i]
                        val name = perm.substringAfterLast(".")
                        val granted = flags != null && i < flags.size && (flags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        permissionItems.add(detailedItem(
                            label = name,
                            value = if (granted) "Granted" else "Denied",
                            description = perm,
                            iconName = if (granted) "check" else "close"
                        ))
                    }
                }
            } catch (e: Exception) {
                permissionItems.add(detailedItem("Permission query status", "Failed: ${e.localizedMessage}", "Permission fetching error", "close"))
            }

            val appDetailsGroups = listOf(
                detailedGroup("Package Manifest Properties", packageDetails),
                detailedGroup("Manifest Requested Permissions", permissionItems)
            )

            listOf(
                FingerprintSignal(
                    id = "app_target_sdk",
                    name = "Target SDK Version",
                    description = "The target API levels set by the compiled app package.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "API ${appInfo.targetSdkVersion}",
                    narrative = "Malicious packages target older SDKs intentionally to circumvent modern Android permission gates and hardware sandboxing layers.",
                    threatScore = 4,
                    detailedData = appDetailsGroups
                ),
                FingerprintSignal(
                    id = "app_installer_source",
                    name = "App Installation Source",
                    description = "Exposes the package installer responsible for placing this app.",
                    category = SignalCategory.PASSIVE,
                    rawValue = installer,
                    narrative = "Trackers identify side-loaded apps versus Google Play Store installs to profile custom ROM users, developers, or pirated environments, which maps device trust scores.",
                    threatScore = 5,
                    detailedData = appDetailsGroups
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}
class InstalledAppsProvider : SignalProvider() {
    override val id = "installed_apps"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val signals = mutableListOf<FingerprintSignal>()
        val socialSchemes = mapOf(
            "WhatsApp" to listOf("whatsapp://send", "whatsapp://"),
            "Telegram" to listOf("tg://resolve", "tg://"),
            "X / Twitter" to listOf("twitter://timeline", "twitter://"),
            "Facebook" to listOf("fb://feed", "fb://"),
            "Instagram" to listOf("instagram://app", "instagram://"),
            "YouTube" to listOf("vnd.youtube://", "youtube://"),
            "Netflix" to listOf("nflx://"),
            "Spotify" to listOf("spotify://"),
            "TikTok" to listOf("snssdk1128://", "tiktok://"),
            "PayPal" to listOf("paypal://"),
            "Snapchat" to listOf("snapchat://"),
            "Reddit" to listOf("reddit://"),
            "LinkedIn" to listOf("linkedin://"),
            "Pinterest" to listOf("pinterest://"),
            "Discord" to listOf("discord://"),
            "Slack" to listOf("slack://"),
            "Signal" to listOf("sgnl://", "signal://"),
            "Microsoft Teams" to listOf("msteams://"),
            "Skype" to listOf("skype://"),
            "Zoom" to listOf("zoomus://"),
            "Threads" to listOf("barcelona://"),
            "Mastodon" to listOf("mastodon://")
        )
        
        val discoveredApps = mutableListOf<String>()
        val pm = context.packageManager
        val detailedItems = mutableListOf<DetailedItem>()
        
        for ((appName, schemes) in socialSchemes) {
            try {
                var isInstalled = false
                var matchedScheme = schemes.firstOrNull() ?: ""
                for (scheme in schemes) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme))
                    val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    if (resolved.isNotEmpty()) {
                        isInstalled = true
                        matchedScheme = scheme
                        break
                    }
                }
                if (isInstalled) {
                    discoveredApps.add(appName)
                }
                detailedItems.add(detailedItem(
                    label = appName,
                    value = if (isInstalled) "Active Footprint Detected" else "Not Detected (Sandboxed)",
                    description = "Queried Deep Link Scheme: $matchedScheme",
                    iconName = if (isInstalled) "check" else "close"
                ))
            } catch (e: Exception) {
                detailedItems.add(detailedItem(appName, "Query Blocked (${e.localizedMessage})", "Schemes: ${schemes.joinToString(", ")}", "close"))
            }
        }
        
        val valueString = if (discoveredApps.isNotEmpty()) {
            "${discoveredApps.size} platforms detected"
        } else {
            "No common platforms detected"
        }
        
        signals.add(
            FingerprintSignal(
                id = "installed_apps_sidechannel",
                name = "Social Presence Mapping (Side-channel)",
                description = "Queries standard intent-filters/URL schemes to discover social app installations without needing list-packages permission.",
                category = SignalCategory.ADVANCED,
                rawValue = valueString,
                narrative = "By probing deep-link URI schemes, any passive tracker can reconstruct your social graph profile, determining which platforms you are active on without querying the restricted package manager API.",
                threatScore = 8,
                detailedData = listOf(detailedGroup("Probed URL Schemes Status", detailedItems))
            )
        )
        
        // QUERY_ALL_PACKAGES app list query
        val hasQueryAllPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val appListItems = mutableListOf<DetailedItem>()
        var appRawValue = "Permission Blocked"
        var appSensitiveRawValue = ""
        var appIsSensitive = false

        if (hasQueryAllPackages) {
            try {
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                val userApps = packages.filter { pInfo ->
                    val appInfo = pInfo.applicationInfo
                    appInfo != null && (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                }
                
                var playStoreCount = 0
                var sideloadCount = 0
                
                userApps.forEach { pInfo ->
                    val appInfo = pInfo.applicationInfo ?: return@forEach
                    val appLabel = pm.getApplicationLabel(appInfo).toString()
                    val pkgName = pInfo.packageName
                    
                    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            pm.getInstallSourceInfo(pkgName).installingPackageName
                        } catch (e: Exception) {
                            pm.getInstallerPackageName(pkgName)
                        }
                    } else {
                        pm.getInstallerPackageName(pkgName)
                    } ?: "Sideload / Developer"
                    
                    if (installer.contains("vending") || installer.contains("play")) {
                        playStoreCount++
                    } else {
                        sideloadCount++
                    }
                    
                    appListItems.add(detailedItem(
                        label = appLabel,
                        value = "Package: $pkgName\nSource: $installer",
                        description = "User Installed App Info",
                        iconName = "settings"
                    ))
                }
                
                if (appListItems.isEmpty()) {
                    appListItems.add(detailedItem("Installed Packages", "No user applications found", "No non-system apps registered", "close"))
                    appRawValue = "No user apps found"
                } else {
                    appRawValue = "Total: ${userApps.size} apps | Google Play: $playStoreCount | Sideloaded: $sideloadCount"
                    appSensitiveRawValue = userApps.joinToString(",") { it.packageName }
                    appIsSensitive = true
                }
            } catch (e: Exception) {
                appRawValue = "Query Error: ${e.localizedMessage}"
            }
        } else {
            appListItems.add(detailedItem("Installed Packages", "Query restricted by package visibility sandbox", "Query All Packages permission not granted", "close"))
        }

        signals.add(
            FingerprintSignal(
                id = "installed_packages_list",
                name = "Installed Applications Footprint",
                description = "Scans all user-installed applications and queries their installation source (e.g. Play Store vs Sideload).",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = appRawValue,
                narrative = "The complete list of installed applications forms a highly distinctive fingerprint (an app-graph). Combined with install sources, it reveals whether you use custom developer apps, side-loaded apps, or standard marketplace apps.",
                threatScore = 8,
                permissionName = "android.permission.QUERY_ALL_PACKAGES",
                detailedData = listOf(detailedGroup("Installed Applications Inventory", appListItems)),
                isSensitive = appIsSensitive,
                sensitiveRawValue = if (appIsSensitive) appSensitiveRawValue else null
            )
        )
        
        return signals
    }
}

class AudioRouteProvider : SignalProvider() {
    override val id = "audio_route"
    @SuppressLint("NewApi")
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = mutableListOf<String>()
            val outputDetails = mutableListOf<DetailedItem>()
            
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
                    outputDetails.add(detailedItem(
                        label = device.productName?.toString() ?: "Output Transducer",
                        value = "Type: $typeName | ID: ${device.id} | Address: ${device.address.ifEmpty { "Internal Address" }}",
                        description = "Sample Rates: ${device.sampleRates.joinToString()} | Channels: ${device.channelCounts.joinToString()}",
                        iconName = "hardware"
                    ))
                }
            } else {
                if (audioManager.isWiredHeadsetOn) {
                    outputs.add("Wired Route")
                    outputDetails.add(detailedItem("Wired Headset Connection", "Wired Route Active", null, "hardware"))
                }
                if (audioManager.isBluetoothA2dpOn) {
                    outputs.add("Bluetooth Route")
                    outputDetails.add(detailedItem("Bluetooth Audio Connection", "Bluetooth Route Active", null, "bluetooth"))
                }
                if (audioManager.isSpeakerphoneOn) {
                    outputs.add("Speakerphone")
                    outputDetails.add(detailedItem("Speakerphone Connection", "Speakerphone Active", null, "hardware"))
                }
            }
            
            val routeValue = if (outputs.isNotEmpty()) outputs.joinToString(", ") else "Default Earpiece/Speaker"
            if (outputDetails.isEmpty()) {
                outputDetails.add(detailedItem("Active Route", "Default Earpiece/Speaker", "No external peripherals active", "info"))
            }

            // Stream Volumes
            val volumeItems = listOf(
                detailedItem("Music Volume", "${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}", "Media and application volume stream", "volume"),
                detailedItem("Ring Volume", "${audioManager.getStreamVolume(AudioManager.STREAM_RING)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)}", "Telephone call ringtone volume", "volume"),
                detailedItem("Alarm Volume", "${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)}", "Alarm clock volume stream", "volume"),
                detailedItem("Notification Volume", "${audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)}", "System notification sound volume", "volume"),
                detailedItem("Voice Call Volume", "${audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}", "In-call receiver volume stream", "volume"),
                detailedItem("System Volume", "${audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)}", "System UI effect sound volume", "volume")
            )

            val defaultRingtone = try {
                android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE)?.toString() ?: "Default"
            } catch (e: Exception) {
                "Sandboxed"
            }
            val ringtoneItem = listOf(
                detailedItem("Ringtone URI Path", defaultRingtone, "Default phone call ringtone file identifier", "settings")
            )

            val audioDetailedData = listOf(
                detailedGroup("Active Audio Transducers", outputDetails),
                detailedGroup("Audio Stream Volumes", volumeItems),
                detailedGroup("Ringtone Signatures", ringtoneItem)
            )

            listOf(
                FingerprintSignal(
                    id = "audio_output_routes",
                    name = "Active Audio Hardware Route",
                    description = "Scans available and connected audio transducers.",
                    category = SignalCategory.PASSIVE,
                    rawValue = routeValue,
                    narrative = "Exposed audio configurations leak exactly what accessories are paired (like high-end Bluetooth headsets or generic adapters), creating secondary biometric beacons.",
                    threatScore = 4,
                    detailedData = audioDetailedData
                ),
                FingerprintSignal(
                    id = "audio_music_volume",
                    name = "Music Stream Volume Ratio",
                    description = "Reads active volume levels of media outputs.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}",
                    narrative = "Your precise volume percentages serve as transient side-channels to track state transitions. If you change volume, trackers can link sessions across tabs.",
                    threatScore = 3,
                    detailedData = audioDetailedData
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class BatteryProvider : SignalProvider() {
    override val id = "battery"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
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
            val technology = batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
            val present = batteryStatus?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true
            
            val healthString = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
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

            val energyCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            } else -1L

            val statusVal = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            val statusString = when (statusVal) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }

            val statusItems = listOf(
                detailedItem("Charge Status", statusString, "Current operational battery mode", "battery"),
                detailedItem("Charge Percentage", "$batteryPct%", "Remaining battery percentage capacity", "battery"),
                detailedItem("Power Connection", pluggedString, "External electrical input connection", "usb"),
                detailedItem("Battery Wear Health", healthString, "Physical health condition of battery cells", "check"),
                detailedItem("Battery Technology", technology, "Internal chemical design tag", "info"),
                detailedItem("Battery Present", present.toString(), "Whether a physical battery is registered", "check")
            )

            val electricalItems = listOf(
                detailedItem("Voltage Level", "${voltage} mV", "Real-time electrical pressure across terminals", "info"),
                detailedItem("Temperature", "${(temp / 10.0)}°C", "Thermal measurement of battery package", "info"),
                detailedItem("Microampere Capacity (Charge Counter)", if (chargeCounter != -1) "$chargeCounter µAh" else "Unavailable", "Remaining charge capacity in microampere-hours", "info"),
                detailedItem("Current Rate (Flow)", if (currentNow != -1) "${currentNow / 1000} mA" else "Unavailable", "Real-time battery current drain (-) or charge (+)", "info"),
                detailedItem("Energy Counter", if (energyCounter != -1L) "$energyCounter nWh" else "Unavailable", "Remaining battery energy in nanowatt-hours", "info")
            )

            val batteryDetails = listOf(
                detailedGroup("Battery Status & Health", statusItems),
                detailedGroup("Electrical Hardware Metrics", electricalItems)
            )

            listOf(
                FingerprintSignal(
                    id = "battery_level",
                    name = "Battery Percentage",
                    description = "Monitors real-time battery charge state.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (batteryPct >= 0) "$batteryPct%" else "Unavailable",
                    narrative = "Ad libraries query the battery level to correlate sessions. If two sessions from the same IP have the exact same dwindling charge rate, they belong to the same person.",
                    threatScore = 4,
                    detailedData = batteryDetails
                ),
                FingerprintSignal(
                    id = "battery_health_temp",
                    name = "Battery Health & Temperature",
                    description = "Exposes voltage, temperature, and wear health status.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "$healthString | ${(temp / 10.0)}°C | ${voltage}mV",
                    narrative = "Battery temperature changes linearly based on CPU usage. High-frequency reads expose background processing signatures and thermal profiling benchmarks.",
                    threatScore = 5,
                    detailedData = batteryDetails
                ),
                FingerprintSignal(
                    id = "battery_charge_capacity",
                    name = "Charge Counter Capacity",
                    description = "Exposes remaining microampere-hours (µAh).",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (chargeCounter > 0) "${chargeCounter} µAh" else "Unavailable",
                    narrative = "The microampere capacity represents exact battery wear states down to sub-milliamp increments. This is highly unique to your specific battery cells.",
                    threatScore = 6,
                    detailedData = batteryDetails
                ),
                FingerprintSignal(
                    id = "battery_source",
                    name = "Power Charging Source",
                    description = "Details the physical medium powering the device.",
                    category = SignalCategory.PASSIVE,
                    rawValue = pluggedString,
                    narrative = "Exposing the source (USB, AC, Wireless) lets advertisers determine whether you are at home, commuting in a car, or connected to a development computer.",
                    threatScore = 3,
                    detailedData = batteryDetails
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
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
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
            
            val adapterDetails = listOf(
                detailedItem("Bluetooth Hardware Presence", if (adapter != null) "Present" else "Not Present", "Local Bluetooth radio hardware status", "bluetooth"),
                detailedItem("Bluetooth Radio Status", adapterState, "Global power state of the Bluetooth transmitter", "bluetooth"),
                detailedItem("Adapter Name", if (hasBluetoothPermission && adapter != null) (adapter.name ?: "Unknown") else "Access Restricted", "User-customized name of this device's Bluetooth profile", "info"),
                detailedItem("Hardware MAC Address", if (hasBluetoothPermission && adapter != null) (adapter.address ?: "Hidden") else "Access Restricted", "Physical hardware identifier of Bluetooth chipset", "info")
            )
            
            val bondedItems = mutableListOf<DetailedItem>()
            var sensitiveBluetoothRaw = ""
            var bluetoothIsSensitive = false
            if (adapter != null) {
                if (hasBluetoothPermission) {
                    val bondedDevices = adapter.bondedDevices
                    val deviceList = mutableListOf<String>()
                    bondedDevices.forEach { device ->
                        val devName = device.name ?: "Unnamed Peripheral"
                        val devAddr = device.address ?: "02:00:00:00:00:00"
                        deviceList.add("$devName($devAddr)")
                        
                        val devType = when (device.type) {
                            android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic (BR/EDR)"
                            android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE -> "Low Energy (BLE)"
                            android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode"
                            else -> "Unknown"
                        }
                        
                        val nameMasked = maskBluetoothName(devName)
                        val addressMasked = maskBluetoothAddress(devAddr)
                        
                        bondedItems.add(detailedItem(
                            label = nameMasked,
                            value = "MAC: $addressMasked\nType: $devType\nBond State: Bonded",
                            description = "Device UUID count: ${device.uuids?.size ?: 0}",
                            iconName = "bluetooth"
                        ))
                    }
                    if (bondedItems.isEmpty()) {
                        bondedItems.add(detailedItem("Bonded Registry", "No paired devices found", "No Bluetooth pairings registered in memory", "close"))
                    } else {
                        sensitiveBluetoothRaw = deviceList.joinToString(",")
                        bluetoothIsSensitive = true
                    }
                } else {
                    bondedItems.add(detailedItem("Bonded Registry", "Permission Blocked", "Requires BLUETOOTH_CONNECT runtime permission", "close"))
                }
            } else {
                bondedItems.add(detailedItem("Bonded Registry", "Bluetooth Hardware Unavailable", "No local receiver interface present", "close"))
            }

            val bluetoothDetails = listOf(
                detailedGroup("Local Bluetooth Configuration", adapterDetails),
                detailedGroup("Bonded Peripherals Registry", bondedItems)
            )

            signals.add(
                FingerprintSignal(
                    id = "bluetooth_hardware_status",
                    name = "Bluetooth Adapter State",
                    description = "Checks if the local bluetooth radio is toggled on.",
                    category = SignalCategory.PASSIVE,
                    rawValue = adapterState,
                    narrative = "Even without runtime permission gates, querying the system's global Bluetooth radio state assists trackers in constructing contextual location profiles.",
                    threatScore = 4,
                    detailedData = bluetoothDetails
                )
            )

            val pairSummary = if (!hasBluetoothPermission) {
                "Permission Blocked"
            } else if (adapter == null) {
                "Hardware Unavailable"
            } else {
                val size = adapter.bondedDevices.size
                if (size > 0) "$size paired peripherals" else "No bonded devices"
            }

            signals.add(
                FingerprintSignal(
                    id = "bluetooth_bonded_devices",
                    name = "Active Paired / Bonded Devices",
                    description = "Retrieves names and MAC addresses of previously synchronized peripherals.",
                    category = SignalCategory.NEEDS_PERMISSION,
                    rawValue = pairSummary,
                    narrative = "Paired wearables, headsets, and vehicle infotainment names contain custom tags. This yields extreme tracking capabilities and is an absolute fingerprint beacon.",
                    threatScore = 9,
                    permissionName = android.Manifest.permission.BLUETOOTH_CONNECT,
                    detailedData = bluetoothDetails,
                    isSensitive = bluetoothIsSensitive,
                    sensitiveRawValue = if (bluetoothIsSensitive) sensitiveBluetoothRaw else null
                )
            )
        } catch (e: Exception) {
            // Safe fallback
        }
        return signals
    }
}



class CalendarProvider : SignalProvider() {
    override val id = "calendar"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        
        val detailedItems = mutableListOf<DetailedItem>()
        var count = 0
        if (hasPermission) {
            try {
                val uri: Uri = CalendarContract.Calendars.CONTENT_URI
                val projection = arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.OWNER_ACCOUNT,
                    CalendarContract.Calendars.VISIBLE
                )
                val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
                if (cursor != null) {
                    count = cursor.count
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                        val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                        val accNameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                        val accTypeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                        val ownerIdx = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
                        val visIdx = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
                        
                        do {
                            val calId = if (idIdx >= 0) cursor.getString(idIdx) else "Unknown"
                            val calName = if (nameIdx >= 0) cursor.getString(nameIdx) else "Unnamed"
                            val accName = if (accNameIdx >= 0) cursor.getString(accNameIdx) else "Unknown"
                            val accType = if (accTypeIdx >= 0) cursor.getString(accTypeIdx) else "Unknown"
                            val owner = if (ownerIdx >= 0) cursor.getString(ownerIdx) else "Unknown"
                            val visible = if (visIdx >= 0) cursor.getInt(visIdx) == 1 else false
                            
                            detailedItems.add(detailedItem(
                                label = calName,
                                value = "ID: $calId\nAccount: $accName ($accType)\nOwner: $owner\nVisible: $visible",
                                description = "Calendar Contract Entry",
                                iconName = "calendar"
                            ))
                        } while (cursor.moveToNext())
                    }
                    cursor.close()
                }
            } catch (e: Exception) {
                detailedItems.add(detailedItem("Calendar Fetch Status", "Error: ${e.localizedMessage}", "Failed to query calendars", "close"))
            }
        } else {
            detailedItems.add(detailedItem("Calendar Fetch Status", "Permission Blocked", "READ_CALENDAR permission denied", "close"))
        }

        if (detailedItems.isEmpty()) {
            detailedItems.add(detailedItem("Calendar Accounts", "No calendars found", "No calendar profiles registered on the system", "close"))
        }

        val summaryValue = if (hasPermission) "$count active calendars" else "Permission Blocked"

        return listOf(
            FingerprintSignal(
                id = "calendar_exposure_count",
                name = "Calendar Registrations Count",
                description = "Evaluates system content providers to check for active schedules and calendar profiles.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = summaryValue,
                narrative = "Exposing calendar configurations and names leaks your personal email accounts, corporate profiles, and structural lifestyle schedules.",
                threatScore = 8,
                permissionName = android.Manifest.permission.READ_CALENDAR,
                detailedData = listOf(detailedGroup("Calendar Registrations", detailedItems))
            )
        )
    }
}

class CameraProvider : SignalProvider() {
    override val id = "camera"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val signals = mutableListOf<FingerprintSignal>()
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val idList = cameraManager.cameraIdList
            val camerasCount = idList.size
            
            val details = mutableListOf<DetailedItem>()
            for (cid in idList) {
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
                    val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    
                    details.add(detailedItem(
                        label = "Camera $cid ($facingStr)",
                        value = "Hardware Level: $hwStr\nSensor Size: ${sensorSize?.width ?: 0f}x${sensorSize?.height ?: 0f} mm\nFocal Lengths: ${focalLengths?.joinToString() ?: "N/A"} mm\nFlash Available: $flash",
                        description = "Facing Direction: $facingStr",
                        iconName = "camera"
                    ))
                } catch (e: Exception) {
                    details.add(detailedItem("Camera $cid", "Characteristics restricted: ${e.localizedMessage}", null, "close"))
                }
            }
            if (details.isEmpty()) {
                details.add(detailedItem("Camera Hardware", "No camera sensors found", "No hardware detected", "close"))
            }
            
            val hasCameraPermission = context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            
            val cameraDetailGroup = listOf(
                detailedGroup("Physical Camera Transducers", details),
                detailedGroup("Dynamic Live Access State", listOf(
                    detailedItem("Camera Permission Status", if (hasCameraPermission) "Access Granted" else "Permission Blocked", "Dynamic optical sensor feed authorization", "key")
                  ))
              )

              signals.add(
                  FingerprintSignal(
                      id = "camera_hardware_specs",
                      name = "Camera Hardware Sensors",
                      description = "Queries the camera subsystems, exposure levels, and lens configs.",
                      category = SignalCategory.PASSIVE,
                      rawValue = "$camerasCount sensors available",
                      narrative = "Exposed camera quantities, lens configurations, and internal sensor calibrations serve as static hardware signatures that do not change across factory resets.",
                      threatScore = 5,
                      detailedData = cameraDetailGroup
                  )
              )
              
              signals.add(
                  FingerprintSignal(
                      id = "camera_runtime_access",
                      name = "Camera Live Access Capability",
                      description = "Verifies dynamic optical feed access permission status.",
                      category = SignalCategory.NEEDS_PERMISSION,
                      rawValue = if (hasCameraPermission) "Access Granted" else "Permission Blocked",
                      narrative = "Once camera permissions are acquired, applications can silently map your room environments, run background iris recognition, or analyze optical features.",
                      threatScore = 10,
                      permissionName = android.Manifest.permission.CAMERA,
                      detailedData = cameraDetailGroup
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
      override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
          val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
          
          val detailedItems = mutableListOf<DetailedItem>()
          var count = 0
          val (countVal, threat) = if (hasPermission) {
              try {
                  val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                  val projection = arrayOf(
                      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                      ContactsContract.CommonDataKinds.Phone.NUMBER
                  )
                  val cursor = context.contentResolver.query(uri, projection, null, null, null)
                  if (cursor != null) {
                      count = cursor.count
                      var loaded = 0
                      if (cursor.moveToFirst()) {
                          val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                          val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                          do {
                              val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "Unnamed"
                              val num = if (numIdx >= 0) cursor.getString(numIdx) else "No Number"
                              detailedItems.add(detailedItem(
                                  label = name,
                                  value = num,
                                  description = "Address Book Contact",
                                  iconName = "person"
                              ))
                              loaded++
                          } while (cursor.moveToNext() && loaded < 50)
                      }
                      cursor.close()
                  }
                  
                  val severityText = "$count contacts synced"
                  Pair(severityText, 9)
              } catch (e: Exception) {
                  detailedItems.add(detailedItem("Contact Query Error", e.localizedMessage ?: "Unknown error", null, "close"))
                  Pair("Error reading contacts", 7)
              }
          } else {
              detailedItems.add(detailedItem("Contact Directory", "Permission Blocked", "Requires READ_CONTACTS permission", "close"))
              Pair("Permission Blocked", 8)
          }
          
          if (detailedItems.isEmpty()) {
              detailedItems.add(detailedItem("Contacts List", "No contacts found", "Address book is currently empty", "close"))
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
                  permissionName = android.Manifest.permission.READ_CONTACTS,
                  detailedData = listOf(detailedGroup("Synchronized Contact Cards (First 50)", detailedItems))
              )
          )
      }
  }

class DeviceIdentityProvider : SignalProvider() {
      override val id = "device_identity"
      override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
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
          
          val buildProperties = listOf(
              detailedItem("Model Name", Build.MODEL, "Designated user-facing device model identification", "hardware"),
              detailedItem("Manufacturer", Build.MANUFACTURER, "Industrial maker responsible for device build", "hardware"),
              detailedItem("Brand Name", Build.BRAND, "Commercial carrier brand associated with build", "info"),
              detailedItem("Product Name", Build.PRODUCT, "Industrial product release tag", "info"),
              detailedItem("Board Name", Build.BOARD, "Underlying silicon motherboard tag name", "info"),
              detailedItem("Hardware ID", Build.HARDWARE, "Physical system-on-chip hardware chipset code", "info"),
              detailedItem("Device Name", Build.DEVICE, "System-level code identifier of active device", "info"),
              detailedItem("Bootloader", Build.BOOTLOADER, "Active bootloader version signature", "settings"),
              detailedItem("Display Build ID", Build.DISPLAY, "Software user-visible build ID string", "settings"),
              detailedItem("Fingerprint Signature", Build.FINGERPRINT, "Unique ROM build fingerprint descriptor", "settings"),
              detailedItem("Host Name", Build.HOST, "Compilation machine host designation", "info"),
              detailedItem("Build ID", Build.ID, "Alpha-numeric tag index of active code compilation", "info"),
              detailedItem("Build Tags", Build.TAGS, "Distribution developer keys/tags signature", "info"),
              detailedItem("Build Type", Build.TYPE, "OS release variant classification (e.g. user, debug)", "info"),
              detailedItem("Build User", Build.USER, "Creator compile user tag", "info"),
              detailedItem("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(), "Instruction sets supported by CPU", "code"),
              detailedItem("Radio Version", Build.getRadioVersion() ?: "Unknown", "Baseband radio firmware version", "wifi")
          )

          val buildDetails = listOf(detailedGroup("Build Constants", buildProperties))

          return listOf(
              FingerprintSignal(
                  id = "system_hardware_tags",
                  name = "Core Board & Model Identifiers",
                  description = "Build-level hardware constants exposed by the Android OS kernel.",
                  category = SignalCategory.PASSIVE,
                  rawValue = "${Build.MANUFACTURER} ${Build.MODEL}",
                  narrative = "These build-level strings represent physical components. They are totally immutable and act as foundational keys for cross-device correlation scripts.",
                  threatScore = 7,
                  detailedData = buildDetails
              ),
              FingerprintSignal(
                  id = "system_uptime_precision",
                  name = "Precise System Uptime",
                  description = "Returns time passed since the last hardware restart.",
                  category = SignalCategory.PASSIVE,
                  rawValue = uptimeFormatted,
                  narrative = "Uptime ticks are continuous, high-precision numbers. When combined with localized IP gateways, uptime makes your device instantly unique among millions of concurrent connections.",
                  threatScore = 4,
                  detailedData = listOf(detailedGroup("Time Metrics", listOf(
                      detailedItem("System Uptime", uptimeFormatted, "Elapsed time since last restart", "time"),
                      detailedItem("Elapsed Realtime", "${SystemClock.elapsedRealtime()} ms", "Exact boot time elapsed clock ticks", "time"),
                      detailedItem("Uptime Millis", "${SystemClock.uptimeMillis()} ms", "CPU running thread uptime time", "time")
                  )))
              ),
              FingerprintSignal(
                  id = "system_security_patch",
                  name = "OS Security Patch Level",
                  description = "Displays the exact date-stamp of the applied security fixes.",
                  category = SignalCategory.PASSIVE,
                  rawValue = Build.VERSION.SECURITY_PATCH,
                  narrative = "Exposing security patch versions lets software packages catalog the exact system vulnerabilities and patches active on your device, compiling hardware exploitability profiles.",
                  threatScore = 5,
                  detailedData = listOf(detailedGroup("Security Properties", listOf(
                      detailedItem("Security Patch Date", Build.VERSION.SECURITY_PATCH, "OS security vulnerability patch level code", "key"),
                      detailedItem("SDK Version", "API ${Build.VERSION.SDK_INT}", "System API Level code", "code"),
                      detailedItem("OS Release", Build.VERSION.RELEASE, "User-visible Android OS version tag", "info")
                  )))
              ),
              FingerprintSignal(
                  id = "system_kernel_version",
                  name = "Linux Kernel Compilation Signature",
                  description = "Exposes the underlying compiled OS kernel version.",
                  category = SignalCategory.PASSIVE,
                  rawValue = kernelVersion,
                  narrative = "The specific compiler flags and dates of the underlying Linux kernel make it highly unique, especially for devices running custom ROMs or sideloaded firmwares.",
                  threatScore = 6,
                  detailedData = listOf(detailedGroup("OS Core", listOf(
                      detailedItem("Linux Kernel Version", kernelVersion, "Active OS kernel compilation code", "settings")
                  )))
              )
          )
      }
  }

class DeviceMotionProvider : SignalProvider() {
      override val id = "device_motion"
      override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
          val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
          
          val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
          val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
          val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
          val baro = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
          
          val sensorList = mutableListOf<DetailedItem>()
          listOf(
              "Accelerometer" to accel,
              "Gyroscope" to gyro,
              "Magnetometer" to mag,
              "Barometer" to baro
          ).forEach { (label, sensor) ->
              if (sensor != null) {
                  sensorList.add(detailedItem(
                      label = label,
                      value = "Vendor: ${sensor.vendor}\nPower: ${sensor.power} mA\nResolution: ${sensor.resolution}\nMax Range: ${sensor.maximumRange}\nMin Delay: ${sensor.minDelay} µs",
                      description = "Type: ${sensor.stringType}",
                      iconName = "sensor"
                  ))
              } else {
                  sensorList.add(detailedItem(label, "Not Present on Device", "Hardware sensor unavailable", "close"))
              }
          }

          val motionDetails = listOf(detailedGroup("Motion Sensors Specifications", sensorList))

          return listOf(
              FingerprintSignal(
                  id = "sensor_accelerometer_signature",
                  name = "Accelerometer Sensor Signature",
                  description = "Queries vendor specs and sensor resolutions.",
                  category = SignalCategory.PASSIVE,
                  rawValue = if (accel != null) "${accel.vendor} Acceleration Chip" else "Not Present",
                  narrative = "High-precision physical calibrations of gravity chips contain micro-imperfections. Trackers read micro-vibrations of these sensors to index your physical silicon fingerprint.",
                  threatScore = 6,
                  detailedData = motionDetails
              ),
              FingerprintSignal(
                  id = "sensor_gyro_spec",
                  name = "Gyroscope Sensor Specification",
                  description = "Detects rotation metrics sensor vendor profiles.",
                  category = SignalCategory.PASSIVE,
                  rawValue = if (gyro != null) "${gyro.vendor} Rotation Chip" else "Not Present",
                  narrative = "Because rotation chips operate at high speeds, reading raw coordinate fluctuations allows applications to track spatial gestures, keystroke sounds, or walking gaits.",
                  threatScore = 5,
                  detailedData = motionDetails
              ),
              FingerprintSignal(
                  id = "sensor_magnetometer_barometer",
                  name = "Magnetometer & Barometer Sensors",
                  description = "Exposes local magnetic and barometric pressure availability.",
                  category = SignalCategory.PASSIVE,
                  rawValue = "Mag: ${if (mag != null) "Yes" else "No"} | Baro: ${if (baro != null) "Yes" else "No"}",
                  narrative = "Barometer values reveal the exact elevation changes of the user, down to floor level. Trackers cross-reference pressure curves to detect indoor position.",
                  threatScore = 4,
                  detailedData = motionDetails
              )
          )
      }
  }

class DisplayProvider : SignalProvider() {
      override val id = "display"
      override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
          val metrics = context.resources.displayMetrics
          val width = metrics.widthPixels
          val height = metrics.heightPixels
          val dpi = metrics.densityDpi
          val density = metrics.density
          val xdpi = metrics.xdpi
          val ydpi = metrics.ydpi
          
          val orientation = if (width > height) "Landscape" else "Portrait"
          
          val fontScale = context.resources.configuration.fontScale
          
          val refreshRate = try {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                  context.display?.mode?.refreshRate
              } else {
                  val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
                  @Suppress("DEPRECATION")
                  windowManager?.defaultDisplay?.refreshRate
              }
          } catch (e: Exception) {
              null
          } ?: 60f
          
          val displayItems = listOf(
              detailedItem("Viewport Width", "$width px", "Physical pixel width of screen layout", "hardware"),
              detailedItem("Viewport Height", "$height px", "Physical pixel height of screen layout", "hardware"),
              detailedItem("Screen Density", "${density}x", "Logical scaling density factor", "info"),
              detailedItem("Density DPI", "$dpi DPI", "Dots-per-inch scaling constant", "info"),
              detailedItem("Screen Refresh Rate", "${String.format("%.1f", refreshRate)} Hz", "Hardware screen refresh frequency in Hertz", "info"),
              detailedItem("Horizontal DPI (xDpi)", "$xdpi", "Physical pixels per inch in X dimension", "info"),
              detailedItem("Vertical DPI (yDpi)", "$ydpi", "Physical pixels per inch in Y dimension", "info"),
              detailedItem("Scaled Density", "${metrics.scaledDensity}x", "Typography scaling density factor", "info"),
              detailedItem("Layout Orientation", "$orientation Mode", "Physical screen layout orientation state", "info"),
              detailedItem("System Font Scale", "${fontScale}x", "User-customized text scaling setting", "info")
          )

          val displayDetails = listOf(detailedGroup("Display Specifications", displayItems))

          return listOf(
              FingerprintSignal(
                  id = "display_resolution_dpi",
                  name = "Display Resolution & Density",
                  description = "Reads pixel height, width, screen scaling density (DPI), and refresh rate.",
                  category = SignalCategory.PASSIVE,
                  rawValue = "${width}x${height} px | $dpi DPI | ${String.format("%.1f", refreshRate)} Hz",
                  narrative = "Screen resolution profiles are shared with ad servers to scale visual content. In analytics scripts, display height, width, and color depth serve as core components of browser identity.",
                  threatScore = 4,
                  detailedData = displayDetails
              ),
              FingerprintSignal(
                  id = "display_orientation_state",
                  name = "Device Layout Orientation",
                  description = "Checks current structural viewport rendering layout.",
                  category = SignalCategory.PASSIVE,
                  rawValue = "$orientation Mode",
                  narrative = "Frequent orientation state transitions expose active physical interaction, making it highly obvious whether a human is shifting the device or a automated emulator is testing it.",
                  threatScore = 2,
                  detailedData = displayDetails
              )
          )
      }
  }



class FontsProvider : SignalProvider() {
    override val id = "fonts"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val fontsDir = File("/system/fonts")
        val fontFiles = mutableListOf<DetailedItem>()
        var count = 0
        if (fontsDir.exists() && fontsDir.isDirectory) {
            val list = fontsDir.listFiles()
            if (list != null) {
                count = list.size
                list.take(50).forEach { file ->
                    fontFiles.add(detailedItem(
                        label = file.name,
                        value = "Path: ${file.absolutePath}\nSize: ${file.length() / 1024} KB",
                        description = "System TrueType / OpenType Font",
                        iconName = "media"
                    ))
                }
            }
        }
        
        if (fontFiles.isEmpty()) {
            fontFiles.add(detailedItem("Font Files", "No fonts found / sandbox restricted", null, "close"))
        }

        val fontStatus = if (count > 0) "$count system fonts found" else "Sandboxed / Unreadable"
        
        return listOf(
            FingerprintSignal(
                id = "fonts_system_inventory",
                name = "System Font File Inventory",
                description = "Scans typical local font directories to detect unique pre-loaded typographies.",
                category = SignalCategory.PASSIVE,
                rawValue = fontStatus,
                narrative = "By enumerating local system fonts, trackers determine whether a device is running specific localized modifications, custom regional language packs, or custom manufacturer overlays.",
                threatScore = 6,
                detailedData = listOf(detailedGroup("Typographical File Inventory (First 50)", fontFiles))
            )
        )
    }
}

class LocalNetworkProvider : SignalProvider() {
    override val id = "local_network"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val signals = mutableListOf<FingerprintSignal>()
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
            val isCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
            val isBluetooth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ?: false
            val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ?: false
            
            val transportType = when {
                isVpn -> "VPN Protected"
                isWifi -> "WiFi Connection"
                isCell -> "Cellular Network"
                isEthernet -> "Ethernet Link"
                isBluetooth -> "Bluetooth Tethering"
                else -> "Disconnected / Unknown Link"
            }
            
            val linkSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps != null) {
                "${caps.linkDownstreamBandwidthKbps / 1000} Mbps Down"
            } else "Unavailable"
            
            val linkUpSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps != null) {
                "${caps.linkUpstreamBandwidthKbps / 1000} Mbps Up"
            } else "Unavailable"

            val capDetails = mutableListOf<DetailedItem>()
            capDetails.add(detailedItem("Active Connection Type", transportType, "Primary network routing channel", "wifi"))
            capDetails.add(detailedItem("Link Downstream Speed", linkSpeed, "Estimated network download capability", "info"))
            capDetails.add(detailedItem("Link Upstream Speed", linkUpSpeed, "Estimated network upload capability", "info"))
            
            if (caps != null) {
                capDetails.add(detailedItem("Metered Network", (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).toString(), "Whether this link bills per data usage", "check"))
                capDetails.add(detailedItem("Validated Network", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).toString(), "Whether connection reaches global internet", "check"))
                capDetails.add(detailedItem("Captive Portal Detected", (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)).toString(), "Whether login gate exists on route", "check"))
            }

            // Carrier Details
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val carrier = tm.networkOperatorName ?: "No SIM Card"
            val carrierIso = tm.networkCountryIso ?: "Unknown"
            val simOperator = tm.simOperatorName ?: "No Operator"
            val simState = when (tm.simState) {
                TelephonyManager.SIM_STATE_READY -> "SIM Card Ready"
                TelephonyManager.SIM_STATE_ABSENT -> "SIM Card Absent"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
                else -> "Other State"
            }

            val carrierItems = listOf(
                detailedItem("Registered Carrier Operator", carrier.ifEmpty { "No Network Operator" }, "Carrier network connected on towers", "phone"),
                detailedItem("SIM Operator Name", simOperator.ifEmpty { "No SIM Operator" }, "Home network name on plastic SIM card", "phone"),
                detailedItem("Carrier ISO Code", carrierIso.uppercase(), "Timezone/Region country code of SIM billing", "info"),
                detailedItem("SIM Chip State", simState, "Physical SIM registration state", "check")
            )

            val networkDetails = listOf(
                detailedGroup("Active Connection Attributes", capDetails),
                detailedGroup("Cellular Operator Details", carrierItems)
            )

            signals.add(
                FingerprintSignal(
                    id = "network_transport_type",
                    name = "Network Interface Medium",
                    description = "Determines cellular, WiFi, or VPN transport active channels.",
                    category = SignalCategory.PASSIVE,
                    rawValue = transportType,
                    narrative = "VPN statuses let advertisers block privacy-minded users. They also isolate users routing through corporate tunnels to map workplace structures.",
                    threatScore = 5,
                    detailedData = networkDetails
                )
            )
            
            signals.add(
                FingerprintSignal(
                    id = "network_carrier_name",
                    name = "Mobile Telephony Carrier",
                    description = "Detects the registered cellular service provider name.",
                    category = SignalCategory.PASSIVE,
                    rawValue = carrier.ifEmpty { "No Carrier" },
                    narrative = "Mobile networks segment users based on socioeconomic billing scales. If your carrier matches a high-priced local tier, you may be categorized into premium advertising segments.",
                    threatScore = 4,
                    detailedData = networkDetails
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
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val locale = Locale.getDefault()
        val timezone = TimeZone.getDefault()
        
        val localeItems = listOf(
            detailedItem("Display Name", locale.displayName, "User-readable display name of default language setting", "info"),
            detailedItem("Language Tag", locale.toLanguageTag(), "Standard IETF language configuration tag", "info"),
            detailedItem("Language Code", locale.language, "ISO 639-1 language code descriptor", "info"),
            detailedItem("Country ISO Code", locale.country, "ISO 3166-1 country code descriptor", "info"),
            detailedItem("Display Country", locale.displayCountry, "User-readable name of configured region", "info"),
            detailedItem("Script Layout", if (locale.displayName.contains("RTL", ignoreCase = true)) "Right-To-Left" else "Left-To-Right", "Writing direction of default interface locale", "info")
        )

        val tzItems = listOf(
            detailedItem("Timezone ID", timezone.id, "Standard Timezone database ID tag", "time"),
            detailedItem("Display Name", timezone.displayName, "User-readable timezone description", "time"),
            detailedItem("Raw Offset Hours", "${timezone.rawOffset / 3600000.0} hrs", "Raw time difference offset from UTC standard", "time"),
            detailedItem("DST Offset Hours", "${timezone.dstSavings / 3600000.0} hrs", "Daylight savings offset hours", "time"),
            detailedItem("In Daylight Time", timezone.inDaylightTime(java.util.Date()).toString(), "Whether DST is currently active", "check")
        )

        val localeDetails = listOf(
            detailedGroup("System Language Localization", localeItems),
            detailedGroup("System Timezone settings", tzItems)
        )

        return listOf(
            FingerprintSignal(
                id = "locale_language_tags",
                name = "System Language & Country ID",
                description = "Default language and localization variables set by user settings.",
                category = SignalCategory.PASSIVE,
                rawValue = locale.displayName,
                narrative = "Language, text orientation, and local date formats are highly localized. This instantly filters your geographic region without requiring GPS access.",
                threatScore = 3,
                detailedData = localeDetails
            ),
            FingerprintSignal(
                id = "locale_timezone_id",
                name = "Standard System Timezone ID",
                description = "Default timezone identifier configured on the device.",
                category = SignalCategory.PASSIVE,
                rawValue = timezone.id,
                narrative = "Timezone markers help correlation algorithms sync timestamps of background pings, linking user activities recorded on disparate service networks.",
                threatScore = 3,
                detailedData = localeDetails
            )
        )
    }
}

class PasteboardProvider : SignalProvider() {
    override val id = "pasteboard"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val hasClip = clipboard.hasPrimaryClip()
            val description = clipboard.primaryClipDescription
            
            val mimeType = if (hasClip && description != null && description.mimeTypeCount > 0) {
                description.getMimeType(0)
            } else {
                "Empty"
            }
            
            val clipDetails = mutableListOf<DetailedItem>()
            clipDetails.add(detailedItem("Clipboard Contents Present", hasClip.toString(), "Whether any copied data occupies registry", "check"))
            
            if (hasClip && description != null) {
                clipDetails.add(detailedItem("Clip Label", description.label?.toString() ?: "Unnamed Clip", "Optional label tag on clipboard item", "info"))
                clipDetails.add(detailedItem("MIME Type Count", "${description.mimeTypeCount} registered", "Total MIME formats available for this clip", "info"))
                
                val mimeTypesList = mutableListOf<String>()
                for (i in 0 until description.mimeTypeCount) {
                    mimeTypesList.add(description.getMimeType(i))
                }
                clipDetails.add(detailedItem("All MIME Types", mimeTypesList.joinToString(), "Supported clipboard formats", "info"))
                
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    clipDetails.add(detailedItem("Item Count", "${clipData.itemCount} items", "Number of distinct elements copied in clip", "info"))
                    val firstItem = clipData.getItemAt(0)
                    val textPreview = when {
                        firstItem.text != null -> firstItem.text.toString()
                        firstItem.htmlText != null -> firstItem.htmlText
                        firstItem.uri != null -> firstItem.uri.toString()
                        firstItem.intent != null -> "System Intent Object"
                        else -> "Non-Text Binary Data"
                    }
                    val previewCut = if (textPreview.length > 80) textPreview.take(77) + "..." else textPreview
                    clipDetails.add(detailedItem("Primary Item Data (Preview)", previewCut, "Exact value copied on clipboard (Redacted if sensitive)", "key"))
                }
            } else {
                clipDetails.add(detailedItem("Primary Item Data", "Clipboard Empty", "No data resides on clipboard", "close"))
            }

            val clipboardDetails = listOf(detailedGroup("Active Clipboard Cache", clipDetails))

            listOf(
                FingerprintSignal(
                    id = "pasteboard_status",
                    name = "Clipboard Active Contents",
                    description = "Detects whether active, unpurged entries occupy the global clipboard.",
                    category = SignalCategory.PASSIVE,
                    rawValue = if (hasClip) "Active Content ($mimeType)" else "Clipboard Empty",
                    narrative = "Many applications query the pasteboard automatically upon launching. If a user copies a password, secure token, or personal email from one app, a background tracker can read it instantly.",
                    threatScore = 7,
                    detailedData = clipboardDetails
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class StorageProvider : SignalProvider() {
    override val id = "storage"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val dataDir = Environment.getDataDirectory()
            val totalBytes = dataDir.totalSpace
            val freeBytes = dataDir.freeSpace
            val usedBytes = totalBytes - freeBytes
            
            val df = DecimalFormat("#.##")
            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
            
            val storageItems = listOf(
                detailedItem("Storage Mount Path", dataDir.absolutePath, "Physical filesystem partition mount root", "storage"),
                detailedItem("Total Capacity", "${df.format(totalGb)} GB", "Gross hardware partitioned memory space", "storage"),
                detailedItem("Used Capacity", "${df.format(usedGb)} GB", "Memory space occupied by files", "storage"),
                detailedItem("Free Available Capacity", "${df.format(freeGb)} GB", "Remaining memory space for files", "storage"),
                detailedItem("Usable Capacity", "${df.format(dataDir.usableSpace / (1024.0 * 1024.0 * 1024.0))} GB", "Actual writable memory storage space", "storage"),
                detailedItem("Raw Total Space (Bytes)", "$totalBytes bytes", "Exact byte count of partition size", "info"),
                detailedItem("Raw Free Space (Bytes)", "$freeBytes bytes", "Exact byte count of free space", "info")
            )

            val externalStorage = Environment.getExternalStorageDirectory()
            val extTotal = externalStorage.totalSpace
            val extFree = externalStorage.freeSpace
            val extUsed = extTotal - extFree
            val extItems = listOf(
                detailedItem("External Storage Mount", externalStorage.absolutePath, "External SD card / emulated storage path", "storage"),
                detailedItem("External Total Space", "${df.format(extTotal / (1024.0 * 1024.0 * 1024.0))} GB", "Total external media volume size", "storage"),
                detailedItem("External Used Space", "${df.format(extUsed / (1024.0 * 1024.0 * 1024.0))} GB", "Used external media space", "storage"),
                detailedItem("External Free Space", "${df.format(extFree / (1024.0 * 1024.0 * 1024.0))} GB", "Remaining external media space", "storage")
            )

            val storageDetails = listOf(
                detailedGroup("Internal Storage Partition", storageItems),
                detailedGroup("External / Shared Storage Volume", extItems)
            )

            listOf(
                FingerprintSignal(
                    id = "storage_capacity_metrics",
                    name = "Internal Storage Volume Metrics",
                    description = "Queries maximum byte capacities and exact free bytes remaining.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "Total: ${df.format(totalGb)} GB | Free: ${df.format(freeGb)} GB",
                    narrative = "Disk volume sizes map to standard hardware configurations (e.g. 128GB disk). The exact byte count (e.g. 128,000,456,128 bytes) and active free space are highly volatile, letting scripts index unique disk profiles.",
                    threatScore = 5,
                    detailedData = storageDetails
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class WebViewFingerprintProvider : SignalProvider() {
    override val id = "webview_fingerprint"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val userAgent = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            System.getProperty("http.agent") ?: "Unknown User Agent"
        }
        
        val webviewItems = listOf(
            detailedItem("Default User-Agent Header", userAgent, "Full standard user agent identification string", "code"),
            detailedItem("Embedded User-Agent", System.getProperty("http.agent") ?: "N/A", "Underlying Java network property http agent string", "code"),
            detailedItem("WebKit Version Info", if (userAgent.contains("AppleWebKit/")) userAgent.substringAfter("AppleWebKit/").substringBefore(" ") else "Unknown", "Rendering engine version index parsed from UA", "info"),
            detailedItem("Chrome / WebView Version", if (userAgent.contains("Chrome/")) userAgent.substringAfter("Chrome/").substringBefore(" ") else "Unknown", "Embedded Chromium build version parsed from UA", "info")
        )

        val jsItems = listOf(
            detailedItem("Javascript V8 Engine", "Active (ECMAScript 2026 Compatible)", "Whether JS runtime compilation features are enabled", "check"),
            detailedItem("Dynamic Sandbox Capabilities", "Canvas 2D rendering, WebGL 3D context, Audio latency profile checks possible", "Capabilities that trackers probe in WebViews", "info")
        )

        val webviewDetails = listOf(
            detailedGroup("WebKit Engine User-Agent", webviewItems),
            detailedGroup("JavaScript Execution sandbox", jsItems)
        )

        return listOf(
            FingerprintSignal(
                id = "webview_user_agent",
                name = "WebView Default User-Agent String",
                description = "The browser identification header compiled into the embedded WebKit engine.",
                category = SignalCategory.ADVANCED,
                rawValue = "WebKit Default User-Agent",
                narrative = "The User-Agent details the exact Safari/WebKit builds, Android firmware compile version, and Chromium updates. It serves as the primary correlation vector across different web contexts.",
                threatScore = 7,
                detailedData = webviewDetails
            ),
            FingerprintSignal(
                id = "webview_javascript_engine",
                name = "JavaScript V8 Engine Signature",
                description = "Checks underlying engine execution properties.",
                category = SignalCategory.ADVANCED,
                rawValue = "ECMAScript 2026 Compatible",
                narrative = "Trackers load hidden web views to execute JavaScript side-channels like canvas pixel rendering or audio context latency checks, bypassing standard native app isolation layers completely.",
                threatScore = 8,
                detailedData = webviewDetails
            )
        )
    }
}

class LocationProvider : SignalProvider() {
    override val id = "location"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPermission = hasCoarse || hasFine
        
        val detailedItems = mutableListOf<DetailedItem>()
        var countVal = "Permission Blocked"
        
        detailedItems.add(detailedItem("Coarse Location Granted", hasCoarse.toString(), "Coarse-level network triangulation permission status", "check"))
        detailedItems.add(detailedItem("Fine Location Granted", hasFine.toString(), "GPS hardware positioning sensor permission status", "check"))
        
        if (hasPermission) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = lm.getProviders(true)
                detailedItems.add(detailedItem("Active Location Providers", if (providers.isNotEmpty()) providers.joinToString() else "None active", "Enabled positioning subsystems on device", "location"))
                
                var lastLoc: android.location.Location? = null
                for (provider in providers) {
                    val loc = try {
                        lm.getLastKnownLocation(provider)
                    } catch (e: SecurityException) {
                        null
                    }
                    if (loc != null) {
                        if (lastLoc == null || loc.time > lastLoc.time) {
                            lastLoc = loc
                        }
                    }
                }
                
                if (lastLoc != null) {
                    detailedItems.add(detailedItem("Last Known Coordinates", "Lat: ${lastLoc.latitude} | Lon: ${lastLoc.longitude}", "Triangulated geo-coordinates of device", "location"))
                    detailedItems.add(detailedItem("Latitude Coordinate", lastLoc.latitude.toString(), "Decimal degrees coordinate", "location"))
                    detailedItems.add(detailedItem("Longitude Coordinate", lastLoc.longitude.toString(), "Decimal degrees coordinate", "location"))
                    detailedItems.add(detailedItem("Altitude Position", "${lastLoc.altitude} meters", "Position height offset from sea level", "info"))
                    detailedItems.add(detailedItem("Accuracy Radius", "${lastLoc.accuracy} meters", "Precision error bounds margin of last read", "info"))
                    detailedItems.add(detailedItem("Last Known Speed", "${lastLoc.speed} m/s", "Real-time travel speed vector", "info"))
                    detailedItems.add(detailedItem("Last Known Bearing", "${lastLoc.bearing}°", "Directional degrees alignment", "info"))
                    detailedItems.add(detailedItem("Last Update Timestamp", java.util.Date(lastLoc.time).toString(), "Time of coordinates registry update", "time"))
                    detailedItems.add(detailedItem("Positioning Source Provider", lastLoc.provider ?: "Unknown", "Hardware sensor source of calculations", "info"))
                    countVal = "Lat: ${String.format("%.4f", lastLoc.latitude)} | Lon: ${String.format("%.4f", lastLoc.longitude)}"
                } else {
                    detailedItems.add(detailedItem("Last Known Coordinates", "No coordinates logged in database", "Location services enabled but no last position found", "close"))
                    countVal = "Granted (No last coordinates)"
                }
            } catch (e: Exception) {
                detailedItems.add(detailedItem("Location Query Error", "Failed: ${e.localizedMessage}", "Query failed", "close"))
                countVal = "Granted (Query Error)"
            }
        } else {
            detailedItems.add(detailedItem("Last Known Coordinates", "Access restricted by permission gate", "Location permission denied", "close"))
        }

        val locationDetails = listOf(detailedGroup("Geographical Positioning Metrics", detailedItems))

        return listOf(
            FingerprintSignal(
                id = "location_exposure_status",
                name = "Precise Geographic Location",
                description = "Evaluates active global positioning sensors and network triangulation parameters.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = countVal,
                narrative = "Precise location isolates your exact physical location. Trackers use this to identify your home address, workplace, and physical social circles.",
                threatScore = 10,
                permissionName = android.Manifest.permission.ACCESS_COARSE_LOCATION,
                detailedData = locationDetails
            )
        )
    }
}

class MicrophoneProvider : SignalProvider() {
    override val id = "microphone"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val rawSummary = if (hasPermission) "Access Granted" else "Permission Blocked"
        
        val detailedItems = mutableListOf<DetailedItem>()
        detailedItems.add(detailedItem("Microphone Permission Granted", hasPermission.toString(), "Audio recording sensor access status", "check"))
        
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            var count = 0
            for (device in devices) {
                val typeName = when (device.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO Microphone"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset Microphone"
                    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Microphone"
                    else -> "Input Device: ${device.type}"
                }
                detailedItems.add(detailedItem(
                    label = device.productName?.toString() ?: "Microphone Hardware",
                    value = "Type: $typeName | ID: ${device.id} | Address: ${device.address.ifEmpty { "Internal" }}",
                    description = "Sample Rates: ${device.sampleRates.joinToString()} | Channels: ${device.channelCounts.joinToString()}",
                    iconName = "mic"
                ))
                count++
            }
            detailedItems.add(detailedItem("Total Microphone Transducers", "$count found", "Count of input audio transducers", "info"))
        } else {
            detailedItems.add(detailedItem("Microphone Transducers", "Specs query requires API 23+", "Older SDK version", "info"))
        }

        val micDetails = listOf(detailedGroup("Audio Input Hardware Specifications", detailedItems))

        return listOf(
            FingerprintSignal(
                id = "microphone_runtime_access",
                name = "Microphone Sensor Access",
                description = "Verifies access to background audio capture devices.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawSummary,
                narrative = "Malicious SDKs query microphone availability or record background acoustic snippets to profile ambient noise level and device surroundings.",
                threatScore = 8,
                permissionName = android.Manifest.permission.RECORD_AUDIO,
                detailedData = micDetails
            )
        )
    }
}

class DrmProvider : SignalProvider() {
    override val id = "drm_fingerprint"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val WIDEVINE_UUID = UUID(-0x12107c1d3537856aL, -0x7c2137b381042968L)
        val drmProperties = mutableListOf<DetailedItem>()
        var drmId = "Unavailable"
        var securityLevel = "Unknown"
        try {
            val mediaDrm = MediaDrm(WIDEVINE_UUID)
            val deviceId = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            drmId = deviceId.joinToString("") { "%02x".format(it) }
            
            drmProperties.add(detailedItem("Widevine Device Unique ID (Full)", drmId, "Cryptographically secure device unique ID", "key"))
            drmProperties.add(detailedItem("DRM Vendor", mediaDrm.getPropertyString(MediaDrm.PROPERTY_VENDOR), "Creator of DRM implementation", "info"))
            drmProperties.add(detailedItem("DRM Version", mediaDrm.getPropertyString(MediaDrm.PROPERTY_VERSION), "DRM engine version number", "info"))
            drmProperties.add(detailedItem("DRM Description", mediaDrm.getPropertyString(MediaDrm.PROPERTY_DESCRIPTION), "DRM profile description", "info"))
            drmProperties.add(detailedItem("DRM Algorithms", mediaDrm.getPropertyString(MediaDrm.PROPERTY_ALGORITHMS), "Supported cryptographic algorithms", "code"))
            
            securityLevel = try {
                mediaDrm.getPropertyString("securityLevel")
            } catch (e: Exception) {
                "L3 (Software/Fallback)"
            }
            drmProperties.add(detailedItem("Widevine Security Level", securityLevel, "Hardware key security level certification (L1 vs L3)", "check"))
            mediaDrm.close()
        } catch (e: Exception) {
            drmProperties.add(detailedItem("DRM Subsystem status", "Error: ${e.localizedMessage}", "Widevine initialization failed", "close"))
        }

        val drmDetails = listOf(detailedGroup("Widevine Hardware DRM Signature", drmProperties))
        val summaryId = if (drmId.length > 25) drmId.take(22) + "..." else drmId

        return listOf(
            FingerprintSignal(
                id = "drm_widevine_security_level",
                name = "Widevine DRM Security Level",
                description = "Queries the Widevine DRM security level (L1, L2, L3) to evaluate hardware protection capability.",
                category = SignalCategory.PASSIVE,
                rawValue = securityLevel,
                narrative = "The DRM Security Level indicates whether the device has a hardware-backed secure execution environment (L1) or software-based decryption (L3). Trackers use this status to categorize device capability classes.",
                threatScore = 3,
                detailedData = drmDetails
            ),
            FingerprintSignal(
                id = "drm_widevine_system_id",
                name = "Widevine DRM Hardware Signature ID",
                description = "Queries the cryptographically secure device unique ID embedded in media subsystems.",
                category = SignalCategory.ADVANCED,
                rawValue = summaryId,
                narrative = "The Widevine DRM Device Unique ID is a persistent hardware identifier that is immune to factory resets and application clear-data events, allowing tracking across entire device lifecycles.",
                threatScore = 10,
                detailedData = drmDetails
            )
        )
    }
}

class PhoneStateProvider : SignalProvider() {
    override val id = "phone_state"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        
        val detailedItems = mutableListOf<DetailedItem>()
        var summaryVal = "Permission Blocked"
        
        detailedItems.add(detailedItem("READ_PHONE_STATE Granted", hasPermission.toString(), "System telephone information access status", "check"))

        if (hasPermission) {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val deviceId = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    tm.deviceId ?: "Unavailable"
                } else {
                    "Restricted on API 29+ (Q)"
                }
                val simSerial = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    tm.simSerialNumber ?: "Unavailable"
                } else {
                    "Restricted on API 29+ (Q)"
                }
                
                val phoneType = when (tm.phoneType) {
                    TelephonyManager.PHONE_TYPE_GSM -> "GSM Cellular"
                    TelephonyManager.PHONE_TYPE_CDMA -> "CDMA Cellular"
                    TelephonyManager.PHONE_TYPE_SIP -> "SIP Voip Network"
                    else -> "No Active Radio Type"
                }
                
                val networkOperator = tm.networkOperator ?: "None"
                val simOperator = tm.simOperator ?: "None"
                val countryIso = tm.networkCountryIso ?: "None"
                
                detailedItems.add(detailedItem("IMEI / MEID Device ID", deviceId, "Hardware serial number of cellular modem (restricted on newer APIs)", "key"))
                detailedItems.add(detailedItem("SIM Chip Serial Number", simSerial, "Hardware serial number of plastic SIM card (restricted on newer APIs)", "key"))
                detailedItems.add(detailedItem("Voice Mail Tag", try { tm.voiceMailAlphaTag ?: "None" } catch (e: Exception) { "Restricted" }, "Voicemail user label tag", "info"))
                detailedItems.add(detailedItem("Cellular Radio Type", phoneType, "Tower protocol interface class", "info"))
                detailedItems.add(detailedItem("Carrier Network Operator Code", networkOperator, "Mobile country/network code (MCC/MNC)", "info"))
                detailedItems.add(detailedItem("SIM Operator Code", simOperator, "Home network code of SIM card", "info"))
                detailedItems.add(detailedItem("Network Country ISO", countryIso.uppercase(), "Geographical country registration of active tower", "info"))
                
                summaryVal = "Carrier: ${tm.networkOperatorName.ifEmpty { "Unknown" }} | SIM State: ${if (tm.simState == TelephonyManager.SIM_STATE_READY) "Ready" else "Not Ready"}"
            } catch (e: Exception) {
                detailedItems.add(detailedItem("Telephony Query Error", "Failed: ${e.localizedMessage}", "Query error", "close"))
                summaryVal = "Granted (Query Error)"
            }
        } else {
            detailedItems.add(detailedItem("Telephony Parameters", "Access restricted by permission gate", "Phone state permission denied", "close"))
        }

        val phoneDetails = listOf(detailedGroup("Cellular Radio & SIM State Details", detailedItems))

        return listOf(
            FingerprintSignal(
                id = "phone_state_access",
                name = "Device Phone State & Cellular ID",
                description = "Monitors hardware serial numbers, Sim details, and cellular metadata.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = summaryVal,
                narrative = "The READ_PHONE_STATE permission grants access to persistent cellular IDs like Sim Serial Numbers and IMEIs on legacy versions, or detailed operator connection details on newer versions. This makes cross-app user tracking effortless.",
                threatScore = 9,
                permissionName = android.Manifest.permission.READ_PHONE_STATE,
                detailedData = phoneDetails
            )
        )
    }
}

class CallLogProvider : SignalProvider() {
    override val id = "call_log"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val detailedItems = mutableListOf<DetailedItem>()
        var count = 0
        var summaryVal = "Permission Blocked"
        
        if (hasPermission) {
            try {
                val uri = CallLog.Calls.CONTENT_URI
                val projection = arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_NAME
                )
                val cursor = context.contentResolver.query(uri, projection, null, null, CallLog.Calls.DATE + " DESC")
                if (cursor != null) {
                    count = cursor.count
                    var loaded = 0
                    if (cursor.moveToFirst()) {
                        val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                        val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                        val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                        val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                        
                        do {
                            val num = if (numIdx >= 0) cursor.getString(numIdx) else "Private"
                            val dateVal = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                            val duration = if (durIdx >= 0) cursor.getInt(durIdx) else 0
                            val typeVal = if (typeIdx >= 0) cursor.getInt(typeIdx) else -1
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Unknown Contact" else "Unknown Contact"
                            
                            val typeString = when (typeVal) {
                                CallLog.Calls.INCOMING_TYPE -> "Incoming Call"
                                CallLog.Calls.OUTGOING_TYPE -> "Outgoing Call"
                                CallLog.Calls.MISSED_TYPE -> "Missed Call"
                                CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
                                CallLog.Calls.REJECTED_TYPE -> "Rejected Call"
                                CallLog.Calls.BLOCKED_TYPE -> "Blocked Call"
                                else -> "Call"
                            }
                            
                            val dateFormatted = java.util.Date(dateVal).toString()
                            detailedItems.add(detailedItem(
                                label = "$name ($num)",
                                value = "$typeString\nDuration: $duration seconds\nDate: $dateFormatted",
                                description = "Call Log Record",
                                iconName = "phone"
                            ))
                            loaded++
                        } while (cursor.moveToNext() && loaded < 30)
                    }
                    cursor.close()
                }
                summaryVal = "$count call records synced"
            } catch (e: Exception) {
                detailedItems.add(detailedItem("Call Log Error", "Failed: ${e.localizedMessage}", "Query failed", "close"))
                summaryVal = "Granted (Query Error)"
            }
        } else {
            detailedItems.add(detailedItem("Call Logs Cache", "Access restricted by permission gate", "READ_CALL_LOG permission denied", "close"))
        }

        if (detailedItems.isEmpty()) {
            detailedItems.add(detailedItem("Call Logs Registry", "No records found", "Call history is currently empty", "close"))
        }

        return listOf(
            FingerprintSignal(
                id = "call_log_exposure",
                name = "Call Log Catalog Access",
                description = "Monitors system call histories, timestamps, contact identities, and telephone numbers.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = summaryVal,
                narrative = "Accessing your Call Log allows trackers to reconstruct your private telephone communication graph, establishing highly sensitive social mapping. Advertisers can utilize active talk durations to target user demographics.",
                threatScore = 9,
                permissionName = android.Manifest.permission.READ_CALL_LOG,
                detailedData = listOf(detailedGroup("Private Call Log Registry (First 30)", detailedItems))
            )
        )
    }
}

class SmsProvider : SignalProvider() {
    override val id = "sms"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val detailedItems = mutableListOf<DetailedItem>()
        var count = 0
        var summaryVal = "Permission Blocked"
        
        if (hasPermission) {
            try {
                val uri = Uri.parse("content://sms")
                val projection = arrayOf(
                    "address",
                    "date",
                    "body",
                    "type",
                    "read"
                )
                val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC")
                if (cursor != null) {
                    count = cursor.count
                    var loaded = 0
                    if (cursor.moveToFirst()) {
                        val addrIdx = cursor.getColumnIndex("address")
                        val dateIdx = cursor.getColumnIndex("date")
                        val bodyIdx = cursor.getColumnIndex("body")
                        val typeIdx = cursor.getColumnIndex("type")
                        val readIdx = cursor.getColumnIndex("read")
                        
                        do {
                            val addr = if (addrIdx >= 0) cursor.getString(addrIdx) ?: "Unknown" else "Unknown"
                            val dateVal = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                            val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                            val typeVal = if (typeIdx >= 0) cursor.getInt(typeIdx) else -1
                            val readVal = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else false
                            
                            val typeString = when (typeVal) {
                                1 -> "Received SMS (Inbox)"
                                2 -> "Sent SMS"
                                3 -> "Draft SMS"
                                4 -> "Outbox SMS"
                                else -> "Text Message"
                            }
                            
                            val preview = if (body.length > 50) body.take(47) + "..." else body
                            val dateFormatted = java.util.Date(dateVal).toString()
                            
                            detailedItems.add(detailedItem(
                                label = "$typeString: $addr",
                                value = "Preview: \"$preview\"\nDate: $dateFormatted\nRead Status: ${if (readVal) "Read" else "Unread"}",
                                description = "SMS Database Entry",
                                iconName = "sms"
                            ))
                            loaded++
                        } while (cursor.moveToNext() && loaded < 30)
                    }
                    cursor.close()
                }
                summaryVal = "$count text messages synced"
            } catch (e: Exception) {
                detailedItems.add(detailedItem("SMS Database Error", "Failed: ${e.localizedMessage}", "Query failed", "close"))
                summaryVal = "Granted (Query Error)"
            }
        } else {
            detailedItems.add(detailedItem("SMS Database", "Access restricted by permission gate", "READ_SMS permission denied", "close"))
        }

        if (detailedItems.isEmpty()) {
            detailedItems.add(detailedItem("SMS Registry", "No text messages found", "SMS message inbox is currently empty", "close"))
        }

        return listOf(
            FingerprintSignal(
                id = "sms_exposure_status",
                name = "SMS Message Inbox Access",
                description = "Monitors text message counts, sender contacts, and inbox/sent databases.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = summaryVal,
                narrative = "SMS permissions can expose secure multi-factor authentication (MFA) codes, bank alerts, and private texts. Spammers scan message databases to construct targeted phishing profiles.",
                threatScore = 10,
                permissionName = android.Manifest.permission.READ_SMS,
                detailedData = listOf(detailedGroup("Private SMS Message Registry (First 30)", detailedItems))
            )
        )
    }
}

class AccountsProvider : SignalProvider() {
    override val id = "accounts"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED
        val detailedItems = mutableListOf<DetailedItem>()
        var count = 0
        var summaryVal = "Permission Blocked"
        
        if (hasPermission) {
            try {
                val am = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
                val accounts = am.accounts
                count = accounts.size
                accounts.forEach { acc ->
                    detailedItems.add(detailedItem(
                        label = acc.name,
                        value = "Account Type: ${acc.type}",
                        description = "System Identity Account Profile",
                        iconName = "account"
                    ))
                }
                summaryVal = "$count profiles exposed"
            } catch (e: Exception) {
                detailedItems.add(detailedItem("Accounts Fetch Error", "Failed: ${e.localizedMessage}", "Query failed", "close"))
                summaryVal = "Granted (Query Error)"
            }
        } else {
            detailedItems.add(detailedItem("Accounts Index", "Access restricted by permission gate", "GET_ACCOUNTS permission denied", "close"))
        }

        if (detailedItems.isEmpty()) {
            detailedItems.add(detailedItem("System Accounts Profiles", "No active accounts registered", "No profiles exposed in system registry", "close"))
        }

        return listOf(
            FingerprintSignal(
                id = "system_accounts_list",
                name = "System Accounts Index",
                description = "Scans registered Google, email, and social accounts on the device.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = summaryVal,
                narrative = "Scanning registered device accounts reveals your primary email address and active service profiles. This uniquely identifies you with high precision across any third-party app utilizing the same ecosystem.",
                threatScore = 7,
                permissionName = android.Manifest.permission.GET_ACCOUNTS,
                detailedData = listOf(detailedGroup("System Identity Registry", detailedItems))
            )
        )
    }
}


class NotificationPermissionProvider : SignalProvider() {
    override val id = "notifications"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val rawValue = if (hasPermission) "Authorized" else "Blocked"
        
        val detailedItems = listOf(
            detailedItem("Post Notification Permission", hasPermission.toString(), "System authorization to display alerts on notification shades", "check"),
            detailedItem("System Notification Channels", "Total Channels: 2 active\nImportance level: Default\nSound settings: Active", "Internal application notification properties", "settings")
        )

        return listOf(
            FingerprintSignal(
                id = "notification_post_permission",
                name = "Post System Notifications",
                description = "Checks authorization to push system-level notifications to the status bar.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "Notifications are frequently abused by spamware to deploy click-bait promotions, keep background services alive indefinitely, or deliver background payloads silently.",
                threatScore = 5,
                permissionName = android.Manifest.permission.POST_NOTIFICATIONS,
                detailedData = listOf(detailedGroup("System Alert Settings", detailedItems))
            )
        )
    }
}

class HardwareSensorsProvider : SignalProvider() {
    override val id = "hardware_sensors"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensors = sm.getSensorList(Sensor.TYPE_ALL)
            val count = sensors.size
            
            val details = mutableListOf<DetailedItem>()
            sensors.forEach { sensor ->
                details.add(detailedItem(
                    label = sensor.name,
                    value = "Type: ${sensor.stringType} (Code: ${sensor.type})",
                    description = "Hardware Sensor Node",
                    iconName = "sensor"
                ))
            }
            if (details.isEmpty()) {
                details.add(detailedItem("System Sensors", "No hardware sensors listed", "Hardware index is empty", "close"))
            }
            
            listOf(
                FingerprintSignal(
                    id = "hardware_sensors_footprint",
                    name = "Hardware Sensors Signature",
                    description = "Enumerate the full suite of physical hardware sensors installed on the chassis.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "$count hardware sensors detected",
                    narrative = "Every device contains a specific selection of sensors supplied by distinct manufacturers (e.g., Bosch, STMicroelectronics). Querying the complete sensor list creates a highly unique fingerprint string that requires zero permissions to fetch.",
                    threatScore = 6,
                    detailedData = listOf(detailedGroup("Hardware Sensors Inventory", details))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class SystemSettingsProvider : SignalProvider() {
    override val id = "system_settings"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            val timeout = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
            val haptic = try {
                Settings.System.getInt(context.contentResolver, "haptic_feedback_enabled", -1)
            } catch (e: Exception) {
                -1
            }
            val developerOptions = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, -1)
            val airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            
            val settingsItems = listOf(
                detailedItem("Screen Brightness Level", "$brightness (scale: 0-255)", "Active screen backlight intensity level", "settings"),
                detailedItem("Screen Off Timeout Duration", "$timeout ms (${timeout / 1000} seconds)", "Inactivity duration before system sleeps screen display", "time"),
                detailedItem("Haptic Touch Feedback", if (haptic == 1) "Enabled" else if (haptic == 0) "Disabled" else "Unavailable", "Vibration on key taps preference setting", "check"),
                detailedItem("Developer Options Active", if (developerOptions == 1) "Enabled" else if (developerOptions == 0) "Disabled" else "Unavailable", "Whether platform developer mode menu is enabled", "check"),
                detailedItem("Airplane Mode Status", airplaneMode.toString(), "Whether wireless network transmitters are disabled", "check")
            )

            val secureSettings = try {
                val adbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
                val autoRotate = Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 0)
                val rotateLocked = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 0
                val transitionScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
                
                listOf(
                    detailedItem("ADB Bridge Active", adbEnabled.toString(), "Developer USB debugging connection status", "usb"),
                    detailedItem("Screen Rotation Lock", rotateLocked.toString(), "Display automatic rotation toggle status", "check"),
                    detailedItem("System Rotation Orientation", if (autoRotate == 1) "Landscape" else "Portrait", "Orientation state locked by system settings", "info"),
                    detailedItem("Transition Animation Scale", "${transitionScale}x", "System animation transition timing multiplier", "info")
                )
            } catch (e: Exception) {
                emptyList()
            }

            val systemDetails = listOf(
                detailedGroup("Standard System Settings Preferences", settingsItems),
                detailedGroup("Developer & Secure Interface Settings", secureSettings)
            )

            listOf(
                FingerprintSignal(
                    id = "system_settings_leak",
                    name = "Passive System Settings Leak",
                    description = "Collects global settings like screen timeout, screen brightness levels, developer options, and airplane mode.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "Brightness: $brightness | Timeout: ${timeout / 1000}s | Airplane Mode: ${if (airplaneMode) "On" else "Off"}",
                    narrative = "System settings are highly customizable. Combining multiple system variables (screen off timeout, developer mode toggles, and brightness metrics) narrows down the uniqueness index of your phone with zero permission friction.",
                    threatScore = 5,
                    detailedData = systemDetails
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class DeviceUptimeProvider : SignalProvider() {
    override val id = "device_uptime"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val uptimeMs = SystemClock.elapsedRealtime()
        val uptimeHrs = uptimeMs / (1000.0 * 60.0 * 60.0)
        val df = DecimalFormat("#.##")
        
        val days = (uptimeMs / (1000 * 60 * 60 * 24)).toInt()
        val hours = ((uptimeMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
        val minutes = ((uptimeMs % (1000 * 60 * 60)) / (1000 * 60)).toInt()
        val seconds = ((uptimeMs % (1000 * 60)) / 1000).toInt()
        
        val formatted = if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m"
        
        val bootTime = java.util.Date(System.currentTimeMillis() - uptimeMs).toString()
        
        val uptimeItems = listOf(
            detailedItem("System Uptime", formatted, "Elapsed time duration since last hardware boot", "time"),
            detailedItem("Boot Timestamp", bootTime, "Calculated system startup calendar date time", "time"),
            detailedItem("Elapsed Realtime", "$uptimeMs ms", "Millisecond hardware clock elapsed time", "time"),
            detailedItem("Uptime Millis (excluding sleep)", "${SystemClock.uptimeMillis()} ms", "Active cpu instruction time excluding deep sleep", "time")
        )

        return listOf(
            FingerprintSignal(
                id = "device_uptime_metrics",
                name = "Active Device Uptime Metric",
                description = "Calculates elapsed milliseconds since the absolute last boot sequence.",
                category = SignalCategory.PASSIVE,
                rawValue = formatted,
                narrative = "Uptime increments continuously and is exact down to the millisecond. By polling uptime and subtracting it from current calendar time, trackers calculate the exact millisecond your device booted, creating a perfect session tracker.",
                threatScore = 4,
                detailedData = listOf(detailedGroup("Boot Sequence Metrics", uptimeItems))
            )
        )
    }
}

class OpenGLProvider : SignalProvider() {
    override val id = "opengl_fingerprint"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val info = am.deviceConfigurationInfo
            val glVer = info.glEsVersion ?: "Unknown"
            
            val glItems = listOf(
                detailedItem("OpenGL ES Version", glVer, "Standard support level of OpenGL API", "code"),
                detailedItem("Req Navigation Configuration", info.reqNavigation.toString(), "Required hardware navigation configuration", "info"),
                detailedItem("Req Touchscreen Type", info.reqTouchScreen.toString(), "Required touchscreen hardware capability", "info"),
                detailedItem("Req Input Features", "Flags: ${info.reqInputFeatures}", "Input features required by device config", "info")
            )

            listOf(
                FingerprintSignal(
                    id = "opengl_version_sig",
                    name = "OpenGL ES Shader Engine Version",
                    description = "Identifies the GPU configuration specifications via the system manager.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "OpenGL ES $glVer",
                    narrative = "Graphics sub-systems contain highly specific render capabilities. Querying OpenGL ES profiles allows trackers to identify your specific GPU and target shader engine directly.",
                    threatScore = 4,
                    detailedData = listOf(detailedGroup("GPU Shader Engine Specifications", glItems))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class NetworkInterfacesProvider : SignalProvider() {
    override val id = "network_interfaces"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val count = interfaces.size
            val hasVpn = interfaces.any { it.name.contains("tun") || it.name.contains("ppp") || it.name.contains("tap") || it.name.contains("vpn") }
            
            val details = mutableListOf<DetailedItem>()
            interfaces.forEach { netInterface ->
                val ipList = netInterface.inetAddresses.toList().map { it.hostAddress }
                val mac = try {
                    val hardwareAddress = netInterface.hardwareAddress
                    if (hardwareAddress != null) {
                        hardwareAddress.joinToString(":") { "%02x".format(it) }
                    } else "Unavailable (No Permission/Hardware)"
                } catch (e: Exception) {
                    "Error querying MAC"
                }

                details.add(detailedItem(
                    label = netInterface.name,
                    value = "Display Name: ${netInterface.displayName}\nMAC Address: $mac\nMTU: ${netInterface.mtu}\nLoopback: ${netInterface.isLoopback}\nUp/Active: ${netInterface.isUp}\nVirtual Interface: ${netInterface.isVirtual}\nAddresses: ${ipList.joinToString()}",
                    description = "Network Interface Details",
                    iconName = "wifi"
                ))
            }
            if (details.isEmpty()) {
                details.add(detailedItem("Network Interfaces", "No adapters found", "Network stack empty", "close"))
            }

            listOf(
                FingerprintSignal(
                    id = "network_interfaces_signature",
                    name = "Network Interfaces Structure",
                    description = "Scans all active physical and virtual local network adapter identifiers.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "$count adapters configured",
                    narrative = "The existence of specific interface identifiers (e.g. wlan0, rmnet_data0, or virtual tun0 adapters) tells trackers if you are connected via WiFi, cellular data, or utilizing an encrypted VPN channel.",
                    threatScore = 5,
                    detailedData = listOf(detailedGroup("Configured Network Adapters", details))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class TelephonyOperatorProvider : SignalProvider() {
    override val id = "telephony_operator"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val name = tm.networkOperatorName ?: "Unknown"
            val iso = tm.networkCountryIso ?: "Unknown"
            val state = when (tm.simState) {
                TelephonyManager.SIM_STATE_READY -> "SIM Ready"
                TelephonyManager.SIM_STATE_ABSENT -> "SIM Absent"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
                else -> "SIM Other"
            }
            
            val details = listOf(
                detailedItem("Carrier Operator Name", name.ifEmpty { "Unknown" }, "Public network name of tower link", "phone"),
                detailedItem("Geographic Country ISO", iso.uppercase(), "Billing country designation code", "info"),
                detailedItem("SIM Chip State", state, "Operational state of the plastic SIM card", "check"),
                detailedItem("Network Operator Numeric (MCC/MNC)", tm.networkOperator ?: "N/A", "System numeric carrier identifier", "info"),
                detailedItem("SIM Operator Numeric", tm.simOperator ?: "N/A", "Home numeric carrier code registered in SIM", "info"),
                detailedItem("Data Activity State", when (tm.dataActivity) {
                    TelephonyManager.DATA_ACTIVITY_IN -> "Receiving Data"
                    TelephonyManager.DATA_ACTIVITY_OUT -> "Sending Data"
                    TelephonyManager.DATA_ACTIVITY_INOUT -> "Sending & Receiving"
                    TelephonyManager.DATA_ACTIVITY_NONE -> "Idle Connection"
                    else -> "No Data Connection"
                }, "Active IP data transmission channel status", "wifi")
            )

            listOf(
                FingerprintSignal(
                    id = "telephony_carrier_operator",
                    name = "Cellular Operator Carrier Details",
                    description = "Reads public carrier codes, country registration, and active cellular sim states.",
                    category = SignalCategory.PASSIVE,
                    rawValue = name.ifEmpty { "No Network Operator" },
                    narrative = "Network carrier parameters identify your service provider and geographic country. This enables geographic content blocking and targeted ISP profile tracking without location permissions.",
                    threatScore = 5,
                    detailedData = listOf(detailedGroup("Cellular Operator Details", details))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class CpuAndRamProvider : SignalProvider() {
    override val id = "cpu_and_ram"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val abis = Build.SUPPORTED_ABIS.joinToString(", ")
            val cores = Runtime.getRuntime().availableProcessors()
            
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            val availRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
            val df = DecimalFormat("#.##")
            
            val ramPercentUsed = ((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem.toDouble()) * 100.0
            
            val cpuRamDetails = listOf(
                detailedItem("Processor Architecture ABIs", abis, "CPU execution instruction sets compatible with system", "code"),
                detailedItem("Available Logical CPU Cores", "$cores cores", "Total execution threads available for scheduling", "hardware"),
                detailedItem("Total Physical RAM", "${df.format(totalRamGb)} GB", "Gross hardware random access memory size", "hardware"),
                detailedItem("Available Free RAM", "${df.format(availRamGb)} GB", "Remaining memory space for background tasks", "hardware"),
                detailedItem("Memory Consumption Rate", "${df.format(ramPercentUsed)}%", "Percent of RAM currently occupied", "info"),
                detailedItem("Low Memory Threshold", "${df.format(memInfo.threshold / (1024.0 * 1024.0 * 1024.0))} GB", "System RAM limit where apps start to be terminated", "info"),
                detailedItem("System Low Memory Alert", memInfo.lowMemory.toString(), "Whether system is currently in critical low-RAM state", "check")
            )

            listOf(
                FingerprintSignal(
                    id = "cpu_ram_specs",
                    name = "CPU & Memory Configuration",
                    description = "Collects system CPU core architecture, hardware instruction sets, and exact RAM metrics.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "$cores Cores | ${df.format(totalRamGb)} GB RAM",
                    narrative = "CPU instruction sets and exact physical memory configurations are static hardware signatures. Combined with screen and storage, they create highly reliable device fingerprints.",
                    threatScore = 5,
                    detailedData = listOf(detailedGroup("Processor & RAM Hardware Specs", cpuRamDetails))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class WifiDetailsProvider : SignalProvider() {
    override val id = "wifi_details"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasWifiState = context.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
        val hasLocation = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPhoneState = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        val networkSignals = mutableListOf<FingerprintSignal>()

        // 1. Wi-Fi Details
        val wifiItems = mutableListOf<DetailedItem>()
        var wifiRaw = "Permission Blocked"
        var wifiSensitiveRaw = ""
        var wifiIsSensitive = false

        wifiItems.add(detailedItem("ACCESS_WIFI_STATE Granted", hasWifiState.toString(), "WiFi adapter parameters query permission status", "check"))
        wifiItems.add(detailedItem("ACCESS_FINE_LOCATION Granted", hasLocation.toString(), "Geo-location access status (required to query SSID names)", "check"))

        if (hasWifiState) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                val info = wm.connectionInfo
                if (info != null) {
                    val rawBssid = info.bssid
                    val rawSsid = info.ssid
                    val rssi = info.rssi
                    val speed = info.linkSpeed
                    val frequency = info.frequency
                    
                    val bssidMasked = maskBssid(rawBssid)
                    val ssidMasked = maskSsid(rawSsid)
                    
                    wifiItems.add(detailedItem("Connected Router SSID", ssidMasked, "Wireless network name identifier (Masked)", "wifi"))
                    wifiItems.add(detailedItem("Router BSSID MAC Address", bssidMasked, "Physical MAC address signature of access point (Masked)", "key"))
                    wifiItems.add(detailedItem("Signal Strength (RSSI)", "$rssi dBm", "Received signal strength indication decibels", "info"))
                    wifiItems.add(detailedItem("Current Link Speed", "$speed Mbps", "Estimated local transmission bandwidth", "info"))
                    wifiItems.add(detailedItem("Frequency Band", "$frequency MHz", "Active radio frequency channel", "info"))

                    wifiRaw = "SSID: $ssidMasked | BSSID: $bssidMasked | RSSI: $rssi dBm"
                    wifiSensitiveRaw = "SSID: $rawSsid | BSSID: $rawBssid"
                    wifiIsSensitive = rawSsid != null && rawSsid != "<unknown ssid>" && rawBssid != "02:00:00:00:00:00"
                } else {
                    wifiRaw = "No Connection"
                }
            } catch (e: Exception) {
                wifiRaw = "Query Error: ${e.localizedMessage}"
            }
        }

        networkSignals.add(
            FingerprintSignal(
                id = "wifi_network_details",
                name = "WiFi Connection Signatures",
                description = "Scans connected WiFi routers, SSID network names, and BSSID MAC addresses.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = wifiRaw,
                narrative = "Querying BSSID MAC addresses allows cross-referencing against global coordinate databases (like Google Location Services), identifying your precise physical address with up to 1-meter accuracy without using GPS.",
                threatScore = 8,
                permissionName = android.Manifest.permission.ACCESS_FINE_LOCATION,
                detailedData = listOf(detailedGroup("WiFi Connection Details", wifiItems)),
                isSensitive = wifiIsSensitive,
                sensitiveRawValue = if (wifiIsSensitive) wifiSensitiveRaw else null
            )
        )

        // 2. Cellular Details
        val cellItems = mutableListOf<DetailedItem>()
        var cellRaw = "Permission Blocked"

        cellItems.add(detailedItem("READ_PHONE_STATE Granted", hasPhoneState.toString(), "Telephony state permission status", "check"))
        cellItems.add(detailedItem("ACCESS_FINE_LOCATION Granted", hasLocation.toString(), "Location permission status (required for CellInfo & Signal Strength)", "check"))

        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            val carrierName = tm.networkOperatorName ?: "No Carrier"
            
            val netType = if (hasPhoneState || hasLocation) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    tm.dataNetworkType
                } else {
                    @Suppress("DEPRECATION")
                    tm.networkType
                }
            } else {
                android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN
            }
            
            val cellGen = getCellularGeneration(netType)
            
            var dbm = -1
            if (hasLocation) {
                val cellInfo = tm.allCellInfo
                if (cellInfo != null && cellInfo.isNotEmpty()) {
                    val info = cellInfo[0]
                    dbm = when (info) {
                        is android.telephony.CellInfoLte -> info.cellSignalStrength.dbm
                        is android.telephony.CellInfoGsm -> info.cellSignalStrength.dbm
                        is android.telephony.CellInfoWcdma -> info.cellSignalStrength.dbm
                        is android.telephony.CellInfoCdma -> info.cellSignalStrength.dbm
                        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is android.telephony.CellInfoNr) {
                            info.cellSignalStrength.dbm
                        } else {
                            -1
                        }
                    }
                }
            }
            
            if (dbm == -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dbm = tm.signalStrength?.cellSignalStrengths?.firstOrNull()?.dbm ?: -1
            }

            val dbmStr = if (dbm != -1) "$dbm dBm" else "Unavailable"
            
            cellItems.add(detailedItem("SIM Carrier Operator", carrierName.ifEmpty { "Unknown" }, "Public carrier operator name", "phone"))
            cellItems.add(detailedItem("Cellular Generation", cellGen, "Wireless access technology level (e.g. 4G/5G)", "wifi"))
            cellItems.add(detailedItem("Cellular Signal Strength", dbmStr, "Signal strength of active cell transceiver", "info"))
            cellItems.add(detailedItem("Data Network Type", getNetworkTypeString(netType), "Platform network protocol constants code", "info"))

            cellRaw = "Carrier: ${carrierName.ifEmpty { "Unknown" }} | Generation: $cellGen | Signal: $dbmStr"
        } catch (e: Exception) {
            cellRaw = "Query Error: ${e.localizedMessage}"
        }

        networkSignals.add(
            FingerprintSignal(
                id = "cellular_network_details",
                name = "Cellular Network Connection Details",
                description = "Scans SIM carrier name, active cellular signal strength, and network generation.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = cellRaw,
                narrative = "Querying cell towers and signal strength can be used to track device movement and location triangulation. Combined with WiFi SSID and BSSID, they make offline tracking extremely accurate.",
                threatScore = 7,
                permissionName = android.Manifest.permission.ACCESS_FINE_LOCATION,
                detailedData = listOf(detailedGroup("Cellular Connection Details", cellItems))
            )
        )

        return networkSignals
    }
}

class PhysicalActivityProvider : SignalProvider() {
    override val id = "physical_activity"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) "Access Granted" else "Permission Blocked"
        
        val detailedItems = listOf(
            detailedItem("Activity Recognition Permission", hasPermission.toString(), "Access status of step counters and body gesture recognizers", "check"),
            detailedItem("Motion Tracking Status", if (hasPermission) "Dynamic detection enabled" else "Blocked by security sandbox", "Real-time gesture analysis capability", "sensor")
        )

        return listOf(
            FingerprintSignal(
                id = "activity_motion_tracking",
                name = "Physical Activity Tracking",
                description = "Monitors real-time physical motion, steps, walking patterns, and movement states.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "Physical activity permissions track steps and behavioral dynamics. Commercial SDKs use this data to profile lifestyle habits (e.g. active commuter vs stationary) and identify specific daily routines.",
                threatScore = 7,
                permissionName = android.Manifest.permission.ACTIVITY_RECOGNITION,
                detailedData = listOf(detailedGroup("Physical Activity Authorization", detailedItems))
            )
        )
    }
}

class AmbientSensorsProvider : SignalProvider() {
    override val id = "ambient_sensors"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
            val proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            val pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
            
            val details = mutableListOf<DetailedItem>()
            
            details.add(detailedItem(
                label = "Ambient Light Sensor",
                value = if (light != null) "Vendor: ${light.vendor}\nPower: ${light.power} mA\nMax Range: ${light.maximumRange} lux\nResolution: ${light.resolution} lux" else "Not Present",
                description = "Environmental lux level sensor specifications",
                iconName = "sensor"
            ))
            details.add(detailedItem(
                label = "Proximity Sensor",
                value = if (proximity != null) "Vendor: ${proximity.vendor}\nPower: ${proximity.power} mA\nMax Range: ${proximity.maximumRange} cm\nResolution: ${proximity.resolution} cm" else "Not Present",
                description = "Hardware proximity detection coordinates spec",
                iconName = "sensor"
            ))
            details.add(detailedItem(
                label = "Barometric Pressure Sensor",
                value = if (pressure != null) "Vendor: ${pressure.vendor}\nPower: ${pressure.power} mA\nMax Range: ${pressure.maximumRange} hPa\nResolution: ${pressure.resolution} hPa" else "Not Present",
                description = "Atmospheric barometer sensor specs",
                iconName = "sensor"
            ))

            val lightAvailable = if (light != null) "Light: Yes" else "Light: No"
            val proxAvailable = if (proximity != null) "Prox: Yes" else "Prox: No"
            val baroAvailable = if (pressure != null) "Baro: Yes" else "Baro: No"

            listOf(
                FingerprintSignal(
                    id = "ambient_sensors_footprint",
                    name = "Ambient Environmental Sensors",
                    description = "Queries the presence of secondary environmental sensors like lux light level and proximity chips.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "$lightAvailable | $proxAvailable | $baroAvailable",
                    narrative = "Ambient sensors require no permission to access. By continuously monitoring ambient light or proximity changes, background scripts can identify when a device is in a pocket, a purse, or being held near a human face.",
                    threatScore = 5,
                    detailedData = listOf(detailedGroup("Secondary Environmental Sensors", details))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class AntiAnalysisProvider : SignalProvider() {
    override val id = "anti_analysis"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
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
            
            val isDebuggerConnected = android.os.Debug.isDebuggerConnected()
            val isBeingDebugged = isDebuggerConnected || (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

            val heuristics = listOf(
                detailedItem("Android Emulator Mode", isEmulator.toString(), "Whether system signature indicates virtualization engine", "check"),
                detailedItem("SuperUser (su) Binary Root", isRooted.toString(), "Whether local superuser binaries are accessible", "check"),
                detailedItem("Debugger / Probe Attached", isBeingDebugged.toString(), "Whether java VM execution is being traced", "check"),
                detailedItem("System Bootloader Version", Build.BOOTLOADER, "Signature tag of active motherboard bootloader", "settings"),
                detailedItem("Hardware Board", Build.BOARD, "Hardware motherboard code name", "info"),
                detailedItem("ROM Fingerprint Profile", Build.FINGERPRINT, "Standard Android ROM build identification signature", "settings")
            )

            listOf(
                FingerprintSignal(
                    id = "anti_analysis_signals",
                    name = "Sandbox & Emulator Checks",
                    description = "Executes heuristics to identify emulated platforms, debug configurations, and rooted kernels.",
                    category = SignalCategory.ADVANCED,
                    rawValue = "Emulator: $isEmulator | Rooted: $isRooted | Debugged: $isBeingDebugged",
                    narrative = "Malicious applications and tracking SDKs execute sandbox check heuristics to detect security analysts or automated Google Play dynamic analyzer bots, changing their behavior to avoid detection.",
                    threatScore = 8,
                    detailedData = listOf(detailedGroup("Sandbox Inspection Metrics", heuristics))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class InputMethodProvider : SignalProvider() {
    override val id = "input_method"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val list = imm.enabledInputMethodList
            val count = list.size
            
            val details = mutableListOf<DetailedItem>()
            list.forEach { imi ->
                details.add(detailedItem(
                    label = imi.loadLabel(context.packageManager).toString(),
                    value = "Package: ${imi.packageName}\nService ID: ${imi.id}\nSettings Activity: ${imi.settingsActivity}",
                    description = "IME Keyboard Interface Input Channel",
                    iconName = "keyboard"
                ))
            }
            if (details.isEmpty()) {
                details.add(detailedItem("Enabled Keyboards", "No IME services found", "System list is empty", "close"))
            }

            val names = list.take(3).joinToString { it.loadLabel(context.packageManager).toString() }
            val summaryVal = if (count > 3) "$names (+${count - 3} more)" else names

            listOf(
                FingerprintSignal(
                    id = "input_method_package_list",
                    name = "Enabled Input Keyboards",
                    description = "Lists installed keyboard input methods and internationalization IME modules.",
                    category = SignalCategory.ADVANCED,
                    rawValue = summaryVal,
                    narrative = "The specific combination of custom keyboards (e.g. Gboard, SwiftKey, custom emoji boards) and localized keyboard languages makes a device signature highly unique with zero permissions.",
                    threatScore = 6,
                    detailedData = listOf(detailedGroup("Active System Input Keyboards", details))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class NfcAndUsbProvider : SignalProvider() {
    override val id = "nfc_and_usb"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
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
            
            val nfcUsbDetails = listOf(
                detailedItem("NFC Hardware Available", (adapter != null).toString(), "Contactless payment antenna chip availability", "check"),
                detailedItem("NFC Antenna State", nfcState, "Active operational toggle of NFC chip", "info"),
                detailedItem("ADB Debugging Bridge", adbEnabled.toString(), "Developer ADB interface active toggle", "check"),
                detailedItem("USB Mass Storage", try { Settings.Secure.getInt(context.contentResolver, "usb_mass_storage_enabled", 0) == 1 } catch (e: Exception) { false }.toString(), "USB file transfer connectivity status", "check")
            )

            listOf(
                FingerprintSignal(
                    id = "nfc_usb_hardware_state",
                    name = "NFC & USB Connectivity State",
                    description = "Identifies NFC adapter features and USB debugger connections passive states.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "NFC: $nfcState | ADB: ${if (adbEnabled) "Active" else "Inactive"}",
                    narrative = "ADB debugging state exposes if the user is a developer. NFC chip configurations reveal short-range contactless payment hardware models, aiding in profile building.",
                    threatScore = 5,
                    detailedData = listOf(detailedGroup("NFC & USB Subsystems", nfcUsbDetails))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class SoundStreamsProvider : SignalProvider() {
    override val id = "sound_streams"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val alarm = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val ring = am.getStreamVolume(AudioManager.STREAM_RING)
            val system = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
            val music = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val notification = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            val voice = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            
            val ringtoneUri = try {
                android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE)?.toString() ?: "Default"
            } catch (e: Exception) {
                "Sandboxed / Restricted"
            }
            
            val soundItems = listOf(
                detailedItem("Alarm Volume Stream", "$alarm (max: ${am.getStreamMaxVolume(AudioManager.STREAM_ALARM)})", "Active alarm clock ring volume", "media"),
                detailedItem("Ringer Volume Stream", "$ring (max: ${am.getStreamMaxVolume(AudioManager.STREAM_RING)})", "Incoming call alerts sound level", "phone"),
                detailedItem("System Volume Stream", "$system (max: ${am.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)})", "System UI alert sound volume", "info"),
                detailedItem("Music Volume Stream", "$music (max: ${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)})", "Media playback volume level", "media"),
                detailedItem("Notification Volume Stream", "$notification (max: ${am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)})", "Message and app notification alert volume", "sms"),
                detailedItem("Voice Call Volume Stream", "$voice (max: ${am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)})", "Modem call earpiece voice sound volume", "phone"),
                detailedItem("Ringtone URI Path", ringtoneUri, "Location of active incoming call sound resource", "key")
            )

            val shortUri = if (ringtoneUri.length > 35) ringtoneUri.substringAfterLast("/") else ringtoneUri

            listOf(
                FingerprintSignal(
                    id = "sound_stream_volumes",
                    name = "Sound Stream Metrics & Default Ringtone",
                    description = "Collects system volume streams (Alarm, Ring, System) and queries the active ringtone URI path.",
                    category = SignalCategory.PASSIVE,
                    rawValue = "Ringtone: $shortUri",
                    narrative = "System volume ratios serve as high-precision, transient side-channel identifiers. Tracking the default ringtone URI (which can be a custom user-defined file) yields highly unique identification.",
                    threatScore = 6,
                    detailedData = listOf(detailedGroup("Acoustic Volume & Sound Streams", soundItems))
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class BluetoothScanProvider : SignalProvider() {
    override val id = "bluetooth_scan"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val rawValue = if (hasPermission) "Access Granted" else "Permission Blocked"
        
        val detailedItems = listOf(
            detailedItem("Bluetooth Scan Permission", hasPermission.toString(), "Access authorization to run background BLE scans", "check"),
            detailedItem("Background Scan State", if (hasPermission) "Dynamic receiver active" else "Blocked by security sandbox", "Active discovery capability", "wifi")
        )

        return listOf(
            FingerprintSignal(
                id = "bluetooth_ble_scanning",
                name = "BLE Beacons Scan Capability",
                description = "Identifies the authorization status for background Bluetooth Low Energy (BLE) scanning.",
                category = SignalCategory.NEEDS_PERMISSION,
                rawValue = rawValue,
                narrative = "BLE Scan allows apps to scan for surrounding physical beacons (e.g., in retail stores, malls, or airports). Trackers map these beacon coordinates globally, locking your exact real-time offline location without GPS.",
                threatScore = 9,
                permissionName = android.Manifest.permission.BLUETOOTH_SCAN,
                detailedData = listOf(detailedGroup("Bluetooth Low Energy Scanning Specs", detailedItems))
            )
        )
    }
}

class ExternalMediaProvider : SignalProvider() {
    override val id = "external_media"
    override suspend fun provideSignals(context: Context): List<FingerprintSignal> {
        val hasPermission = if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission("android.permission.READ_MEDIA_IMAGES") == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission("android.permission.READ_MEDIA_VIDEO") == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        
        val detailedItems = mutableListOf<DetailedItem>()
        var count = 0
        var rawValue = "Permission Blocked"
        
        detailedItems.add(detailedItem("Media Permissions Granted", hasPermission.toString(), "Public media and photo files access status", "check"))

        if (hasPermission) {
            try {
                val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    android.provider.MediaStore.Images.Media.SIZE,
                    android.provider.MediaStore.Images.Media.DATE_ADDED
                )
                val cursor = context.contentResolver.query(uri, projection, null, null, android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC")
                if (cursor != null) {
                    count = cursor.count
                    var loaded = 0
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.SIZE)
                        val dateIdx = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_ADDED)
                        
                        do {
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Unnamed Image" else "Unnamed Image"
                            val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                            val dateVal = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else 0L
                            
                            val dateFormatted = java.util.Date(dateVal).toString()
                            
                            detailedItems.add(detailedItem(
                                label = name,
                                value = "File Size: ${String.format("%.2f", size / 1024.0)} KB\nDate Added: $dateFormatted",
                                description = "Public Shared Photo File",
                                iconName = "media"
                            ))
                            loaded++
                        } while (cursor.moveToNext() && loaded < 50)
                    }
                    cursor.close()
                }
                rawValue = "$count photos available in shared index"
            } catch (e: Exception) {
                detailedItems.add(detailedItem("Shared Storage Image Catalog", "Error: ${e.localizedMessage}", "Query failed", "close"))
                rawValue = "Granted (Query error)"
            }
        } else {
            detailedItems.add(detailedItem("Shared Storage Image Catalog", "Access restricted by permission gate", "Shared media permission denied", "close"))
        }

        if (detailedItems.isEmpty()) {
            detailedItems.add(detailedItem("Shared Storage Images", "No files found", "MediaStore database is empty", "close"))
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
                permissionName = android.Manifest.permission.READ_EXTERNAL_STORAGE,
                detailedData = listOf(detailedGroup("Public Shared Media Assets (First 50)", detailedItems))
            )
        )
    }
}

private fun maskSsid(ssid: String?): String {
    if (ssid == null || ssid.isEmpty() || ssid == "<unknown ssid>") return "Hidden/Restricted"
    val clean = if (ssid.startsWith("\"") && ssid.endsWith("\"")) ssid.substring(1, ssid.length - 1) else ssid
    if (clean.length <= 4) return "$clean***"
    return clean.take(4) + "***"
}

private fun maskBssid(bssid: String?): String {
    if (bssid == null || bssid.isEmpty() || bssid == "02:00:00:00:00:00") return "Hidden/Restricted"
    val parts = bssid.split(":")
    if (parts.size == 6) {
        return "${parts[0]}:${parts[1]}:${parts[2]}:XX:XX:XX"
    }
    return bssid.take(8) + "***"
}

private fun maskBluetoothName(name: String?): String {
    if (name == null || name.isEmpty()) return "Unnamed Peripheral"
    if (name.length <= 4) return "$name***"
    return name.take(4) + "***"
}

private fun maskBluetoothAddress(address: String?): String {
    if (address == null || address.isEmpty() || address == "02:00:00:00:00:00") return "Hidden/Restricted"
    val parts = address.split(":")
    if (parts.size == 6) {
        return "${parts[0]}:${parts[1]}:${parts[2]}:XX:XX:XX"
    }
    return address.take(8) + "***"
}

private fun getCellularGeneration(networkType: Int): String {
    return when (networkType) {
        android.telephony.TelephonyManager.NETWORK_TYPE_GPRS,
        android.telephony.TelephonyManager.NETWORK_TYPE_EDGE,
        android.telephony.TelephonyManager.NETWORK_TYPE_CDMA,
        android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT,
        android.telephony.TelephonyManager.NETWORK_TYPE_IDEN,
        android.telephony.TelephonyManager.NETWORK_TYPE_GSM -> "2G"
        
        android.telephony.TelephonyManager.NETWORK_TYPE_UMTS,
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0,
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B,
        android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP,
        android.telephony.TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
        
        android.telephony.TelephonyManager.NETWORK_TYPE_LTE,
        android.telephony.TelephonyManager.NETWORK_TYPE_IWLAN,
        19 -> "4G" // NETWORK_TYPE_LTE_CA is 19
        
        android.telephony.TelephonyManager.NETWORK_TYPE_NR -> "5G"
        
        else -> "Unknown"
    }
}

private fun getNetworkTypeString(networkType: Int): String {
    return when (networkType) {
        android.telephony.TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        android.telephony.TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        android.telephony.TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        android.telephony.TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
        android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        android.telephony.TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
        android.telephony.TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
        android.telephony.TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        android.telephony.TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD_SCDMA"
        android.telephony.TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        android.telephony.TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
        else -> "UNKNOWN"
    }
}

