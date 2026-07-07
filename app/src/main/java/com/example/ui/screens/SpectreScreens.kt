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
import com.matepazy.spectre.ui.theme.*
import com.matepazy.spectre.viewmodel.SpectreViewModel
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

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
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(CyberRed.copy(alpha = 0.1f))
                    .border(1.5.dp, CyberRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Cryptographic Lock Icon",
                    tint = CyberRed,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "SPECTRE SECURED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = CyberTextPrimary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This application is locked with device biometrics to protect sensitive device audit logs.",
                fontSize = 12.sp,
                color = CyberTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Status: $errorMessage",
                    fontSize = 12.sp,
                    color = CyberRed,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(CyberRed.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

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
                    shape = RoundedCornerShape(100),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Decrypt Database", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberOrange.copy(alpha = 0.08f))
                        .border(1.dp, CyberOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "ENVIRONMENT SIMULATION MODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberOrange,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Physical biometric sensors (fingerprint/face) are absent or unenrolled in this environment.",
                        fontSize = 11.sp,
                        color = CyberTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onUnlockSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberOrange),
                        shape = RoundedCornerShape(100),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Unlock", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        viewModel.refreshSignals(context, isInitial = true)
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
    
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(CyberDarkBg, Color(0xFFECEAF8))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App Logo / Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            SpectreLogo(
                modifier = Modifier.size(54.dp),
                color = SpectrePurple
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "SPECTRE",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = SpectrePurple,
                letterSpacing = 4.sp
            )
        }

        // Center Content with Slide Transition
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
                        title = "What are Spectral Vectors?",
                        description = "Hidden, ghost-like telemetry and tracking vectors quietly monitor your hardware state, battery discharges, network parameters, and screen layouts. Advertisers synthesize these silent background metrics into a unique digital Spectre that tracks you invisibly.",
                        tint = CyberGreen
                    )
                    2 -> OnboardingStepCard(
                        icon = Icons.Default.Security,
                        title = "Classification Levels",
                        description = "Spectre organizes these phantom signals into three distinct collection vectors:\n\n• PASSIVE: Totally invisible side-channels queryable silently without permissions.\n• RESTRICTED: Standard sandbox permission boundaries.\n• ADVANCED: Deep device fingerprinting, browser identifiers, and font hashes.",
                        tint = CyberBlue
                    )
                    3 -> OnboardingStepCard(
                        icon = Icons.Default.Visibility,
                        title = "Offline Privacy Guarantee",
                        description = "Spectre operates 100% locally and offline. Your hardware metrics never leave your device. By continuing, you authorize Spectre to conduct a deep spectral analysis of your system APIs.",
                        tint = SpectrePurple
                    )
                }
            }
        }

        // Bottom Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Indicator dots
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                for (i in 1..3) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (step == i) 20.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (step == i) SpectrePurple else CyberBorder.copy(alpha = 0.8f))
                    )
                }
            }

            // Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier
                            .height(48.dp)
                            .weight(1f)
                            .padding(end = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SpectrePurple
                        ),
                        border = BorderStroke(1.5.dp, SpectrePurple.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(100)
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
                        .padding(start = if (step > 1) 8.dp else 0.dp)
                        .testTag("onboarding_next_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (step == 3) CyberGreen else SpectrePurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(100)
                ) {
                    Text(
                        text = if (step == 3) "Agree & Scan" else "Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (step < 3) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
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
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(tint, SpectrePurple)
            )
        ),
        shape = RoundedCornerShape(topStart = 28.dp, bottomEnd = 28.dp, topEnd = 12.dp, bottomStart = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.1f))
                    .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (icon == Icons.Default.Radar) {
                    SpectreLogo(
                        modifier = Modifier.size(54.dp),
                        color = tint
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = CyberTextPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
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
    var showExportDialog by remember { mutableStateOf(false) }
    var showAboutView by remember { mutableStateOf(false) }
    var activeDetailedSignal by remember { mutableStateOf<FingerprintSignal?>(null) }
    val hazeState = remember { HazeState() }

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
                // Background Layer for Glassmorphism
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

                // Bottom border to define the glass card edge
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
                                modifier = Modifier.size(36.dp),
                                color = SpectrePurple
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "SPECTRE",
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = SpectrePurple,
                                letterSpacing = 2.sp,
                                fontSize = 20.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAboutView = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About App",
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
                // Background Layer for Glassmorphism
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

                // Top border to define the glass card edge for bottom bar
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
                                indicatorColor = indicatorColor.copy(alpha = 0.12f),
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
                // scanning loader state
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
                            text = "Scanning Ghostly Tracking Vectors...",
                            color = SpectrePurple,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Mapping invisible side-channel signatures",
                            color = CyberTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                // Core Screen Content wrapped with PullToRefreshBox
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
                        // Filtered signal rows
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

                        // Small structural padding
                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }

    // Export Dialog Overlay
    if (showExportDialog && uiState.inferenceResult != null) {
        Dialog(
            onDismissRequest = { showExportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ExportView(
                signals = uiState.signals,
                inference = uiState.inferenceResult,
                onDismiss = { showExportDialog = false }
            )
        }
    }

    // About Dialog Overlay
    if (showAboutView) {
        Dialog(
            onDismissRequest = { showAboutView = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
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

    // Detailed Registry Dialog Overlay
    if (activeDetailedSignal != null) {
        Dialog(
            onDismissRequest = { activeDetailedSignal = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
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
    val borderBrush = remember {
        Brush.linearGradient(
            colors = listOf(SpectrePurple, CyberBlue)
        )
    }

    val passiveCount = signals.count { it.category == SignalCategory.PASSIVE && it.rawValue != "Permission Blocked" && it.rawValue.isNotEmpty() }
    val permissionCount = signals.count { it.category == SignalCategory.NEEDS_PERMISSION && it.rawValue != "Permission Blocked" && it.rawValue.isNotEmpty() }
    val advancedCount = signals.count { it.category == SignalCategory.ADVANCED && it.rawValue != "Permission Blocked" && it.rawValue.isNotEmpty() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.5.dp, borderBrush),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomStart = 24.dp, topStart = 8.dp, bottomEnd = 8.dp)
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
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
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
                text = "Active Data Collection Vectors:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyberBlue,
                fontFamily = FontFamily.Monospace,
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
        id.contains("body_sensors_tracking") -> Icons.Default.Favorite
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
            "Accessibility services have full view of your screen, keyboard inputs, and on-screen clicks. If an unauthorized background app or malicious SDK monitors enabled services, it can log typed text (passwords, PINs, or emails) and automate fake gestures to execute ad fraud or transfer funds without consent."
        }
        "accessibility_touch_exploration" -> {
            "Touch exploration indicates that speech assistance (like TalkBack) is active. Tracking this can identify visually-impaired or elderly user profiles, allowing malicious platforms to serve targeted phishing campaigns tailored to exploit specific accessibility flows."
        }
        "accessibility_font_scale" -> {
            "A customized font scale (e.g. 1.22x or 0.85x) narrows down the anonymity set of a device to a fraction of a percent of global web traffic, making web browser tracking extremely easy even when IP tracking is blocked."
        }
        "accessibility_running_services" -> {
            "Specific running accessibility helper names allow trackers to identify exact utility or security apps you use. Malware can use this to disable screen overlays, target security tools, or prepare tailor-made accessibility overlay attacks."
        }
        "app_target_sdk" -> {
            "Malicious applications target old SDK versions (e.g. Android 9 or below) intentionally to completely bypass modern Android permission gates, scoped storage rules, and hardware sandboxing layers."
        }
        "app_installer_source" -> {
            "Knowing if an app was installed via official stores (Google Play) or sideloaded (APK) lets trackers detect developer configurations, vulnerability testers, or non-technical profiles who might be more vulnerable to targeted social engineering attacks."
        }
        "installed_apps_sidechannel" -> {
            "Scanning installed applications (such as dating, financial, religious, medical, or political apps) leaks intimate personal details about your lifestyle, health, beliefs, and net worth. Advertisers compile this 'app footprint' into a persistent profile to track you across different websites and apps."
        }
        "audio_output_routes" -> {
            "Active audio peripheral routes (like wired headsets vs speakerphone) reveal when a user is actively on a phone call or listening to music, which is exploited by trackers to run context-aware ads or check physical device presence."
        }
        "audio_music_volume" -> {
            "Monitoring system volume levels allows apps to detect when you are actively using the device or ignoring notifications, optimizing intrusive background ads or detecting silent user-away states."
        }
        "battery_level" -> {
            "Battery level changes are highly granular and update constantly. Advertisers query the precise rate of battery draining to link multiple open web browser tabs or distinct apps to the exact same device, effectively bypassing all browser cookie blocks."
        }
        "battery_health_temp" -> {
            "Battery temperature and physical health status expose hardware wear. Trackers use this distinct hardware decay signature to identify your device even after complete factory resets or app reinstalls."
        }
        "battery_charge_capacity" -> {
            "The physical maximum charge capacity of the battery is highly unique due to manufacturing variations, acting as a permanent hardware identifier that cannot be changed by clearing application caches."
        }
        "battery_source" -> {
            "Identifying if you are plugged into USB, AC, or Wireless reveals your physical location context (at a desk, in a car, or in bed) and charging habits, enabling highly context-targeted advertising and behavior modeling."
        }
        "bluetooth_hardware_status" -> {
            "Determining whether Bluetooth is enabled allows background tracking SDKs to prompt you for local beacon scanning or silently monitor your proximity to commercial Bluetooth beacons in physical stores."
        }
        "bluetooth_bonded_devices" -> {
            "The list of paired Bluetooth devices (smart watches, car audio, smart TVs, or home speakers) is unique to you. Because these device names and MAC addresses do not change, tracking bonded Bluetooth devices creates an un-resettable geographic and hardware anchor of your identity."
        }
        "calendar_exposure_count" -> {
            "Exposing calendar event density lets trackers trace your schedule patterns and detect when you are traveling, busy, or asleep, helping malicious networks time highly target-specific scams or ads."
        }
        "camera_hardware_specs" -> {
            "Camera sensor data details precise optical dimensions, aperture sizes, and lens counts. Trackers combine these physical camera specifications to build a distinct hardware signature that cannot be altered or reset."
        }
        "camera_runtime_access" -> {
            "Camera runtime permission grants background streams the ability to capture photos or video. Unauthorized apps can exploit this to perform ambient environment mapping or build visual facial signatures without your explicit active awareness."
        }
        "contacts_exposure_threshold" -> {
            "Contacts list access gives standard apps full names, personal phone numbers, emails, and physical addresses of everyone you know. Trackers compile this social graph into giant shadow-profile databases, leaking the privacy of your family and friends."
        }
        "system_hardware_tags" -> {
            "Standard build fingerprints (bootloader version, build ID, kernel version) are shared among identical phone batches. While not completely unique, compiling 10+ hardware attributes restricts the search domain to a tiny pool, making device tracking across the web highly precise."
        }
        "system_uptime_precision" -> {
            "Uptime tracks the exact millisecond your device booted up. Because no two devices boot up at the exact same millisecond, subtracting uptime from current calendar time generates a perfect, un-spoofable session ID that links your activity across different apps."
        }
        "system_security_patch" -> {
            "Knowing your exact OS security patch date lets malicious apps map out known, unpatched software vulnerabilities on your device, allowing targeted exploit delivery or remote code executions."
        }
        "system_kernel_version" -> {
            "Your specific OS kernel compilation signature is unique to your exact software build, letting cross-app trackers correlate browser and app data easily to build a persistent device profile."
        }
        "sensor_accelerometer_signature" -> {
            "Motion sensors are accessible without permissions. Because every accelerometer has minute manufacturing defects, it generates slightly biased noise patterns that are completely unique to your phone. Research shows that malicious web scripts can use this noise to keylog keystrokes on your keyboard or track your walking gait."
        }
        "sensor_gyro_spec" -> {
            "Detailed gyroscope performance characteristics expose your movement patterns, which malicious scripts use to reconstruct your walking speed, physical posture, or keyboard keystrokes without requiring standard permission gates."
        }
        "sensor_magnetometer_barometer" -> {
            "Magnetometer and barometer values leak precise physical compass orientations and atmospheric pressure changes, revealing your exact building floor, altitude, and physical movements."
        }
        "display_resolution_dpi" -> {
            "Exact screen pixel dimensions, refresh rates, and display density reveal the specific physical chassis. It is combined with other viewport settings to construct a persistent canvas fingerprint to track you across browser sessions."
        }
        "display_orientation_state" -> {
            "Real-time screen rotation leaks when you flip your phone. Trackers analyze this physical action to confirm user presence, optimize high-conversion video ads, or detect if the device is laying flat on a desk."
        }
        "fonts_system_inventory" -> {
            "Custom system fonts loaded onto your device are highly unique. Listing all available font files allows web trackers to distinguish your browser from millions of other devices with almost 99% accuracy."
        }
        "network_transport_type" -> {
            "Exposing whether you are on Wi-Fi or Cellular reveals your connection stability and medium, which trackers use to tailor bandwidth-heavy video advertising or detect when you are traveling."
        }
        "network_carrier_name" -> {
            "Your carrier name exposes your home country and cellular subscription level, enabling trackers to profile your geographic location, carrier tier, and spending demographics."
        }
        "locale_language_tags" -> {
            "Standard language and timezone settings immediately narrow down your geographical continent, country, and language demographic, enabling broad-scale demographic profiling."
        }
        "locale_timezone_id" -> {
            "Timezone information narrows down your geographical continent and country, assisting trackers in mapping your daily active hours, sleep schedule, and general location."
        }
        "pasteboard_status" -> {
            "The clipboard is a global area accessible to all foreground apps. Because users frequently copy passwords, authentication codes, addresses, and credit card numbers, background scripts automatically query the clipboard to steal highly sensitive credentials."
        }
        "storage_capacity_metrics" -> {
            "The exact maximum and free storage byte counts reflect your specific disk partition, creating a volatile but highly custom metric. When polled repeatedly, storage capacity becomes a key correlation factor."
        }
        "webview_user_agent" -> {
            "User-Agents detail the exact firmware compile version, safari build, and engine update. Trackers use it as the main header to recognize and track you across the web, regardless of whether you clear cookies or use private browser tabs."
        }
        "webview_javascript_engine" -> {
            "JavaScript engine properties reveal specific rendering capabilities. Trackers combine these execution times to build a distinct browser fingerprint to track you across private browsing sessions."
        }
        "location_exposure_status" -> {
            "Precise geographical coordinates pin down your home address, medical clinic visits, workplace, and personal habits. Location trackers continuously record your coordinates, exposing you to active tracking, physical stalking, and geofencing ad profiles."
        }
        "microphone_runtime_access" -> {
            "Microphone access allows standard apps to record and analyze audio in the background. It is exploited by bad actors to build voice profiles, detect ambient environments, or process keyword triggers to target advertising based on real-world conversations."
        }
        "drm_widevine_system_id" -> {
            "The Widevine DRM Device Unique ID is cryptographically burned into your hardware chip. Since it cannot be cleared by factory resets, app reinstalls, or software updates, any app reading it has a permanent, lifetime tracker of your physical device."
        }
        "phone_state_access" -> {
            "The READ_PHONE_STATE permission grants access to persistent device numbers like SIM serials and IMEIs. This allows standard applications to instantly identify you and link your phone number, service plan, and identity to advertiser databases."
        }
        "call_log_exposure" -> {
            "Exposing call history logs allows advertising and data brokering SDKs to trace everyone you call, the time of calls, and conversation durations, reconstructing your entire private professional and social network."
        }
        "sms_exposure_status" -> {
            "Exposing text message databases allows apps to read highly confidential information like single-use 2FA login codes, bank transaction amounts, shipping track links, and personal chats."
        }
        "system_accounts_list" -> {
            "Exposing registered accounts tells standard apps the list of accounts linked to your device (e.g. Gmail, Outlook, Spotify). It reveals your email addresses, letting cross-app trackers instantly tie your anonymous browsing data to your real-world identity."
        }
        "body_sensors_tracking" -> {
            "Body sensors record delicate biometric inputs like continuous heart rate. Access to this data is highly sensitive and is actively bought by insurance companies and health profiles to track user physical activity, heart abnormalities, and health metrics."
        }
        "notification_post_permission" -> {
            "Spamware uses notification permissions to spam click-bait ads, bypass battery restrictions by keeping services alive, and manipulate user attention, constantly recording when you interact with notifications to profile your active hours."
        }
        "hardware_sensors_footprint" -> {
            "Every phone has a distinct combination of sensors (accelerometer, gyroscope, barometer) from various manufacturers. Polling the full list of installed sensors creates a highly unique hardware signature requiring zero permissions."
        }
        "system_settings_leak" -> {
            "Your customized system settings (timeout duration, custom brightness, haptic toggles) reveal highly personal preference states. Because they are readable without any permission, they serve as easy fingerprinting markers."
        }
        "device_uptime_metrics" -> {
            "Uptime tracks the exact millisecond your device booted up. Because no two devices boot up at the exact same millisecond, subtracting uptime from current calendar time generates a perfect, un-spoofable session ID that links your activity across different apps."
        }
        "opengl_version_sig" -> {
            "Identifying your OpenGL version and GPU specifications details the exact graphic driver build. It is combined with canvas rendering to construct highly robust GPU profiles for persistent web-tracking."
        }
        "network_interfaces_signature" -> {
            "Scanning local network adapters (e.g., wlan0, rmnet_data) reveals your precise connection medium. Importantly, checking for active virtual tunnel adapters (tun0) immediately flags if you use a VPN, letting trackers bypass VPN masking or flag your traffic."
        }
        "telephony_carrier_operator" -> {
            "Querying carrier details exposes your service provider and country registration without needing GPS permissions. This information is leveraged to serve country-restricted advertisements, profile spending capabilities based on high-end vs low-end carriers, and tracking cross-border roaming."
        }
        "cpu_ram_specs" -> {
            "CPU architecture and exact memory limits let advertisers pinpoint hardware tiers, grouping similar device batches together to bypass simple cookie-based profiling."
        }
        "wifi_network_details" -> {
            "Querying connected WiFi router MAC addresses allows lookup against global BSSID mapping databases, pinning your absolute location within meters without active GPS."
        }
        "activity_motion_tracking" -> {
            "Real-time step counts and physical movement profiles indicate your habits, fitness levels, active commute times, and daily schedules."
        }
        "ambient_sensors_footprint" -> {
            "Ambient light lux and proximity sensors require zero permissions. Tracking them lets background trackers identify when your phone is in a pocket, a purse, or being actively held."
        }
        "anti_analysis_signals" -> {
            "Tracking SDKs run emulator, root, and sandbox heuristics to check if the app is monitored by security analysts, hiding their real behavior when tested."
        }
        "input_method_package_list" -> {
            "The combination of enabled keyboard layouts and customized IME dictionary languages restricts your anonymity pool to a tiny fraction of global devices."
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
            "Public photo count, filenames, and Exif metadata provide precise location tags, camera sensor calibration offsets, and private daily activity counts."
        }
        else -> {
            "This signal is queryable by applications and web scripts. Even small, seemingly harmless details are aggregated using advanced profiling algorithms to construct a unique fingerprint, removing your online anonymity."
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
    var expanded by remember { mutableStateOf(false) }
    
    val categoryColor = when (signal.category) {
        SignalCategory.PASSIVE -> CyberGreen
        SignalCategory.NEEDS_PERMISSION -> SpectrePurple
        SignalCategory.ADVANCED -> CyberOrange
    }
    
    val signalIcon = remember(signal.id) { getSignalIcon(signal.id) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .testTag("signal_row_${signal.id}"),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, if (expanded) categoryColor.copy(alpha = 0.5f) else CyberBorder),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Individual Signal Icon instead of generic Category Icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = signalIcon,
                        contentDescription = signal.name,
                        tint = categoryColor,
                        modifier = Modifier.size(14.dp)
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
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = signal.description,
                        fontSize = 11.sp,
                        color = CyberTextSecondary,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Raw value chip and interactive chevron indicator if detailedData is available
                if (signal.detailedData != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (signal.rawValue == "Permission Blocked") CyberRed.copy(alpha = 0.1f)
                                else CyberBorder.copy(alpha = 0.4f)
                            )
                            .border(
                                1.dp,
                                if (signal.rawValue == "Permission Blocked") CyberRed.copy(alpha = 0.4f)
                                else categoryColor.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onDetailClick() }
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
                                color = if (signal.rawValue == "Permission Blocked") CyberRed else CyberTextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Detailed Registry",
                                tint = categoryColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (signal.rawValue == "Permission Blocked") CyberRed.copy(alpha = 0.1f)
                                else CyberBorder
                            )
                            .border(
                                0.5.dp,
                                if (signal.rawValue == "Permission Blocked") CyberRed.copy(alpha = 0.3f)
                                else CyberBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (signal.rawValue.length > 25) signal.rawValue.take(22) + "..." else signal.rawValue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (signal.rawValue == "Permission Blocked") CyberRed else CyberTextPrimary
                        )
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(
                    color = CyberBorder,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Text(
                    text = "Why This Tracking Is Dangerous:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberRed,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = getTrackingRiskExplanation(signal),
                    fontSize = 12.sp,
                    color = CyberTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Raw Signal Value:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberBlue,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                val clipboard = LocalClipboardManager.current
                val localContext = LocalContext.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberBorder.copy(alpha = 0.5f))
                        .clickable {
                            clipboard.setText(AnnotatedString(signal.rawValue))
                            Toast.makeText(localContext, "Copied raw value!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = signal.rawValue,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CyberTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Raw Value",
                            tint = CyberTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // If permission gate and blocked, show button to unlock
                if (signal.rawValue == "Permission Blocked" && signal.permissionName != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onActionClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberOrange),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Grant ${signal.permissionName.substringAfterLast(".")}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
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
        shape = RoundedCornerShape(16.dp)
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
                    text = "Permission Sandbox Gates",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "In order to demonstrate how advertising software reads restricted variables, Spectre needs access to these permission categories. All data remains strictly local on your device.",
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
                            text = if (granted) "UNLOCKED" else "SECURE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (granted) CyberGreen else CyberRed,
                            fontFamily = FontFamily.Monospace
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

    // Synthesize structured JSON
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
            // Escape quotes inside raw values
            val escapedVal = sig.rawValue.replace("\"", "\\\"").replace("\n", " ")
            sb.append("      \"raw_value\": \"$escapedVal\"\n")
            sb.append("    }${if (idx == signals.size - 1) "" else ","}\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        sb.toString()
    }

    // Synthesize PlainText Report
    val plainReport = remember(signals) {
        val sb = java.lang.StringBuilder()
        sb.append("=========================================\n")
        sb.append("SPECTRE: SYSTEM COGNIZANCE COLLECTIBLE VECTORS REPORT\n")
        sb.append("Generated local time: 2026-07-05\n")
        sb.append("=========================================\n")
        sb.append("Fingerprint Hash (SHA-256): ${inference.deviceSignature}\n")
        sb.append("Collectible Parameters Scanned: ${signals.size}\n\n")
        sb.append("SYSTEM COGNIZANCE VECTORS AUDITED:\n")
        
        signals.forEach { sig ->
            sb.append("- [${sig.category.title}] ${sig.name} : ${sig.rawValue}\n")
        }
        sb.append("\nReport prepared offline by Spectre for local privacy-awareness verification.\n")
        sb.toString()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(vertical = 24.dp)
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = CyberBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Privacy Audit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Export your local audited device parameters in machine-readable JSON or standard plain-text formatting. You can copy the code directly or share it with external audit centers.",
                fontSize = 12.sp,
                color = CyberTextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberDarkBg)
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = jsonReport,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = CyberTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.5.dp, CyberBorder),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(100)
                ) {
                    Text("Close", color = CyberTextPrimary)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(jsonReport))
                        Toast.makeText(context, "JSON report copied!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(100)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = SpectrePurple
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy JSON", color = SpectrePurple, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Spectre Hardware Fingerprint")
                                putExtra(Intent.EXTRA_TEXT, plainReport)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Privacy Audit"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple),
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(100)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
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

    Card(
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(vertical = 24.dp)
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberBlue.copy(alpha = 0.1f))
                    .border(1.dp, CyberBlue, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                SpectreLogo(
                    modifier = Modifier.size(36.dp),
                    color = CyberBlue
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Spectre",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTextPrimary
            )

            Text(
                text = "v1.2.0",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = CyberTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Spectre was developed to raise deep hardware tracking awareness. Modern ad SDKs operate in secrecy, combining dozens of silent passive side-channels to bypass advertising ID resets entirely.\n\nAll signals exposed by this app are queryable by any basic script on your device.",
                fontSize = 12.sp,
                color = CyberTextSecondary,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = CyberBorder, modifier = Modifier.padding(bottom = 12.dp))

            Text(
                text = "SECURITY LOCKDOWN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = CyberTextSecondary,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberDarkBg)
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
                            text = "Lock Spectre when closed",
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
                        checkedTrackColor = SpectrePurple
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = CyberBorder, modifier = Modifier.padding(bottom = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onResetOnboarding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberRed),
                    border = BorderStroke(1.5.dp, CyberRed.copy(alpha = 0.5f)),
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(100)
                ) {
                    Text("Re-Onboard", fontSize = 11.sp)
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrePurple),
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(100)
                ) {
                    Text("Got It", color = Color.White, fontWeight = FontWeight.Bold)
                }
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

    Card(
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(vertical = 24.dp)
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.80f)),
        border = BorderStroke(1.5.dp, categoryColor.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Drag Handle Indicator
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CyberBorder)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val signalIcon = remember(signal.id) { getSignalIcon(signal.id) }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.12f)),
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
                        text = signal.category.title + " Vector",
                        fontSize = 11.sp,
                        color = categoryColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Copy All Button
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
                        Toast.makeText(context, "Copied entire registry to clipboard!", Toast.LENGTH_SHORT).show()
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

            // Scrollable Registry Content
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 450.dp)
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    val detailedDataList = signal.detailedData
                    if (detailedDataList.isNullOrEmpty()) {
                        // Fallback: If no detailedData, show a structured card for the rawValue
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CyberBorder.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, CyberBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Raw Registry Value",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberBlue,
                                    fontFamily = FontFamily.Monospace
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
                        // Display Groups
                        detailedDataList.forEachIndexed { groupIdx, group ->
                            if (groupIdx > 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            
                            Text(
                                text = (group.categoryName ?: "").uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberBlue,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            // Display Table/Grid Layout for items
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CyberBorder.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, CyberBorder),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column {
                                    group.items.forEachIndexed { idx, item ->
                                        if (idx > 0) {
                                            HorizontalDivider(color = CyberBorder, thickness = 0.5.dp)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Item Icon
                                            val itemIcon = getDetailItemIcon(item.iconName ?: "info")
                                            Icon(
                                                imageVector = itemIcon,
                                                contentDescription = null,
                                                tint = categoryColor.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.label,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CyberTextPrimary
                                                )
                                                if (item.description != null) {
                                                    Text(
                                                        text = item.description!!,
                                                        fontSize = 10.sp,
                                                        color = CyberTextSecondary,
                                                        lineHeight = 12.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.value,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = CyberTextPrimary,
                                                    lineHeight = 14.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Individual Row Copy Button
                                            IconButton(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString("${item.label}: ${item.value}"))
                                                    Toast.makeText(context, "Copied row detail!", Toast.LENGTH_SHORT).show()
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

            // Action/Close button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = categoryColor),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100)
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
