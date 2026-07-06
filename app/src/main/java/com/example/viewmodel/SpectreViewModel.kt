package com.matepazy.spectre.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matepazy.spectre.model.CategoryStore
import com.matepazy.spectre.model.SignalCategory
import com.matepazy.spectre.provider.*
import com.matepazy.spectre.support.AppInferenceEngine
import com.matepazy.spectre.support.PermissionCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpectreViewModel : ViewModel() {

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
