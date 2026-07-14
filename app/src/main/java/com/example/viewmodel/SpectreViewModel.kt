package com.matepazy.spectre.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matepazy.spectre.model.CategoryStore
import com.matepazy.spectre.model.SignalCategory
import com.matepazy.spectre.provider.*
import com.matepazy.spectre.support.AppInferenceEngine
import com.matepazy.spectre.support.PermissionCenter
import com.matepazy.spectre.support.UpdateState
import com.matepazy.spectre.support.VersionUpdater
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpectreViewModel : ViewModel() {

    private val _versionCheckEnabled = MutableStateFlow<Boolean?>(null)
    val versionCheckEnabled: StateFlow<Boolean?> = _versionCheckEnabled.asStateFlow()

    private val _updateChannel = MutableStateFlow("release")
    val updateChannel: StateFlow<String> = _updateChannel.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _uiState = MutableStateFlow(CategoryStore())
    val uiState: StateFlow<CategoryStore> = _uiState.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isBiometricLockEnabled = MutableStateFlow(false)
    val isBiometricLockEnabled: StateFlow<Boolean> = _isBiometricLockEnabled.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(true)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val providers = listOf(
        AccessibilityProvider(),
        AppInfoProvider(),
        InstalledAppsProvider(),
        AudioRouteProvider(),
        BatteryProvider(),
        BluetoothProvider(),
        CalendarProvider(),
        CameraProvider(),
        ContactsProvider(),
        DeviceIdentityProvider(),
        DeviceMotionProvider(),
        DisplayProvider(),
        FontsProvider(),
        LocalNetworkProvider(),
        LocaleProvider(),
        PasteboardProvider(),
        StorageProvider(),
        WebViewFingerprintProvider(),
        LocationProvider(),
        MicrophoneProvider(),
        DrmProvider(),
        PhoneStateProvider(),
        CallLogProvider(),
        SmsProvider(),
        AccountsProvider(),
        BodySensorsProvider(),
        NotificationPermissionProvider(),
        HardwareSensorsProvider(),
        SystemSettingsProvider(),
        DeviceUptimeProvider(),
        OpenGLProvider(),
        NetworkInterfacesProvider(),
        TelephonyOperatorProvider(),
        CpuAndRamProvider(),
        WifiDetailsProvider(),
        PhysicalActivityProvider(),
        AmbientSensorsProvider(),
        AntiAnalysisProvider(),
        InputMethodProvider(),
        NfcAndUsbProvider(),
        SoundStreamsProvider(),
        BluetoothScanProvider(),
        ExternalMediaProvider()
    )

    fun checkOnboardingState(context: Context) {
        val prefs = context.getSharedPreferences("spectre_prefs", Context.MODE_PRIVATE)
        _isOnboardingCompleted.value = prefs.getBoolean("onboarding_completed", false)
    }

    fun completeOnboarding(context: Context) {
        val prefs = context.getSharedPreferences("spectre_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        _isOnboardingCompleted.value = true
    }
    
    fun resetOnboarding(context: Context) {
        val prefs = context.getSharedPreferences("spectre_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", false).apply()
        _isOnboardingCompleted.value = false
    }

    fun checkBiometricState(context: Context) {
        val prefs = context.getSharedPreferences("spectre_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("biometric_lock_enabled", false)
        _isBiometricLockEnabled.value = enabled
        _isAppUnlocked.value = !enabled
    }

    fun setBiometricLockEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("spectre_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_lock_enabled", enabled).apply()
        _isBiometricLockEnabled.value = enabled
        if (!enabled) {
            _isAppUnlocked.value = true
        }
    }

    fun setAppUnlocked(unlocked: Boolean) {
        _isAppUnlocked.value = unlocked
    }

    fun checkVersionPrefs(context: Context) {
        val prefs = context.getSharedPreferences("spectre_prefs", Context.MODE_PRIVATE)
        if (prefs.contains("version_check_enabled")) {
            _versionCheckEnabled.value = prefs.getBoolean("version_check_enabled", false)
        } else {
            _versionCheckEnabled.value = null
        }
        _updateChannel.value = prefs.getString("version_check_channel", "release") ?: "release"
    }

    fun setVersionCheckEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("spectre_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("version_check_enabled", enabled).apply()
        _versionCheckEnabled.value = enabled
        if (enabled) {
            triggerVersionCheck(context, manual = false)
        }
    }

    fun setUpdateChannel(context: Context, channel: String) {
        val prefs = context.getSharedPreferences("spectre_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("version_check_channel", channel).apply()
        _updateChannel.value = channel
        if (_versionCheckEnabled.value == true) {
            triggerVersionCheck(context, manual = false)
        }
    }

    fun triggerVersionCheck(context: Context, manual: Boolean) {
        if (!manual && _versionCheckEnabled.value != true) return
        
        _updateState.value = UpdateState.Checking
        VersionUpdater.checkForUpdates(
            currentVersion = com.matepazy.spectre.BuildConfig.VERSION_NAME,
            channel = _updateChannel.value
        ) { state ->
            _updateState.value = state
        }
    }

    fun startApkDownload(context: Context, downloadUrl: String) {
        _updateState.value = UpdateState.Downloading(0f)
        VersionUpdater.downloadApk(
            context = context,
            downloadUrl = downloadUrl,
            onProgress = { progress ->
                _updateState.value = UpdateState.Downloading(progress)
            },
            onCompleted = { file ->
                _updateState.value = UpdateState.Completed(file)
            },
            onError = { errorMsg ->
                _updateState.value = UpdateState.Error(errorMsg)
            }
        )
    }

    fun installApk(context: Context, file: File) {
        com.matepazy.spectre.support.ApkInstaller.installApk(context, file)
    }

    fun canInstallPackages(context: Context): Boolean {
        return com.matepazy.spectre.support.ApkInstaller.canInstallPackages(context)
    }

    fun requestInstallPermission(context: Context) {
        com.matepazy.spectre.support.ApkInstaller.requestInstallPermission(context)
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    fun selectCategory(category: SignalCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun refreshSignals(context: Context, isInitial: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanning = true) }
            
            // Add a realistic scan delay to give tactile feedback and show visual transition
            if (!isInitial) {
                delay(1200)
            } else {
                delay(300)
            }
            
            val collectedSignals = providers.flatMap { provider ->
                try {
                    provider.provideSignals(context)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            
            val inference = AppInferenceEngine.computeInference(collectedSignals)
            val permissions = PermissionCenter.getPermissionStatusMap(context)
            
            _uiState.update {
                it.copy(
                    isScanning = false,
                    signals = collectedSignals,
                    inferenceResult = inference,
                    permissionStatus = permissions
                )
            }
        }
    }

    fun updatePermissionState(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val collectedSignals = providers.flatMap { provider ->
                try {
                    provider.provideSignals(context)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            val inference = AppInferenceEngine.computeInference(collectedSignals)
            val permissions = PermissionCenter.getPermissionStatusMap(context)
            
            _uiState.update {
                it.copy(
                    signals = collectedSignals,
                    inferenceResult = inference,
                    permissionStatus = permissions
                )
            }
        }
    }
}
