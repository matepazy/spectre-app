package com.matepazy.spectre.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.matepazy.spectre.model.CategoryStore
import com.matepazy.spectre.model.FingerprintSignal
import com.matepazy.spectre.model.SignalCategory
import com.matepazy.spectre.model.DetailedGroup
import com.matepazy.spectre.model.DetailedItem
import android.os.Build
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import com.matepazy.spectre.support.PermissionCenter
import com.matepazy.spectre.support.UpdateState
import com.matepazy.spectre.ui.theme.*
import com.matepazy.spectre.viewmodel.SpectreViewModel
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SpectreLogo(
    modifier: Modifier = Modifier,
    color: Color = SpectrePurple
) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        
        val ghostPath = Path().apply {
            moveTo(12f * scaleX, 2f * scaleY)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(4f * scaleX, 2f * scaleY, 20f * scaleX, 18f * scaleY),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(4f * scaleX, 22f * scaleY)
            lineTo(7f * scaleX, 19f * scaleY)
            lineTo(9.5f * scaleX, 21.5f * scaleY)
            lineTo(12f * scaleX, 19f * scaleY)
            lineTo(14.5f * scaleX, 21.5f * scaleY)
            lineTo(17f * scaleX, 19f * scaleY)
            lineTo(20f * scaleX, 22f * scaleY)
            lineTo(20f * scaleX, 10f * scaleY)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(4f * scaleX, 2f * scaleY, 20f * scaleX, 18f * scaleY),
                startAngleDegrees = 0f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            close()
        }
        
        drawPath(
            path = ghostPath,
            color = color,
            style = Stroke(
                width = 2.3f * scaleX,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        drawCircle(
            color = color,
            radius = 1.15f * scaleX,
            center = Offset(9f * scaleX, 10f * scaleY)
        )
        
        drawCircle(
            color = color,
            radius = 1.15f * scaleX,
            center = Offset(15f * scaleX, 10f * scaleY)
        )
    }
}

fun Context.findActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) {
            return currentContext
        }
        currentContext = (currentContext as ContextWrapper).baseContext
    }
    return null
}

fun authenticateBiometrically(
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context.findActivity()
    if (activity == null) {
        onError("Could not locate host Activity")
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Authentication failed")
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Spectre Lock")
        .setSubtitle("Authenticate using your device security to access the privacy audit registries.")
        .setNegativeButtonText("Cancel")
        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "Biometric prompt error")
    }
}

@Composable
fun LockScreen(onUnlockSuccess: () -> Unit) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val biometricManager = remember { BiometricManager.from(context) }
    val canAuth = remember {
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
    }
    val isBiometricEnrolled = canAuth == BiometricManager.BIOMETRIC_SUCCESS

    LaunchedEffect(Unit) {
        if (isBiometricEnrolled) {
            authenticateBiometrically(
                context = context,
                onSuccess = onUnlockSuccess,
                onError = { err -> errorMessage = err }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDarkBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SpectrePurple.copy(alpha = 0.08f))
                    .border(1.dp, SpectrePurple.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = SpectrePurple,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Spectre Locked",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This application is locked to protect sensitive device logs. Authenticate using device security to proceed.",
                fontSize = 12.sp,
                color = CyberTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    fontSize = 12.sp,
                    color = CyberRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(CyberRed.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isBiometricEnrolled) {
                Button(
                    onClick = {
                        errorMessage = null
                        authenticateBiometrically(
                            context = context,
                            onSuccess = onUnlockSuccess,
                            onError = { err -> errorMessage = err }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometrics", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberDarkBg)
                        .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Biometrics Unavailable",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No biometric sensors are configured or available on this device.",
                        fontSize = 11.sp,
                        color = CyberTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onUnlockSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bypass Authentication", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SpectreAppContainer(viewModel: SpectreViewModel) {
    val context = LocalContext.current
    val isOnboardingDone by viewModel.isOnboardingCompleted.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricLockEnabled.collectAsState()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkOnboardingState(context)
        viewModel.checkBiometricState(context)
        viewModel.checkVersionPrefs(context)
        viewModel.refreshSignals(context, isInitial = true)
        viewModel.triggerVersionCheck(context, manual = false)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isBiometricEnabled && !isAppUnlocked) {
            LockScreen(
                onUnlockSuccess = {
                    viewModel.setAppUnlocked(true)
                }
            )
        } else {
            if (!isOnboardingDone) {
                OnboardingView(
                    onOnboardingComplete = {
                        viewModel.completeOnboarding(context)
                    }
                )
            } else {
                HomeView(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// 1. ONBOARDING VIEW
// -------------------------------------------------------------------------
@Composable
fun OnboardingView(onOnboardingComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // App Header (persistent)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                SpectreLogo(
                    modifier = Modifier.size(36.dp),
                    color = SpectrePurple
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Spectre",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = CyberTextPrimary
                )
            }

            // Center Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    },
                    label = "OnboardingStep"
                ) { currentStep ->
                    when (currentStep) {
                        1 -> OnboardingStepCard(
                            icon = Icons.Default.Radar,
                            title = "Telemetry Analysis",
                            description = "Every mobile device exposes unique configurations, battery behaviors, and screen properties. Trackers compile these data points into a digital signature to identify you. Spectre runs a local analysis to show you exactly what is visible.",
                            tint = CyberGreen
                        )
                        2 -> OnboardingStepCard(
                            icon = Icons.Default.Security,
                            title = "Risk Classification",
                            description = "Spectre organizes signals across three privacy categories:\n\n• Passive: Public data accessible silently without any permissions.\n• Needs Permission: Protected data requiring standard Android permission approval.\n• Advanced: Deep hardware identifiers and unique browser hashes.",
                            tint = SpectrePurple
                        )
                        3 -> OnboardingStepCard(
                            icon = Icons.Default.Shield,
                            title = "100% Offline & Private",
                            description = "Your privacy is our priority. All analysis runs completely on-device, offline, and locally. Absolutely no data is uploaded to any servers. Continue to scan and inspect your device's telemetry footprint.",
                            tint = CyberBlue
                        )
                    }
                }
            }

            // Bottom Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Segmented Step Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp).width(120.dp)
                ) {
                    for (i in 1..3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (step >= i) SpectrePurple else CyberBorder)
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (step > 1) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier
                                .height(48.dp)
                                .weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SpectrePurple
                            ),
                            border = BorderStroke(1.dp, CyberBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Back", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (step < 3) {
                                step++
                            } else {
                                onOnboardingComplete()
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .weight(1f)
                            .testTag("onboarding_next_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpectrePurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (step == 3) "Start Scanning" else "Continue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingStepCard(
    icon: ImageVector,
    title: String,
    description: String,
    tint: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.08f))
                    .border(1.dp, tint.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (icon == Icons.Default.Radar) {
                    SpectreLogo(
                        modifier = Modifier.size(36.dp),
                        color = tint
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = CyberTextPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = description,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = CyberTextSecondary,
                lineHeight = 22.sp
            )
        }
    }
}

// -------------------------------------------------------------------------
// 2. HOME VIEW (MAIN DASHBOARD)
// -------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    uiState: CategoryStore,
    viewModel: SpectreViewModel
) {
    val context = LocalContext.current
    val versionCheckEnabled by viewModel.versionCheckEnabled.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showAboutView by remember { mutableStateOf(false) }
    var showUpdateDetailsDialog by remember { mutableStateOf(false) }
    var activeUpdateDetails by remember { mutableStateOf<UpdateState.UpdateAvailable?>(null) }
    var activeDetailedSignal by remember { mutableStateOf<FingerprintSignal?>(null) }
    val hazeState = remember { HazeState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.startAutoRefresh(context)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopAutoRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopAutoRefresh()
        }
    }

    LaunchedEffect(updateState) {
        if (updateState is UpdateState.UpdateAvailable) {
            activeUpdateDetails = updateState as UpdateState.UpdateAvailable
            showUpdateDetailsDialog = true
        }
    }

    // Multi-Permission Request Launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.updatePermissionState(context)
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeChild(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = CyberDarkBg,
                                blurRadius = 20.dp,
                                tints = listOf(HazeTint(color = CyberDarkBg.copy(alpha = 0.35f))),
                                noiseFactor = 0.15f
                            )
                        )
                )

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.BottomCenter)
                        .background(CyberBorder.copy(alpha = 0.4f))
                )
                
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpectreLogo(
                                modifier = Modifier.size(28.dp),
                                color = SpectrePurple
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Spectre",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = CyberTextPrimary,
                                fontSize = 20.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAboutView = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = CyberTextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = CyberTextPrimary
                    )
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeChild(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = CyberDarkBg,
                                blurRadius = 20.dp,
                                tints = listOf(HazeTint(color = CyberDarkBg.copy(alpha = 0.35f))),
                                noiseFactor = 0.15f
                            )
                        )
                )

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopCenter)
                        .background(CyberBorder.copy(alpha = 0.4f))
                )

                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    SignalCategory.values().forEach { category ->
                        val isSelected = uiState.selectedCategory == category
                        val indicatorColor = when (category) {
                            SignalCategory.PASSIVE -> CyberGreen
                            SignalCategory.NEEDS_PERMISSION -> SpectrePurple
                            SignalCategory.ADVANCED -> CyberOrange
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.selectCategory(category) },
                            icon = {
                                Icon(
                                    imageVector = when (category) {
                                        SignalCategory.PASSIVE -> Icons.Default.Visibility
                                        SignalCategory.NEEDS_PERMISSION -> Icons.Default.Shield
                                        SignalCategory.ADVANCED -> Icons.Default.Warning
                                    },
                                    contentDescription = category.title
                                )
                            },
                            label = {
                                Text(
                                    text = category.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = indicatorColor,
                                selectedTextColor = indicatorColor,
                                indicatorColor = indicatorColor.copy(alpha = 0.08f),
                                unselectedIconColor = CyberTextSecondary,
                                unselectedTextColor = CyberTextSecondary
                            ),
                            modifier = Modifier.testTag("tab_${category.name.lowercase()}")
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = CyberDarkBg
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()
        val bottomPadding = innerPadding.calculateBottomPadding()
        val layoutDirection = LocalLayoutDirection.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                )
                .background(CyberDarkBg)
        ) {
            val showFullScreenLoader = uiState.isScanning && uiState.signals.isEmpty()
            
            if (showFullScreenLoader) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = topPadding, bottom = bottomPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = SpectrePurple,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analyzing Device Signals...",
                            color = SpectrePurple,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Reading system and hardware characteristics...",
                            color = CyberTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isScanning,
                    onRefresh = { viewModel.refreshSignals(context) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .haze(hazeState),
                        contentPadding = PaddingValues(
                            top = topPadding + 16.dp,
                            bottom = bottomPadding + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val currentUpdate = updateState
                        if (currentUpdate is UpdateState.UpdateAvailable) {
                            item {
                                UpdateBannerCard(
                                    version = currentUpdate.version,
                                    onClick = { showUpdateDetailsDialog = true }
                                )
                            }
                        }

                        val filteredSignals = uiState.signals.filter { it.category == uiState.selectedCategory }
                        if (filteredSignals.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                                    border = BorderStroke(1.dp, CyberBorder)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(24.dp)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                                tint = CyberTextSecondary,
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "No signals recorded in this category",
                                                color = CyberTextSecondary,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            items(filteredSignals, key = { it.id }) { signal ->
                                SignalRowView(
                                    signal = signal,
                                    context = context,
                                    onActionClick = {
                                        if (signal.category == SignalCategory.NEEDS_PERMISSION && signal.permissionName != null) {
                                            val safePermissions = PermissionCenter.getSafePermissionsToRequest(signal.permissionName)
                                            if (safePermissions.isNotEmpty()) {
                                                permissionsLauncher.launch(safePermissions)
                                            }
                                        }
                                    },
                                    onDetailClick = {
                                        activeDetailedSignal = signal
                                    }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }

    // About Dialog Overlay (using ModalBottomSheet)
    if (showAboutView) {
        ModalBottomSheet(
            onDismissRequest = { showAboutView = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CyberCardBg,
            scrimColor = Color.Black.copy(alpha = 0.4f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            AboutView(
                onDismiss = { showAboutView = false },
                onResetOnboarding = {
                    viewModel.resetOnboarding(context)
                    showAboutView = false
                },
                viewModel = viewModel
            )
        }
    }

    if (versionCheckEnabled == null) {
        VersionOptInDialog(
            onDecision = { enabled ->
                viewModel.setVersionCheckEnabled(context, enabled)
            }
        )
    }

    if (showUpdateDetailsDialog && activeUpdateDetails != null) {
        val update = activeUpdateDetails!!
        UpdateDetailsSheet(
            version = update.version,
            notes = update.notes,
            downloadUrl = update.downloadUrl,
            onDismiss = {
                showUpdateDetailsDialog = false
                activeUpdateDetails = null
            },
            viewModel = viewModel
        )
    }

    // Detailed Registry Dialog Overlay (using ModalBottomSheet)
    if (activeDetailedSignal != null) {
        ModalBottomSheet(
            onDismissRequest = { activeDetailedSignal = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CyberCardBg,
            scrimColor = Color.Black.copy(alpha = 0.4f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            DetailedRegistryView(
                signal = activeDetailedSignal!!,
                onDismiss = { activeDetailedSignal = null }
            )
        }
    }
}

// -------------------------------------------------------------------------
// 3. SCAN SUMMARY CARD
// -------------------------------------------------------------------------
@Composable
fun ScanSummaryCard(inference: com.matepazy.spectre.support.InferenceResult, signals: List<FingerprintSignal>) {
    val passiveCount = signals.count { it.category == SignalCategory.PASSIVE && it.rawValue != "Permission Blocked" && it.rawValue.isNotEmpty() }
    val permissionCount = signals.count { it.category == SignalCategory.NEEDS_PERMISSION && it.rawValue != "Permission Blocked" && it.rawValue.isNotEmpty() }
    val advancedCount = signals.count { it.category == SignalCategory.ADVANCED && it.rawValue != "Permission Blocked" && it.rawValue.isNotEmpty() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large signature status icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SpectrePurple.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Device Signature Icon",
                        tint = SpectrePurple,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device Signature ID",
                        fontSize = 12.sp,
                        color = CyberTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    val clipboard = LocalClipboardManager.current
                    val context = LocalContext.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                clipboard.setText(AnnotatedString(inference.deviceSignature))
                                Toast.makeText(context, "Signature copied!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = inference.deviceSignature,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = SpectrePurple
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Hash",
                            tint = SpectrePurple,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = CyberBorder,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Text(
                text = "Collected Device Signals:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyberBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryCountBadge(
                    label = "Passive",
                    count = passiveCount,
                    color = CyberGreen,
                    modifier = Modifier.weight(1f)
                )
                CategoryCountBadge(
                    label = "Permission-Gated",
                    count = permissionCount,
                    color = SpectrePurple,
                    modifier = Modifier.weight(1f)
                )
                CategoryCountBadge(
                    label = "Advanced",
                    count = advancedCount,
                    color = CyberOrange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CategoryCountBadge(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp
            )
        }
    }
}

// -------------------------------------------------------------------------
// 4. PERMISSION OVERVIEW CARD
// -------------------------------------------------------------------------
@Composable
fun PermissionOverviewCard(
    statuses: Map<String, Boolean>,
    onRequestPermission: () -> Unit,
    onOpenGate: () -> Unit
) {
    val totalPermissions = statuses.size
    val grantedCount = statuses.values.count { it }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "System Permission Gates",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$grantedCount of $totalPermissions active gates open",
                    fontSize = 12.sp,
                    color = CyberTextSecondary
                )
            }
            
            Row {
                TextButton(onClick = onOpenGate) {
                    Text("Expose Gates", color = SpectrePurple, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Unlock All", color = SpectrePurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// 5. CATEGORY SELECTION TABS
// -------------------------------------------------------------------------
@Composable
fun CategorySelectionTabs(
    selectedCategory: SignalCategory,
    onCategorySelected: (SignalCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(100))
            .background(Color(0xFFEDEAF8))
            .border(1.dp, CyberBorder, RoundedCornerShape(100))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SignalCategory.values().forEach { category ->
            val isSelected = selectedCategory == category
            val indicatorColor = when (category) {
                SignalCategory.PASSIVE -> CyberBlue
                SignalCategory.NEEDS_PERMISSION -> CyberOrange
                SignalCategory.ADVANCED -> CyberRed
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100))
                    .background(if (isSelected) indicatorColor.copy(alpha = 0.15f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) indicatorColor.copy(alpha = 0.4f) else Color.Transparent,
                        RoundedCornerShape(100)
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(vertical = 10.dp)
                    .testTag("tab_${category.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = category.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) indicatorColor else CyberTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) indicatorColor else Color.Transparent)
                    )
                }
            }
        }
    }
}

// Helper function to map individual signal ID to its corresponding icon
private fun getSignalIcon(id: String): ImageVector {
    return when {
        id.contains("accessibility_enabled") -> Icons.Default.Accessibility
        id.contains("accessibility_touch_exploration") -> Icons.Default.TouchApp
        id.contains("accessibility_font_scale") -> Icons.Default.TextFields
        id.contains("accessibility_running_services") -> Icons.Default.List
        id.contains("app_target_sdk") -> Icons.Default.Code
        id.contains("app_installer_source") -> Icons.Default.Download
        id.contains("installed_apps_sidechannel") -> Icons.Default.Apps
        id.contains("audio_output_routes") -> Icons.Default.Headset
        id.contains("audio_music_volume") -> Icons.Default.VolumeUp
        id.contains("battery_level") -> Icons.Default.BatteryFull
        id.contains("battery_health_temp") -> Icons.Default.Thermostat
        id.contains("battery_charge_capacity") -> Icons.Default.FlashOn
        id.contains("battery_source") -> Icons.Default.Usb
        id.contains("bluetooth_hardware_status") -> Icons.Default.Bluetooth
        id.contains("bluetooth_bonded_devices") -> Icons.Default.BluetoothConnected
        id.contains("calendar_exposure_count") -> Icons.Default.DateRange
        id.contains("camera_hardware_specs") -> Icons.Default.CameraAlt
        id.contains("camera_runtime_access") -> Icons.Default.Videocam
        id.contains("contacts_exposure_threshold") -> Icons.Default.Contacts
        id.contains("system_hardware_tags") -> Icons.Default.Memory
        id.contains("system_uptime_precision") -> Icons.Default.Timer
        id.contains("system_security_patch") -> Icons.Default.Security
        id.contains("system_kernel_version") -> Icons.Default.Build
        id.contains("sensor_accelerometer_signature") -> Icons.Default.DirectionsRun
        id.contains("sensor_gyro_spec") -> Icons.Default.Sync
        id.contains("sensor_magnetometer_barometer") -> Icons.Default.Explore
        id.contains("display_resolution_dpi") -> Icons.Default.AspectRatio
        id.contains("display_orientation_state") -> Icons.Default.StayCurrentPortrait
        id.contains("fonts_system_inventory") -> Icons.Default.FontDownload
        id.contains("network_transport_type") -> Icons.Default.Wifi
        id.contains("network_carrier_name") -> Icons.Default.Phone
        id.contains("locale_language_tags") -> Icons.Default.Translate
        id.contains("locale_timezone_id") -> Icons.Default.Schedule
        id.contains("pasteboard_status") -> Icons.Default.ContentPaste
        id.contains("storage_capacity_metrics") -> Icons.Default.Storage
        id.contains("webview_user_agent") -> Icons.Default.Web
        id.contains("webview_javascript_engine") -> Icons.Default.Build
        id.contains("location_exposure_status") -> Icons.Default.LocationOn
        id.contains("microphone_runtime_access") -> Icons.Default.Mic
        id.contains("drm_widevine_system_id") -> Icons.Default.Key
        id.contains("phone_state_access") -> Icons.Default.PhoneAndroid
        id.contains("call_log_exposure") -> Icons.Default.Phone
        id.contains("sms_exposure_status") -> Icons.Default.Email
        id.contains("system_accounts_list") -> Icons.Default.AccountBox
        id.contains("notification_post_permission") -> Icons.Default.Notifications
        id.contains("hardware_sensors_footprint") -> Icons.Default.Build
        id.contains("system_settings_leak") -> Icons.Default.Settings
        id.contains("device_uptime_metrics") -> Icons.Default.Timer
        id.contains("opengl_version_sig") -> Icons.Default.PlayArrow
        id.contains("network_interfaces_signature") -> Icons.Default.Share
        id.contains("telephony_carrier_operator") -> Icons.Default.Phone
        id.contains("cpu_ram_specs") -> Icons.Default.Memory
        id.contains("wifi_network_details") -> Icons.Default.Wifi
        id.contains("activity_motion_tracking") -> Icons.Default.DirectionsRun
        id.contains("ambient_sensors_footprint") -> Icons.Default.Thermostat
        id.contains("anti_analysis_signals") -> Icons.Default.BugReport
        id.contains("input_method_package_list") -> Icons.Default.Keyboard
        id.contains("nfc_usb_hardware_state") -> Icons.Default.Usb
        id.contains("sound_stream_volumes") -> Icons.Default.VolumeUp
        id.contains("bluetooth_ble_scanning") -> Icons.Default.Bluetooth
        id.contains("external_media_specs") -> Icons.Default.Image
        else -> Icons.Default.Info
    }
}

// Security danger explanation map for every tracked signal
private fun getTrackingRiskExplanation(signal: FingerprintSignal): String {
    return when (signal.id) {
        "accessibility_enabled" -> {
            "Accessibility services have deep visibility into user interface interactions. Trackers can check if this is enabled to identify custom device access setups."
        }
        "accessibility_touch_exploration" -> {
            "Touch exploration indicates if speech feedback (like TalkBack) is active, which tells trackers if the user relies on visual assistance tools."
        }
        "accessibility_font_scale" -> {
            "Custom font scales help distinguish your device viewport size, narrowing down the anonymity set for browser fingerprinting."
        }
        "accessibility_running_services" -> {
            "Knowing specific active accessibility services helps trackers identify helper utilities or custom tools installed on the device."
        }
        "app_target_sdk" -> {
            "Older target SDK levels can bypass modern privacy features (like scoped storage or runtime permission prompts) on newer Android releases."
        }
        "app_installer_source" -> {
            "The installer source indicates whether the app was installed from an official store or sideloaded via an APK."
        }
        "installed_apps_sidechannel" -> {
            "Querying installed package names allows trackers to compile a list of your apps, revealing personal interests and demographics."
        }
        "audio_output_routes" -> {
            "Active audio output routes (e.g., Bluetooth, headphones, speaker) indicate if you are currently using audio accessories."
        }
        "audio_music_volume" -> {
            "System volume levels can be checked by applications to determine active user interaction or audio profile preferences."
        }
        "battery_level" -> {
            "Granular battery level changes can be tracked in real-time, helping trackers link web browser tabs and app sessions on the same device."
        }
        "battery_health_temp" -> {
            "Battery temperature and charge cycle statistics expose minor hardware variances and battery wear characteristics."
        }
        "battery_charge_capacity" -> {
            "The maximum charge capacity of the battery is a physical characteristic that varies slightly by device, serving as a soft hardware marker."
        }
        "battery_source" -> {
            "Knowing if the device is charging via USB, AC, or wireless details the power source context and typical charging routines."
        }
        "bluetooth_hardware_status" -> {
            "Checking whether Bluetooth is enabled allows background libraries to prepare for beacon or peripheral scanning."
        }
        "bluetooth_bonded_devices" -> {
            "The list of paired Bluetooth device names and MAC addresses is unique to you, creating a persistent identifier that survives app resets."
        }
        "calendar_exposure_count" -> {
            "Accessing calendar metadata (like event density) allows applications to infer user activity patterns and schedules."
        }
        "camera_hardware_specs" -> {
            "Detailed camera lens specifications, focal lengths, and aperture sizes form a unique physical signature of the camera hardware."
        }
        "camera_runtime_access" -> {
            "Camera permissions allow capturing photos or video. Apps must be restricted to prevent unauthorized camera usage."
        }
        "contacts_exposure_threshold" -> {
            "Access to the contacts registry exposes personal names, numbers, and emails, allowing apps to map social connections."
        }
        "system_hardware_tags" -> {
            "Hardware build tags (like bootloader, kernel version, and build fingerprint) help narrow down the exact device model and update status."
        }
        "system_uptime_precision" -> {
            "System uptime tracks the millisecond the device booted. Because boot times are highly specific, it can act as a temporary session identifier."
        }
        "system_security_patch" -> {
            "The security patch level reveals the date of the last security update, which apps use to check for patch status."
        }
        "system_kernel_version" -> {
            "The kernel compilation date and version identify the exact software build of the operating system."
        }
        "sensor_accelerometer_signature" -> {
            "Accelerometer sensor data contains minute manufacturing noise, which can potentially be used as a physical device signature."
        }
        "sensor_gyro_spec" -> {
            "Gyroscope sensor specifications and noise patterns reveal detailed hardware characteristics and motion patterns."
        }
        "sensor_magnetometer_barometer" -> {
            "Magnetometer and barometer readings leak physical orientation relative to magnetic north and altitude changes."
        }
        "display_resolution_dpi" -> {
            "Screen resolution, density (DPI), and refresh rate define the physical display, forming a primary marker for canvas fingerprinting."
        }
        "display_orientation_state" -> {
            "The screen orientation state shows whether the device is in portrait or landscape mode, confirming user viewport layout."
        }
        "fonts_system_inventory" -> {
            "The list of custom system fonts can be compiled by applications to build a highly distinct browser and device fingerprint."
        }
        "network_transport_type" -> {
            "Exposing whether the active network is Wi-Fi or cellular helps trackers identify connection medium and stability."
        }
        "network_carrier_name" -> {
            "The carrier operator name details your network provider, exposing home country and network demographics."
        }
        "locale_language_tags" -> {
            "Language settings immediately define the user's primary spoken language and region configuration."
        }
        "locale_timezone_id" -> {
            "The timezone ID maps the device's general geographic location and current local time offset."
        }
        "pasteboard_status" -> {
            "The system clipboard stores copied text and media, which unauthorized apps could read to access sensitive copied data."
        }
        "storage_capacity_metrics" -> {
            "Storage capacity and available disk space metrics provide partition details that help distinguish device storage builds."
        }
        "webview_user_agent" -> {
            "WebViews expose the User-Agent header, detailing browser versions and operating system builds to websites."
        }
        "webview_javascript_engine" -> {
            "JavaScript engine capabilities and feature availability are used to distinguish browser configurations."
        }
        "location_exposure_status" -> {
            "Geographical coordinates track your physical location, allowing apps to determine your precise address and movements."
        }
        "microphone_runtime_access" -> {
            "Microphone access allows recording audio. Unauthorized access can lead to ambient conversation recording."
        }
        "drm_widevine_system_id" -> {
            "The Widevine DRM Device ID is a cryptographic hardware key that remains permanent across factory resets and updates."
        }
        "phone_state_access" -> {
            "Phone state permissions grant access to SIM serial numbers and phone numbers, allowing direct identification of the subscriber."
        }
        "call_log_exposure" -> {
            "Call log access exposes your history of incoming and outgoing calls, detailing phone numbers and call durations."
        }
        "sms_exposure_status" -> {
            "SMS access exposes incoming text messages, which could contain private conversations and verification codes."
        }
        "system_accounts_list" -> {
            "The accounts list reveals the registered account names (like Google or email accounts) configured on the device."
        }
        "notification_post_permission" -> {
            "Notification permissions allow apps to display alerts. Restricting it prevents unwanted background notifications."
        }
        "hardware_sensors_footprint" -> {
            "Listing all hardware sensors installed on the device creates a distinct hardware configuration profile."
        }
        "system_settings_leak" -> {
            "System settings (like screen timeout or haptic feedback toggles) expose user personalization preferences."
        }
        "device_uptime_metrics" -> {
            "Device uptime tracks the duration since the last system boot, serving as a soft correlation signal."
        }
        "opengl_version_sig" -> {
            "OpenGL and GPU specifications detail the device's graphic capabilities, used to build visual rendering signatures."
        }
        "network_interfaces_signature" -> {
            "Listing network adapters (such as Wi-Fi or cellular interfaces) flags VPN usage and active interface configurations."
        }
        "telephony_carrier_operator" -> {
            "Telephony details reveal country code (MCC) and network code (MNC), identifying the cellular subscription context."
        }
        "cpu_ram_specs" -> {
            "The processor architecture, number of CPU cores, and total RAM capacity define the device's hardware performance level."
        }
        "wifi_network_details" -> {
            "WiFi metadata, such as BSSID and SSID, can be lookup-mapped to resolve the physical location of the wireless access point."
        }
        "activity_motion_tracking" -> {
            "Physical activity permissions track movement patterns like walking, running, or driving for fitness tracking."
        }
        "ambient_sensors_footprint" -> {
            "Ambient light and proximity sensors detect real-time light levels and physical proximity to objects without requiring permissions."
        }
        "anti_analysis_signals" -> {
            "Anti-analysis checks verify if the app is running in an emulator or debugging sandbox, used by apps to detect security audits."
        }
        "input_method_package_list" -> {
            "Enabled input methods and custom keyboard languages narrow down user localization and typing configurations."
        }
        "nfc_usb_hardware_state" -> {
            "NFC and USB status exposes physical accessory attachments or developer ADB bridge debugging modes, mapping user technical proficiency levels."
        }
        "sound_stream_volumes" -> {
            "Exact sound levels for alarms and systems act as temporary side-channel fingerprint coordinates. Standard ringtone URI paths frequently leak custom file names."
        }
        "bluetooth_ble_scanning" -> {
            "Bluetooth scan allows background SDKs to detect localized BLE beacons in physical retail stores, tracking your path through aisles and stores."
        }
        "external_media_specs" -> {
            "Access to external media reveals public photo files, metadata, and storage directories."
        }
        else -> {
            "This device characteristic is queryable by standard applications and can be combined to build a soft device fingerprint."
        }
    }
}

// -------------------------------------------------------------------------
// 6. SIGNAL ROW VIEW (EXPANDABLE)
// -------------------------------------------------------------------------
@Composable
fun SignalRowView(
    signal: FingerprintSignal,
    context: Context,
    onActionClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    val categoryColor = when (signal.category) {
        SignalCategory.PASSIVE -> CyberGreen
        SignalCategory.NEEDS_PERMISSION -> SpectrePurple
        SignalCategory.ADVANCED -> CyberOrange
    }
    
    val signalIcon = remember(signal.id) { getSignalIcon(signal.id) }
    val isBlocked = signal.rawValue == "Permission Blocked"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isBlocked && signal.permissionName != null) {
                    onActionClick()
                } else {
                    onDetailClick()
                }
            }
            .testTag("signal_row_${signal.id}"),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = signalIcon,
                        contentDescription = signal.name,
                        tint = categoryColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = signal.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = signal.description,
                        fontSize = 11.sp,
                        color = CyberTextSecondary,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isBlocked) CyberRed.copy(alpha = 0.08f)
                            else CyberBorder.copy(alpha = 0.3f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isBlocked) CyberRed.copy(alpha = 0.2f) else CyberBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (signal.rawValue.length > 20) signal.rawValue.take(17) + "..." else signal.rawValue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isBlocked) CyberRed else CyberTextPrimary
                        )
                        if (signal.detailedData != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Details",
                                tint = categoryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// 7. PERMISSION GATE VIEW MODAL
// -------------------------------------------------------------------------
@Composable
fun PermissionGateView(
    statuses: Map<String, Boolean>,
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = CyberBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Permissions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "To demonstrate how applications query restricted device data, Spectre can check these system permissions. All data remains strictly local on your device.",
                fontSize = 13.sp,
                color = CyberTextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lists current permissions
            statuses.forEach { (perm, granted) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = PermissionCenter.getPermissionDisplayNames(perm),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyberTextPrimary
                    )
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (granted) CyberGreen.copy(alpha = 0.1f)
                                else CyberRed.copy(alpha = 0.1f)
                            )
                            .border(
                                0.5.dp,
                                if (granted) CyberGreen.copy(alpha = 0.3f)
                                else CyberRed.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (granted) "GRANTED" else "NOT GRANTED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (granted) CyberGreen else CyberRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.5.dp, CyberBorder),
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(100)
                ) {
                    Text("Cancel", color = CyberTextPrimary)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple),
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(100)
                ) {
                    Text("Grant Permissions", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// 8. EXPORT VIEW MODAL
// -------------------------------------------------------------------------
@Composable
fun ExportView(
    signals: List<FingerprintSignal>,
    inference: com.matepazy.spectre.support.InferenceResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val jsonReport = remember(signals) {
        val sb = java.lang.StringBuilder()
        sb.append("{\n")
        sb.append("  \"device_fingerprint_sha256\": \"${inference.deviceSignature}\",\n")
        sb.append("  \"collected_signals_count\": ${signals.size},\n")
        sb.append("  \"collected_signals\": [\n")
        
        signals.forEachIndexed { idx, sig ->
            sb.append("    {\n")
            sb.append("      \"id\": \"${sig.id}\",\n")
            sb.append("      \"name\": \"${sig.name}\",\n")
            sb.append("      \"category\": \"${sig.category.name}\",\n")
            val escapedVal = sig.rawValue.replace("\"", "\\\"").replace("\n", " ")
            sb.append("      \"raw_value\": \"$escapedVal\"\n")
            sb.append("    }${if (idx == signals.size - 1) "" else ","}\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        sb.toString()
    }

    val plainReport = remember(signals) {
        val sb = java.lang.StringBuilder()
        sb.append("=========================================\n")
        sb.append("SPECTRE REPORT\n")
        sb.append("=========================================\n")
        sb.append("Fingerprint Hash (SHA-256): ${inference.deviceSignature}\n")
        sb.append("Collectible Parameters Scanned: ${signals.size}\n\n")
        sb.append("AUDITED DEVICE SIGNALS:\n")
        
        signals.forEach { sig ->
            sb.append("- [${sig.category.title}] ${sig.name} : ${sig.rawValue}\n")
        }
        sb.append("\nReport prepared offline by Spectre.\n")
        sb.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = SpectrePurple,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Export Scan Data",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Export the collected device metrics as structured audit reports.",
            fontSize = 13.sp,
            color = CyberTextSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberBorder.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Report SHA-256 Fingerprint",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyberBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = inference.deviceSignature,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = CyberTextPrimary,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(jsonReport))
                    Toast.makeText(context, "Copied JSON to clipboard!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberTextPrimary),
                border = BorderStroke(1.dp, CyberBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Copy JSON", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, plainReport)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Spectre Report"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple),
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------------------
// 9. ABOUT VIEW MODAL
// -------------------------------------------------------------------------
@Composable
fun AboutView(
    onDismiss: () -> Unit,
    onResetOnboarding: () -> Unit,
    viewModel: SpectreViewModel
) {
    val context = LocalContext.current
    val isBiometricEnabled by viewModel.isBiometricLockEnabled.collectAsState()
    val versionCheckEnabled by viewModel.versionCheckEnabled.collectAsState()
    val updateChannel by viewModel.updateChannel.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    val appIconDrawable = remember(context) {
        try {
            context.packageManager.getApplicationIcon(context.packageName)
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (appIconDrawable != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        appIconDrawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                        appIconDrawable.draw(canvas.nativeCanvas)
                    }
                }
            } else {
                SpectreLogo(
                    modifier = Modifier.size(32.dp),
                    color = SpectrePurple
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Spectre",
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = CyberTextPrimary,
            fontSize = 20.sp
        )

        Text(
            text = "v${com.matepazy.spectre.BuildConfig.VERSION_NAME}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = CyberTextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Spectre was developed to raise hardware tracking awareness. All signals exposed by this app are queryable by any basic script on your device without root.",
            fontSize = 12.sp,
            color = CyberTextSecondary,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(color = CyberBorder, modifier = Modifier.padding(bottom = 12.dp))

        Text(
            text = "Security",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CyberTextSecondary,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CyberDarkBg)
                .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = SpectrePurple,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Biometric Lock",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTextPrimary
                    )
                    Text(
                        text = "Lock application when closed",
                        fontSize = 11.sp,
                        color = CyberTextSecondary
                    )
                }
            }
            Switch(
                checked = isBiometricEnabled,
                onCheckedChange = { checked ->
                    viewModel.setBiometricLockEnabled(context, checked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SpectrePurple,
                    uncheckedThumbColor = CyberTextSecondary,
                    uncheckedTrackColor = CyberBorder,
                    uncheckedBorderColor = CyberTextSecondary
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Updates",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CyberTextSecondary,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CyberDarkBg)
                .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = SpectrePurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Auto Check Updates",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextPrimary
                        )
                        Text(
                            text = "Check version status on startup",
                            fontSize = 11.sp,
                            color = CyberTextSecondary
                        )
                    }
                }
                Switch(
                    checked = versionCheckEnabled == true,
                    onCheckedChange = { checked ->
                        viewModel.setVersionCheckEnabled(context, checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SpectrePurple,
                        uncheckedThumbColor = CyberTextSecondary,
                        uncheckedTrackColor = CyberBorder,
                        uncheckedBorderColor = CyberTextSecondary
                    )
                )
            }

            HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsSuggest,
                        contentDescription = null,
                        tint = SpectrePurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Update Channel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextPrimary
                        )
                        Text(
                            text = "Choose stable vs beta releases",
                            fontSize = 11.sp,
                            color = CyberTextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(CyberBorder)
                        .padding(2.dp)
                ) {
                    val channels = listOf("release", "pre-release")
                    channels.forEach { ch ->
                        val isSel = updateChannel == ch
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100))
                                .background(if (isSel) SpectrePurple else Color.Transparent)
                                .clickable { viewModel.setUpdateChannel(context, ch) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (ch == "release") "Stable" else "Beta",
                                color = if (isSel) Color.White else CyberTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = SpectrePurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Manual Check",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextPrimary
                        )
                        val statusText = when (updateState) {
                            is UpdateState.Checking -> "Checking..."
                            is UpdateState.UpdateAvailable -> "Update available!"
                            is UpdateState.Downloading -> "Downloading..."
                            is UpdateState.Completed -> "Ready to install"
                            is UpdateState.Error -> "Check failed"
                            else -> "Up to date"
                        }
                        Text(
                            text = "Status: $statusText",
                            fontSize = 11.sp,
                            color = CyberTextSecondary
                        )
                    }
                }

                Button(
                    onClick = { viewModel.triggerVersionCheck(context, manual = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple),
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Check", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onResetOnboarding,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberRed),
                border = BorderStroke(1.dp, CyberRed.copy(alpha = 0.3f)),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Reset App", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DetailedRegistryView(
    signal: FingerprintSignal,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val categoryColor = when (signal.category) {
        SignalCategory.PASSIVE -> CyberGreen
        SignalCategory.NEEDS_PERMISSION -> SpectrePurple
        SignalCategory.ADVANCED -> CyberOrange
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val signalIcon = remember(signal.id) { getSignalIcon(signal.id) }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = signalIcon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = signal.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary
                )
                Text(
                    text = signal.category.title + " Signal",
                    fontSize = 11.sp,
                    color = categoryColor,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = {
                    val fullReport = StringBuilder()
                    fullReport.append("Signal: ${signal.name}\n")
                    fullReport.append("Category: ${signal.category.title}\n")
                    fullReport.append("Raw Value Summary: ${signal.rawValue}\n\n")
                    signal.detailedData?.forEach { group ->
                        fullReport.append("--- ${group.categoryName ?: "Registry"} ---\n")
                        group.items.forEach { item ->
                            fullReport.append("${item.label}: ${item.value}")
                            if (item.description != null) {
                                  fullReport.append(" (${item.description})")
                            }
                            fullReport.append("\n")
                        }
                        fullReport.append("\n")
                    }
                    clipboard.setText(AnnotatedString(fullReport.toString()))
                    Toast.makeText(context, "Copied all data to clipboard!", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy All Data",
                    tint = CyberTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Implications",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CyberBlue
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = getTrackingRiskExplanation(signal),
            fontSize = 12.sp,
            color = CyberTextSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 400.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                val detailedDataList = signal.detailedData
                if (detailedDataList.isNullOrEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CyberBorder.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, CyberBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Raw Value",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberTextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = signal.rawValue,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(signal.rawValue))
                                        Toast.makeText(context, "Copied value!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = CyberTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    detailedDataList.forEachIndexed { groupIdx, group ->
                        if (groupIdx > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Text(
                            text = group.categoryName ?: "",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberBlue,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBorder.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                                .padding(vertical = 4.dp)
                        ) {
                            group.items.forEachIndexed { idx, item ->
                                if (idx > 0) {
                                    HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                                }
                                val isSideChannel = signal.id == "installed_apps_sidechannel"
                                if (isSideChannel) {
                                    val isInstalled = item.value.contains("Active")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            val itemIcon = getDetailItemIcon(item.iconName ?: "info")
                                            Icon(
                                                imageVector = itemIcon,
                                                contentDescription = null,
                                                tint = categoryColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = item.label,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CyberTextPrimary
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isInstalled) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = if (isInstalled) "Detected" else "Not Detected",
                                            tint = if (isInstalled) Color.Green else Color.Red,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val itemIcon = getDetailItemIcon(item.iconName ?: "info")
                                        Icon(
                                            imageVector = itemIcon,
                                            contentDescription = null,
                                            tint = categoryColor,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.label,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CyberTextPrimary
                                            )
                                            if (item.description != null) {
                                                Text(
                                                     text = item.description!!,
                                                     fontSize = 11.sp,
                                                     color = CyberTextSecondary,
                                                     lineHeight = 14.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = item.value,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = CyberTextPrimary,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString("${item.label}: ${item.value}"))
                                                Toast.makeText(context, "Copied details!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Row",
                                                tint = CyberTextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = categoryColor),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Text(
                text = "Close Registry",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

private fun getDetailItemIcon(name: String): ImageVector {
    return when (name.lowercase()) {
        "calendar" -> Icons.Default.DateRange
        "camera" -> Icons.Default.CameraAlt
        "person", "contact", "account" -> Icons.Default.Person
        "hardware", "cpu", "sensor" -> Icons.Default.Memory
        "time", "timer" -> Icons.Default.Timer
        "security", "key" -> Icons.Default.Security
        "wifi", "network" -> Icons.Default.Wifi
        "settings" -> Icons.Default.Settings
        "phone", "call" -> Icons.Default.Phone
        "sms", "email" -> Icons.Default.Email
        "media", "image" -> Icons.Default.Image
        "storage" -> Icons.Default.Storage
        "mic" -> Icons.Default.Mic
        "keyboard" -> Icons.Default.Keyboard
        "usb" -> Icons.Default.Usb
        "bluetooth" -> Icons.Default.Bluetooth
        "check" -> Icons.Default.CheckCircle
        "close", "error" -> Icons.Default.Cancel
        else -> Icons.Default.Info
    }
}

@Composable
fun VersionOptInDialog(
    onDecision: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Force a choice */ },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = SpectrePurple,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Automatic Updates",
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Would you like Spectre to automatically check for updates via the GitHub API?",
                    color = CyberTextPrimary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "We care about your privacy: Spectre does not collect or transmit any personal data. Only the standard networking info that GitHub collects during API queries (like your IP address) is generated when checking for updates.",
                    color = CyberTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onDecision(true) },
                colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple)
            ) {
                Text("Enable", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onDecision(false) },
                border = BorderStroke(1.dp, CyberBorder)
            ) {
                Text("Disable", color = CyberTextPrimary)
            }
        },
        containerColor = CyberCardBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
    )
}

@Composable
fun UpdateBannerCard(version: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CyberOrange.copy(alpha = 0.08f)),
        border = BorderStroke(1.2.dp, CyberOrange.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CyberOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = "New Version Available",
                    tint = CyberOrange,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New Update Available: $version",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tap to review release notes and install seamlessly.",
                    fontSize = 11.sp,
                    color = CyberTextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = CyberTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDetailsSheet(
    version: String,
    notes: String,
    downloadUrl: String,
    onDismiss: () -> Unit,
    viewModel: SpectreViewModel
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsState()
    
    var showPermissionExplanation by remember { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Completed) {
            val file = (updateState as UpdateState.Completed).apkFile
            if (viewModel.canInstallPackages(context)) {
                viewModel.installApk(context, file)
            } else {
                showPermissionExplanation = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (updateState !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CyberCardBg,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = CyberOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Update to $version",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                when (updateState) {
                    is UpdateState.Downloading -> {
                        val progress = (updateState as UpdateState.Downloading).progress
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                color = CyberOrange,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Downloading update... ${(progress * 100).toInt()}%",
                                color = CyberTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                color = CyberOrange,
                                trackColor = CyberBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    is UpdateState.Completed -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CyberGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Download Complete",
                                color = CyberTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ready to install the new version.",
                                color = CyberTextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is UpdateState.Error -> {
                        val errorMsg = (updateState as UpdateState.Error).message
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = CyberRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Update Failed",
                                color = CyberTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMsg,
                                color = CyberRed,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        Column {
                            Text(
                                text = "Release Notes:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = CyberBlue,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberDarkBg)
                                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = notes,
                                    fontSize = 12.sp,
                                    color = CyberTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (updateState !is UpdateState.Downloading) {
                    OutlinedButton(
                        onClick = {
                            viewModel.resetUpdateState()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberTextPrimary),
                        border = BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }

                val showAction = updateState !is UpdateState.Downloading
                if (showAction) {
                    when (updateState) {
                        is UpdateState.Completed -> {
                            val file = (updateState as UpdateState.Completed).apkFile
                            Button(
                                onClick = {
                                    if (viewModel.canInstallPackages(context)) {
                                        viewModel.installApk(context, file)
                                    } else {
                                        showPermissionExplanation = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Install", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UpdateState.Error -> {
                            Button(
                                onClick = { viewModel.resetUpdateState() },
                                colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            Button(
                                onClick = { viewModel.startApkDownload(context, downloadUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberOrange),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Download & Install", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text("Installation Permission Required", fontWeight = FontWeight.Bold, color = CyberTextPrimary) },
            text = {
                Text(
                    text = "To update the app, Spectre needs permission to install packages. We will redirect you to the system settings to enable 'Install unknown apps' for Spectre.",
                    color = CyberTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionExplanation = false
                        viewModel.requestInstallPermission(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple)
                ) {
                    Text("Go to Settings", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showPermissionExplanation = false },
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Text("Cancel", color = CyberTextPrimary)
                }
            },
            containerColor = CyberCardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
