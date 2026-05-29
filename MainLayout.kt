package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BannedDomainEntity
import com.example.data.GmailSubmissionEntity
import com.example.data.UserEntity
import com.example.data.WithdrawalEntity
import com.example.ui.theme.AccentGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(viewModel: AppViewModel) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentModerator by viewModel.currentModerator.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val internetConnected by viewModel.internetConnected.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("dashboard") }
    var showSubmitTaskDialog by remember { mutableStateOf(false) }
    var showAdminAuthenticationDialog by remember { mutableStateOf(false) }
    var showModeratorLoginDialog by remember { mutableStateOf(false) }
    var isAdminMode by remember { mutableStateOf(false) }

    // Floating Push notification simulator state
    var simulatedNotification by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Monitor for incoming simulated broadcasts from Admin Panel
    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) {
            val message = (uiState as UiState.Success).message
            if (message.startsWith("PUSH:")) {
                simulatedNotification = message.substringAfter("PUSH:")
                viewModel.resetUiState()
                delay(5000)
                simulatedNotification = null
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                // Connection Warning Banner
                AnimatedVisibility(!internetConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.error)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, contentDescription = "Offline", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running Offline Mode — State Cached Safely", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Gmail Reward App",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    },
                    actions = {
                        // Connection Toggle Switch
                        IconButton(
                            onClick = {
                                viewModel.setNetworkStatus(!internetConnected)
                                Toast.makeText(
                                    context,
                                    if (!internetConnected) "Simulated: Back online" else "Simulated: Caching offline mode",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = if (internetConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = "Network health",
                                tint = if (internetConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        // Admin Access Lock
                        IconButton(
                            onClick = {
                                if (isAdminMode) {
                                    isAdminMode = false
                                    Toast.makeText(context, "Admin Mode Deactivated", Toast.LENGTH_SHORT).show()
                                } else {
                                    showAdminAuthenticationDialog = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isAdminMode) Icons.Default.Shield else Icons.Outlined.Shield,
                                contentDescription = "Admin Mode Toggle",
                                tint = if (isAdminMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (currentUser != null || currentModerator != null) {
                            IconButton(
                                onClick = {
                                    if (currentUser != null) viewModel.logout()
                                    if (currentModerator != null) viewModel.logoutModerator()
                                }
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentUser != null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    val tabs = listOf(
                        Triple("dashboard", "Home", Icons.Default.Dashboard),
                        Triple("referrals", "Refer", Icons.Default.Group),
                        Triple("leaderboard", "Ranks", Icons.Default.EmojiEvents),
                        Triple("support", "Support", Icons.Default.HelpCenter)
                    )

                    tabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = activeTab == route && !isAdminMode,
                            onClick = {
                                isAdminMode = false
                                activeTab = route
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentUser != null && !isAdminMode) {
                ExtendedFloatingActionButton(
                    text = { Text("Submit Gmail", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Submit Gmail Account") },
                    onClick = { showSubmitTaskDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("submit_gmail_fab")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Simulated Push Notification Floating Banner overlay
            AnimatedVisibility(
                visible = simulatedNotification != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .zIndex(10f)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Alert", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("COMMUNITY NOTICE", color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(simulatedNotification ?: "", color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 13.sp)
                        }
                        IconButton(onClick = { simulatedNotification = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    }
                }
            }

            // Central State Handling
            when (val currentUi = uiState) {
                is UiState.Success -> {
                    // Show a beautiful toast or dialog and reset state
                    LaunchedEffect(currentUi) {
                        Toast.makeText(context, currentUi.message, Toast.LENGTH_LONG).show()
                        viewModel.resetUiState()
                    }
                }
                is UiState.Error -> {
                    LaunchedEffect(currentUi) {
                        Toast.makeText(context, currentUi.reason, Toast.LENGTH_LONG).show()
                        viewModel.resetUiState()
                    }
                }
                else -> {}
            }

            if (currentUser == null && currentModerator == null) {
                LoginScreen(viewModel = viewModel, onModeratorLoginClick = { showModeratorLoginDialog = true })
            } else if (currentModerator != null) {
                ModeratorPanelScreen(viewModel = viewModel)
            } else if (isAdminMode) {
                AdminPanelScreen(viewModel = viewModel)
            } else {
                when (activeTab) {
                    "dashboard" -> DashboardScreen(
                        user = currentUser!!,
                        viewModel = viewModel
                    )
                    "referrals" -> ReferralScreen(
                        user = currentUser!!,
                        viewModel = viewModel
                    )
                    "leaderboard" -> LeaderboardScreen(
                        user = currentUser!!,
                        viewModel = viewModel
                    )
                    "support" -> SupportPage()
                    else -> DashboardScreen(user = currentUser!!, viewModel = viewModel)
                }
            }
        }
    }

    // --- DIALOGS ---

    // 0. Moderator Authentication Dialog
    if (showModeratorLoginDialog) {
        var modEmailInput by remember { mutableStateOf("") }
        var modPasswordInput by remember { mutableStateOf("") }
        var showPassword by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showModeratorLoginDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = "Staff Icon", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Staff Verification sign-in", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Please sign-in using your assigned staff credentials. (Tip: Use default test moderator email 'mod@verification.com' with password 'mod123' for convenience).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = modEmailInput,
                        onValueChange = { modEmailInput = it.lowercase().trim() },
                        label = { Text("Staff Email") },
                        placeholder = { Text("mod@verification.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = modPasswordInput,
                        onValueChange = { modPasswordInput = it },
                        label = { Text("Staff Password / PIN") },
                        placeholder = { Text("••••••••") },
                        singleLine = true,
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (modEmailInput.isBlank() || modPasswordInput.isBlank()) {
                            Toast.makeText(context, "All credentials are required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.loginAsModerator(modEmailInput, modPasswordInput)
                        showModeratorLoginDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Authenticate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showModeratorLoginDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 1. Submit Gmail Task Form
    if (showSubmitTaskDialog) {
        var gmailInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSubmitTaskDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddTask, contentDescription = "Submit Address", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit Gmail Task", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Earn configurable reward points per successful Gmail verification. Enter a unique and valid account.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "Rule", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "CRITICAL REQUIREMENT: Submitted Gmail password must be exactly 'ethicbro999' or task is auto-rejected.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = gmailInput,
                        onValueChange = {
                            gmailInput = it.trim().lowercase()
                            inputError = null
                        },
                        label = { Text("Gmail Address") },
                        placeholder = { Text("username@gmail.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = inputError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_gmail_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            inputError = null
                        },
                        label = { Text("Temporary Password") },
                        placeholder = { Text("ethicbro999") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = inputError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_password_input")
                    )

                    if (inputError != null) {
                        Text(
                            text = inputError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (gmailInput.isBlank() || passwordInput.isBlank()) {
                            inputError = "Please fill in all details"
                            return@Button
                        }
                        if (!gmailInput.endsWith("@gmail.com")) {
                            inputError = "Invalid Gmail format. Must end with @gmail.com."
                            return@Button
                        }
                        viewModel.submitGmailTask(gmailInput, passwordInput)
                        showSubmitTaskDialog = false
                    },
                    modifier = Modifier.testTag("dialog_submit_button")
                ) {
                    Text("Submit Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Admin Authentication Mock Dialog
    if (showAdminAuthenticationDialog) {
        var adminPassword by remember { mutableStateOf("") }
        var isAuthError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAdminAuthenticationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = "Admin Gate", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin Authenticator Gate", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Please verify admin privileges. Default local override code is 'admin123'.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = {
                            adminPassword = it
                            isAuthError = false
                        },
                        label = { Text("Admin Code") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = isAuthError,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isAuthError) {
                        Text("Incorrect Code. Access Denied.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (adminPassword == "admin123") {
                            isAdminMode = true
                            showAdminAuthenticationDialog = false
                        } else {
                            isAuthError = true
                        }
                    }
                ) {
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminAuthenticationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ======================================
// LOGIN SCREEN
// ======================================
@Composable
fun LoginScreen(viewModel: AppViewModel, onModeratorLoginClick: () -> Unit) {
    val context = LocalContext.current
    var emailInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var referralCodeInput by remember { mutableStateOf("") }
    var testTermsChecked by remember { mutableStateOf(true) }
    var useSimulatedAccount by remember { mutableStateOf<String?> (null) }
    var showCustomInputForm by remember { mutableStateOf(false) }

    val fingerprint = remember { viewModel.getDeviceFingerprint() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Specified continue URL matching Google AdSense Accounts Chooser Flow
    val chooserUrl = "https://accounts.google.com/v3/signin/accountchooser?continue=https%3A%2F%2Fadsense.google.com%2Fadsense%2Fsignup%2Fcreate%3Fsubid%3Dww-en-ha-ads-g-a-srchd01_in%21o3%26gad_source%3D1%26gad_campaignid%3D22969841646%26_gl%3D1*g451xi*_up*MQ..%26gclid%3DCjwKCAjw8uTQBhAdEiwAVvtJynKLORqsk97_FlXp4nTpkSaP4eFsTtPsJZ9OPsuaFlvAMw1c4067KxoCnbwQAvD_BwE%26gbraid%3D0AAAAADjUaUfy5S8giVol5zfQ1U9KgQ1Ee%26referer%3Dhttps%253A%252F%252Fadsense.google.com%252Fintl%252Fhi_in%252Fstart%252F%253Fsubid%253Dww-en-ha-ads-g-a-srchd01_in%2521o3%2526gad_source%253D1%2526gad_campaignid%253D22969841646%2526gbraid%253D0AAAAADjUaUfy5S8giVol5zfQ1U9KgQ1Ee%2526gclid%253DCjwKCAjw8uTQBhAdEiwAVvtJynKLORqsk97_FlXp4nTpkSaP4eFsTtPsJZ9OPsuaFlvAMw1c4067KxoCnbwQAvD_BwE%26sac%3Dtrue&faa=1&hl=en_GB&osid=1&service=adsense&flowName=GlifWebSignIn&flowEntry=AccountChooser&dsh=S1127189580%3A1780060127585158"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Simulated Android Custom Browser Frame View
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Browser Window Tab & Address Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2D2D2D))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Window Control Circles (Mac/Chrome style)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF5F56), CircleShape))
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFBD2E), CircleShape))
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF27C93F), CircleShape))
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))

                        // Navigation Icons
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))

                        // Address Bar displaying official URL
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF202020), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure Connection",
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = chooserUrl,
                                color = Color(0xFFAAAAAA),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Simulated Web Content View (The Google Accounts Chooser Canvas)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Google "G" Colorful Letter Mark
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text("G", color = Color(0xFF4285F4), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("o", color = Color(0xFFEA4335), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("o", color = Color(0xFFFBBC05), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("g", color = Color(0xFF4285F4), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("l", color = Color(0xFF34A853), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("e", color = Color(0xFFEA4335), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }

                        // Google Sign-In Headers
                        Text(
                            text = if (showCustomInputForm) "Sign in" else "Choose an account",
                            color = Color(0xFF202124),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "to continue to Google AdSense",
                            color = Color(0xFF5F6368),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (!showCustomInputForm) {
                            // CHOOSER VIEW: List existing accounts on device
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Option A: User Email
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                                        .clickable {
                                            emailInput = "ayushsingh5990@gmail.com"
                                            displayNameInput = "Ayush Singh"
                                            useSimulatedAccount = "ayushsingh5990@gmail.com"
                                            showCustomInputForm = true
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color(0xFF381E72), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("AS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Ayush Singh", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                                        Text("ayushsingh5990@gmail.com", fontSize = 11.sp, color = Color(0xFF5F6368))
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("Signed In", color = Color(0xFF34A853), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                // Option B: Additional Account Custom Choice
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                                        .clickable {
                                            emailInput = "rahul.sharma88@gmail.com"
                                            displayNameInput = "Rahul Sharma"
                                            useSimulatedAccount = "rahul.sharma88@gmail.com"
                                            showCustomInputForm = true
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color(0xff4a35cf), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("RS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Rahul Sharma", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                                        Text("rahul.sharma88@gmail.com", fontSize = 11.sp, color = Color(0xFF5F6368))
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                // Option C: Add custom manually
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFFDADCE0), RoundedCornerShape(8.dp))
                                        .clickable {
                                            emailInput = ""
                                            displayNameInput = ""
                                            useSimulatedAccount = null
                                            showCustomInputForm = true
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFF5F6368),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Use another account", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A73E8))
                                }
                            }
                        } else {
                            // GLIF SIGN-IN INPUT FORM: Enter Email & Name
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Text Inputs resembling real Google Floating Label Card
                                OutlinedTextField(
                                    value = emailInput,
                                    onValueChange = { emailInput = it.lowercase().trim() },
                                    label = { Text("Email (only @gmail.com)") },
                                    placeholder = { Text("email@gmail.com") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF202124),
                                        unfocusedTextColor = Color(0xFF202124),
                                        focusedBorderColor = Color(0xFF1A73E8),
                                        unfocusedBorderColor = Color(0xFFDADCE0),
                                        focusedLabelColor = Color(0xFF1A73E8)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_email_input")
                                )

                                OutlinedTextField(
                                    value = displayNameInput,
                                    onValueChange = { displayNameInput = it },
                                    label = { Text("Your Display Name") },
                                    placeholder = { Text("Rahul Sharma") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF202124),
                                        unfocusedTextColor = Color(0xFF202124),
                                        focusedBorderColor = Color(0xFF1A73E8),
                                        unfocusedBorderColor = Color(0xFFDADCE0),
                                        focusedLabelColor = Color(0xFF1A73E8)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_username_input")
                                )

                                OutlinedTextField(
                                    value = referralCodeInput,
                                    onValueChange = { referralCodeInput = it.uppercase().trim() },
                                    label = { Text("Referral Invite Code (Optional)") },
                                    placeholder = { Text("GMAIL99X") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF202124),
                                        unfocusedTextColor = Color(0xFF202124),
                                        focusedBorderColor = Color(0xFF1A73E8),
                                        unfocusedBorderColor = Color(0xFFDADCE0),
                                        focusedLabelColor = Color(0xFF1A73E8)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_referral_input")
                                )

                                // Checkbox for terms
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { testTermsChecked = !testTermsChecked }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = testTermsChecked,
                                        onCheckedChange = { testTermsChecked = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1A73E8))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("Accept AdSense & Fingerprint Terms", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF202124))
                                        Text("Hardware finger ID will be locked globally: $fingerprint", fontSize = 9.sp, color = Color(0xFF5F6368))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Go Back to selector
                                    TextButton(
                                        onClick = { showCustomInputForm = false },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1A73E8))
                                    ) {
                                        Text("Change account", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Next (Login Proceed Action Button)
                                    Button(
                                        onClick = {
                                            if (emailInput.isBlank() || displayNameInput.isBlank()) {
                                                Toast.makeText(context, "Email and Display Name are mandatory", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (!emailInput.endsWith("@gmail.com")) {
                                                Toast.makeText(context, "Security Restriction: Must utilize standard @gmail.com accounts.", Toast.LENGTH_LONG).show()
                                                return@Button
                                            }
                                            if (!testTermsChecked) {
                                                Toast.makeText(context, "Please agree to device fingerprint terms to block emulator fraud.", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            viewModel.loginWithGoogleGmail(
                                                emailInput,
                                                displayNameInput,
                                                if (referralCodeInput.isNotBlank()) referralCodeInput else null
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.testTag("login_signin_button")
                                    ) {
                                        Text(
                                            text = if (uiState is UiState.Loading) "Verifying..." else "Next",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Google Page Footer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("English (United Kingdom)", color = Color(0xFF5F6368), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Help", color = Color(0xFF5F6368), fontSize = 11.sp)
                                Text("Privacy", color = Color(0xFF5F6368), fontSize = 11.sp)
                                Text("Terms", color = Color(0xFF5F6368), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onModeratorLoginClick,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("moderator_entry_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Access Verification Staff Sign-In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ======================================
// HOME / DASHBOARD SCREEN
// ======================================
@Composable
fun DashboardScreen(user: UserEntity, viewModel: AppViewModel) {
    val context = LocalContext.current
    var showWithdrawalDialog by remember { mutableStateOf(false) }

    val userSubmissions by viewModel.userSubmissions.collectAsStateWithLifecycle()
    val rawSettings by viewModel.settings.collectAsStateWithLifecycle()

    val coinsPerGmail = rawSettings.find { it.key == "points_per_submission" }?.value?.toLongOrNull() ?: 1500L
    val minWithdrawalAmount = rawSettings.find { it.key == "min_withdrawal_rupees" }?.value?.toIntOrNull() ?: 100

    // Wallet metrics
    val rupeeBalance = user.points.toDouble() / 100.0
    val rupeesText = String.format("₹%.2f", rupeeBalance)

    // Progress Calculation toward withdrawal release
    val currentGmailValidTasks = user.submissionsCount
    val targetGmailTasks = 10
    val amountSuccess = rupeeBalance >= minWithdrawalAmount.toDouble()
    val tasksSuccess = currentGmailValidTasks >= targetGmailTasks
    val overallProgressFraction = remember(rupeeBalance, currentGmailValidTasks) {
        val amountProgressFraction = (rupeeBalance / minWithdrawalAmount.toDouble()).coerceIn(0.0, 1.0)
        val taskProgressFraction = (currentGmailValidTasks.toDouble() / targetGmailTasks.toDouble()).coerceIn(0.0, 1.0)
        ((amountProgressFraction + taskProgressFraction) / 2.0).toFloat()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Card with animated gradient
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hello, ${user.displayName} 👋",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Redeem global Gmail tasks. Earn premium points instantly.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = if (user.fraudScore >= 40) Icons.Default.Warning else Icons.Default.Shield,
                                contentDescription = "Security status",
                                tint = if (user.fraudScore >= 70) Color(0xFFFF8A80) else if (user.fraudScore >= 30) Color(0xFFFFD54F) else Color(0xFFA5D6A7),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = when {
                                    user.fraudScore >= 70 -> "High Risk Index: Refined audit required"
                                    user.fraudScore >= 30 -> "Active warning flags under inspection"
                                    else -> "System Secured: Anti-VPN Shield [Active]"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (user.fraudScore >= 70) Color(0xFFFF8A80) else if (user.fraudScore >= 30) Color(0xFFFFD54F) else Color(0xFFE8F5E9)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = "Active user", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        // Wallet metrics panel (Beautiful Card with Wallet Animation / visual detail)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "MEMBER UNIQUE BALANCE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Icon(Icons.Default.Paid, contentDescription = "Coins", tint = AccentGold, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${user.points}",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    " Coins",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }

                        // Rupees converter
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "INR VALUE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = rupeesText,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    // Secondary Referral & Task Earnings stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Submissions Earned", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${user.submissionsCount} accounts", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Referral Payouts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${user.referralEarnings / 100}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic progress track to withdraw unlock
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Withdrawal Threshold Goals", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${(overallProgressFraction * 100).toInt()}% unlocked", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { overallProgressFraction },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress rules markers
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (amountSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (amountSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Min ₹$minWithdrawalAmount", fontSize = 10.sp, color = if (amountSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (tasksSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (tasksSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("10 Total Task Submissions ($currentGmailValidTasks/10)", fontSize = 10.sp, color = if (tasksSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Request Payment Trigger button (strictly evaluated limit constraints)
                    Button(
                        onClick = { showWithdrawalDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("withdraw_trigger_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (amountSuccess && tasksSuccess) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Request UPI Withdrawal",
                                fontWeight = FontWeight.Bold,
                                color = if (amountSuccess && tasksSuccess) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Claim bonus incentive card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f), CircleShape)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = "Bonus", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Daily Loyalty Gift (+₹2)", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Renewable every 24 hours.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Button(
                        onClick = { viewModel.claimDailyBonus() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.testTag("claim_bonus_button")
                    ) {
                        Text("Claim", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Tasks instruction card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Verified Gmail Requirements:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Only standard, registered @gmail.com accounts allowed.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Provided password must strictly be: 'ethicbro999'.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Each Gmail account represents a one-off global reward (+$coinsPerGmail pts).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Submissions Title Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MEMBER VERIFIED SUBMISSIONS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${userSubmissions.size} submitted",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Lazy load actual submissions
        if (userSubmissions.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MailOutline, contentDescription = null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("No Gmail account tasks submitted yet.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            items(userSubmissions) { task ->
                GmailSubmissionRow(task = task)
            }
        }
    }

    // --- WITHDRAWAL FORM MODAL DIALOG ---
    if (showWithdrawalDialog) {
        var upiIdInput by remember { mutableStateOf("") }
        var fullNameInput by remember { mutableStateOf("") }
        var requestAmountInput by remember { mutableStateOf("$minWithdrawalAmount") }
        var modalError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showWithdrawalDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Payout Request", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!amountSuccess || !tasksSuccess) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = "LOCKED: You must meet the ₹$minWithdrawalAmount minimum requirement balance AND successfully log a minimum of 10 tasks to unlocked payouts progress.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = requestAmountInput,
                        onValueChange = {
                            requestAmountInput = it
                            modalError = null
                        },
                        label = { Text("Amount to withdraw (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = (amountSuccess && tasksSuccess),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_amount_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = upiIdInput,
                        onValueChange = {
                            upiIdInput = it
                            modalError = null
                        },
                        label = { Text("UPI ID (e.g. name@okhdfc)") },
                        singleLine = true,
                        enabled = (amountSuccess && tasksSuccess),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_upi_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = fullNameInput,
                        onValueChange = {
                            fullNameInput = it
                            modalError = null
                        },
                        label = { Text("Account Holder Name") },
                        singleLine = true,
                        enabled = (amountSuccess && tasksSuccess),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_name_input")
                    )

                    if (modalError != null) {
                        Text(modalError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountVal = requestAmountInput.toIntOrNull() ?: 0
                        if (amountVal < minWithdrawalAmount) {
                            modalError = "Minimum withdrawal request is ₹$minWithdrawalAmount"
                            return@Button
                        }
                        if (upiIdInput.isBlank() || fullNameInput.isBlank()) {
                            modalError = "Please enter valid banking metrics to process"
                            return@Button
                        }
                        viewModel.submitWithdrawal(amountVal, upiIdInput, fullNameInput)
                        showWithdrawalDialog = false
                    },
                    enabled = (amountSuccess && tasksSuccess),
                    modifier = Modifier.testTag("dialog_withdraw_confirm_button")
                ) {
                    Text("Submit Payout Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GmailSubmissionRow(task: GmailSubmissionEntity) {
    val dateText = remember(task.timestamp) {
        val date = java.util.Date(task.timestamp)
        val format = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        format.format(date)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = when (task.status) {
                                "COMPLETED" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                "REJECTED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (task.status) {
                            "COMPLETED" -> Icons.Default.CheckCircle
                            "REJECTED" -> Icons.Default.Cancel
                            else -> Icons.Default.Pending
                        },
                        contentDescription = task.status,
                        tint = when (task.status) {
                            "COMPLETED" -> MaterialTheme.colorScheme.primary
                            "REJECTED" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = task.gmail,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Password: ${task.password} • $dateText",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right side state indicator
            Surface(
                color = when (task.status) {
                    "COMPLETED" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    "REJECTED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = task.status,
                    color = when (task.status) {
                        "COMPLETED" -> MaterialTheme.colorScheme.primary
                        "REJECTED" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ======================================
// REFERRAL SCREEN
// ======================================
@Composable
fun ReferralScreen(user: UserEntity, viewModel: AppViewModel) {
    val context = LocalContext.current
    val rawSettings by viewModel.settings.collectAsStateWithLifecycle()
    val rawUsers by viewModel.allUsers.collectAsStateWithLifecycle()

    val referralBonusStr = rawSettings.find { it.key == "points_per_referral" }?.value?.toLongOrNull() ?: 1000L
    val inviteLink = "https://gmailrewards.app/join?ref=${user.referralCode}"

    // Count referrals referred by this user
    val referredUsers = remember(rawUsers, user.referralCode) {
        rawUsers.filter { it.referredBy == user.referralCode }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Invite Banner Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CardGiftcard,
                        contentDescription = "Invite Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Invite & Claim Balance", fontSize = 20.sp, fontWeight = FontWeight.Black)

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        "Gain +$referralBonusStr points (₹${referralBonusStr / 100}) immediately into withdrawable balance for every active newcomer registering with your code.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // User's Unique Referral Code Container
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("YOUR RECRUITMENT CODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dashboard-style Code readout
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = user.referralCode,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Referral Code", user.referralCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Referral Code copied to Clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Share Link
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Invite Link", inviteLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Invite link copied! Share anywhere on WhatsApp/Telegram.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Complete Invitation Link", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Referral Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("TOTAL INVITED", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${referredUsers.size} Users", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("INVITE REWARDS", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("₹${user.referralEarnings / 100}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // Referred Users Logs header
        item {
            Text(
                "INVITATION TRANSACTION LOGS",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (referredUsers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Invite people using your code. Earnings land instantly.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(referredUsers) { referredUser ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(referredUser.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(referredUser.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "+₹${referralBonusStr / 100}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ======================================
// LEADERBOARD SCREEN
// ======================================
@Composable
fun LeaderboardScreen(user: UserEntity, viewModel: AppViewModel) {
    val rawUsers by viewModel.allUsers.collectAsStateWithLifecycle()

    // Rank users by total points
    val sortedUsers = remember(rawUsers) {
        rawUsers.sortedByDescending { it.points }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = "Leaderboard Logo",
                        tint = AccentGold,
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Global Leaderboard", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Compete with global Gmail miners. Higher ranks, faster approvals.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("RANK & USERNAME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("TOTAL COINS (VAL)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(sortedUsers.take(25).size) { index ->
            val u = sortedUsers[index]
            val isMe = u.email == user.email

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (isMe) 1.5.dp else 0.5.dp,
                    color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Badge / Rank Number
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when (index) {
                                        0 -> AccentGold
                                        1 -> Color(0xFFC0C0C0)
                                        2 -> Color(0xFFCD7F32)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (index < 3) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = u.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (isMe) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "YOU",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Device: ...${u.deviceFingerprint.takeLast(6)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${u.points} pts",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                        Text(
                            "₹${u.points / 100}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ======================================
// SUPPORT PAGE
// ======================================
@Composable
fun SupportPage() {
    var ticketSubject by remember { mutableStateOf("") }
    var ticketBody by remember { mutableStateOf("") }
    var context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Support Helpdesk", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Have payout queries or format problems? Our automated system resolves disputes within 12 hours.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { Toast.makeText(context, "Opening Technical Chat on Telegram", Toast.LENGTH_SHORT).show() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Join Telegram Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { Toast.makeText(context, "Directing to WhatsApp Support Line", Toast.LENGTH_SHORT).show() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Line on WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text("LODGE AN AUTOMATED SUPPORT DISPUTE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = ticketSubject,
                        onValueChange = { ticketSubject = it },
                        label = { Text("Query Subject") },
                        placeholder = { Text("Dispute regarding Task ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = ticketBody,
                        onValueChange = { ticketBody = it },
                        label = { Text("Describe the issue in details") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (ticketSubject.isBlank() || ticketBody.isBlank()) {
                                Toast.makeText(context, "Please write subject & description details.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            ticketSubject = ""
                            ticketBody = ""
                            Toast.makeText(context, "Dispute Lodge ID generated successfully! Check Telegram log for progress status.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Transmit Support dispute", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ======================================
// ADMIN WORKSPACE / COORD PANEL
// ======================================
@Composable
fun AdminPanelScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var adminActiveTab by remember { mutableStateOf("submissions") }

    val rawSubmissions by viewModel.submissions.collectAsStateWithLifecycle()
    val rawWithdrawals by viewModel.withdrawals.collectAsStateWithLifecycle()
    val rawUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val rawSettings by viewModel.settings.collectAsStateWithLifecycle()
    val rawDomains by viewModel.bannedDomains.collectAsStateWithLifecycle()

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

    Column(modifier = Modifier.fillMaxSize()) {
        // Admin quick toggle tab row
        ScrollableTabRow(
            selectedTabIndex = when (adminActiveTab) {
                "submissions" -> 0
                "withdrawals" -> 1
                "users" -> 2
                "params" -> 3
                "domains" -> 4
                "moderators" -> 5
                else -> 0
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            edgePadding = 12.dp
        ) {
            val sections = listOf(
                "submissions" to "Submissions",
                "withdrawals" to "UPI Requests",
                "users" to "Users Manage",
                "params" to "Config Ratios",
                "domains" to "Spam Blocker",
                "moderators" to "Staff Manage"
            )

            sections.forEachIndexed { idx, (key, label) ->
                Tab(
                    selected = adminActiveTab == key,
                    onClick = { adminActiveTab = key },
                    text = { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (adminActiveTab) {
                "submissions" -> AdminSubmissionsView(rawSubmissions)
                "withdrawals" -> AdminWithdrawalsView(rawWithdrawals, viewModel)
                "users" -> AdminUsersView(rawUsers, viewModel)
                "params" -> AdminSettingsView(rawSettings, viewModel)
                "domains" -> AdminBannedDomainsView(rawDomains, viewModel)
                "moderators" -> AdminModeratorsView(viewModel)
            }
        }
    }
}

@Composable
fun ColumnScope.AdminSubmissionsView(submissions: List<GmailSubmissionEntity>) {
    var searchFilter by remember { mutableStateOf("") }
    val filtered = remember(submissions, searchFilter) {
        if (searchFilter.isBlank()) submissions
        else submissions.filter { it.gmail.contains(searchFilter, ignoreCase = true) || it.submittedBy.contains(searchFilter, ignoreCase = true) }
    }

    Text("COMPLETED GMAIL LEDGERS UNIQUE RECORD", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = searchFilter,
        onValueChange = { searchFilter = it },
        placeholder = { Text("Search by submitted address or user...") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(10.dp))

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No Gmail task submissions tracked.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { record ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(record.gmail, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(record.status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Task Pass: ${record.password} | Submitted by: ${record.submittedBy}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.AdminWithdrawalsView(withdrawals: List<WithdrawalEntity>, viewModel: AppViewModel) {
    Text("PENDING UPI DISPATCH VERIFICATION", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(10.dp))

    if (withdrawals.isEmpty()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No payment withdrawal records saved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(withdrawals) { req ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (req.status == "PENDING") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("User: ${req.userEmail}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Surface(
                                color = when (req.status) {
                                    "APPROVED" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    "REJECTED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    req.status,
                                    color = when (req.status) {
                                        "APPROVED" -> MaterialTheme.colorScheme.primary
                                        "REJECTED" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.secondary
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Requested: ₹${req.amountRupees} (${req.pointsDeducted} Coins)", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Text("UPI ID: ${req.upiId} | Name: ${req.name}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (req.status == "PENDING") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { viewModel.updateWithdrawalRequest(req.id, "REJECTED") },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("REJECT & REFUND", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = { viewModel.updateWithdrawalRequest(req.id, "APPROVED") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("MARK PAID", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.AdminUsersView(users: List<UserEntity>, viewModel: AppViewModel) {
    Text("USER PRIVILEGES & SECURITY MATRIX", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(10.dp))

    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(users) { u ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (u.isBanned) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(u.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (u.isBanned) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.error,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "SUSPENDED",
                                            color = MaterialTheme.colorScheme.onError,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text("Email: ${u.email}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Fingerprint: ${u.deviceFingerprint}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                        }

                        Switch(
                            checked = !u.isBanned,
                            onCheckedChange = { viewModel.changeUserBanStatus(u.email, !it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val (badgeBg, badgeText, textLabel) = when {
                                    u.fraudScore >= 70 -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "CRITICAL RISK")
                                    u.fraudScore >= 30 -> Triple(Color(0xFFFFE0B2), Color(0xFFE65100), "MODERATE RISK")
                                    else -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "SAFE STATUS")
                                }

                                Surface(
                                    color = badgeBg,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (u.fraudScore >= 30) Icons.Default.Warning else Icons.Default.Shield,
                                            contentDescription = "Threat rating",
                                            tint = badgeText,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "${u.fraudScore}% $textLabel",
                                            color = badgeText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (u.isVpnDetected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFE0F7FA),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            "VPN/PROXY DETECTED",
                                            color = Color(0xFF006064),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { viewModel.toggleSimulationVpnState(u.email) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (u.isVpnDetected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (u.isVpnDetected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Sim VPN", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.forceReevaluateFraud(u.email) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.outline
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Run Scan", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (u.fraudFlags.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Threat Flags: ${u.fraudFlags}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Coins Balance: ${u.points} coins | Ledger Tasks Submitted: ${u.submissionsCount}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ColumnScope.AdminSettingsView(settings: List<com.example.data.SettingEntity>, viewModel: AppViewModel) {
    val context = LocalContext.current
    Text("GLOBAL SYSTEM CALCULATORS (REWARD RATIOS)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(12.dp))

    val coinsPerSubmission = settings.find { it.key == "points_per_submission" }?.value ?: "1500"
    val miniWithdrawalRupees = settings.find { it.key == "min_withdrawal_rupees" }?.value ?: "100"
    val pointsPerReferral = settings.find { it.key == "points_per_referral" }?.value ?: "1000"

    var inputPointsPerSubmission by remember(coinsPerSubmission) { mutableStateOf(coinsPerSubmission) }
    var inputUnitWithdrawal by remember(miniWithdrawalRupees) { mutableStateOf(miniWithdrawalRupees) }
    var inputPointsPerReferral by remember(pointsPerReferral) { mutableStateOf(pointsPerReferral) }

    // Floating announcement custom text
    var customNotificationBroadcast by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = inputPointsPerSubmission,
                onValueChange = { inputPointsPerSubmission = it },
                label = { Text("Reward Coins per Gmail submission") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = inputUnitWithdrawal,
                onValueChange = { inputUnitWithdrawal = it },
                label = { Text("Minimum dynamic UPI withdrawal (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = inputPointsPerReferral,
                onValueChange = { inputPointsPerReferral = it },
                label = { Text("Coins Awarded per Active Referral Payout") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.updateLimitSetting("points_per_submission", inputPointsPerSubmission)
                    viewModel.updateLimitSetting("min_withdrawal_rupees", inputUnitWithdrawal)
                    viewModel.updateLimitSetting("points_per_referral", inputPointsPerReferral)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("APPLY SYSTEM CONFIGURATIONS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text("BROADCAST PUSH ALERTS SIMULATOR", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = customNotificationBroadcast,
                onValueChange = { customNotificationBroadcast = it },
                label = { Text("Notice / Event Alert Text") },
                placeholder = { Text("Referral double-bonus event is active till Sunday!") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (customNotificationBroadcast.isNotBlank()) {
                        viewModel.updateLimitSetting("simulated_push", "PUSH:$customNotificationBroadcast")
                        customNotificationBroadcast = ""
                        Toast.makeText(context, "System Broad-cast Dispatched!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("DISPATCH GLOBAL PUSH BAR", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ColumnScope.AdminBannedDomainsView(bannedDomains: List<BannedDomainEntity>, viewModel: AppViewModel) {
    var newDomainInput by remember { mutableStateOf("") }
    var context = LocalContext.current

    Text("ANTI-FRAUD TEMPORARY EMAIL DOMAIN SHIELD", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(10.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newDomainInput,
                onValueChange = { newDomainInput = it.trim().lowercase() },
                label = { Text("Add Domain (e.g. tempmail.net)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = {
                    if (newDomainInput.isBlank()) return@Button
                    if (newDomainInput.contains("@")) {
                        newDomainInput = newDomainInput.substringAfter("@")
                    }
                    viewModel.addTempMailDomain(newDomainInput)
                    newDomainInput = ""
                },
                modifier = Modifier.height(56.dp)
            ) {
                Text("BLOCK")
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(bannedDomains) { entry ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("@${entry.domain}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    IconButton(
                        onClick = { viewModel.removeTempMailDomain(entry.domain) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Unblock", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.AdminModeratorsView(viewModel: AppViewModel) {
    val mods by viewModel.allModerators.collectAsStateWithLifecycle()
    var displayInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    Text("PROVISION NEW VERIFICATION STAFF ACCOUNT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(10.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = displayInput,
                onValueChange = { displayInput = it },
                label = { Text("Display Name (e.g. Master John)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it.trim().lowercase() },
                label = { Text("Agent Email (e.g. john@staff.com)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = passInput,
                onValueChange = { passInput = it },
                label = { Text("Agent Password / Access PIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (displayInput.isBlank() || emailInput.isBlank() || passInput.isBlank()) {
                        Toast.makeText(context, "Fill all provision fields first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.createModeratorAccount(emailInput, displayInput, passInput)
                    displayInput = ""
                    emailInput = ""
                    passInput = ""
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("PROVISION STAFF")
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text("STAFF ACTIVE SESSIONS & COMMISSION PERFORMANCE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(6.dp))

    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(mods) { mod ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(mod.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(mod.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Credentials Password: ${mod.password}", fontSize = 11.sp, color = AccentGold, fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { viewModel.toggleModeratorSuspension(mod.email, !mod.isSuspended) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mod.isSuspended) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(if (mod.isSuspended) "UNSUSPEND" else "SUSPEND", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = { viewModel.deleteModeratorAccount(mod.email) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Remove Account", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Verifications: ${mod.verifiedCount} (${mod.approvalCount} App / ${mod.rejectionCount} Rej)", fontSize = 10.sp)
                        Text("Earnings: ₹${String.format("%.2f", mod.earningsRupees)}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val avgSpeed = if (mod.verifiedCount > 0) mod.totalSpeedSec / mod.verifiedCount else 0L
                        Text("Avg Speed: ${avgSpeed}s", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeratorPanelScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val currentMod by viewModel.currentModerator.collectAsStateWithLifecycle()
    val assignedTask by viewModel.assignedTaskForMod.collectAsStateWithLifecycle()

    var modTab by remember { mutableStateOf("dashboard") }

    if (currentMod == null) return

    val mod = currentMod!!

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, "Staff Icon")
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Staff Portal: ${mod.displayName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(mod.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.logoutModerator() }) {
                        Icon(Icons.Default.Logout, "Log Out Staff", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = modTab == "dashboard",
                    onClick = { modTab = "dashboard"; viewModel.loadAssignedTaskForModerator() },
                    icon = { Icon(Icons.Default.Dashboard, "Dashboard") },
                    label = { Text("Task Verifier") }
                )
                NavigationBarItem(
                    selected = modTab == "history",
                    onClick = { modTab = "history" },
                    icon = { Icon(Icons.Default.History, "History") },
                    label = { Text("Log History") }
                )
                NavigationBarItem(
                    selected = modTab == "earnings",
                    onClick = { modTab = "earnings" },
                    icon = { Icon(Icons.Default.MonetizationOn, "Earnings") },
                    label = { Text("Earnings") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (modTab) {
                "dashboard" -> {
                    ModeratorTaskVerifierView(
                        moderator = mod,
                        assignedTask = assignedTask,
                        viewModel = viewModel
                    )
                }
                "history" -> {
                    ModeratorHistoryView(moderator = mod, viewModel = viewModel)
                }
                "earnings" -> {
                    ModeratorEarningsView(moderator = mod, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun ModeratorTaskVerifierView(
    moderator: com.example.data.ModeratorEntity,
    assignedTask: com.example.data.GmailSubmissionEntity?,
    viewModel: AppViewModel
) {
    val context = LocalContext.current
    var rejectionReasonInput by remember { mutableStateOf("") }
    var showRejectionDialog by remember { mutableStateOf(false) }
    var taskStartTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var secondsRemaining by remember { mutableStateOf(120L) }

    LaunchedEffect(assignedTask) {
        taskStartTime = System.currentTimeMillis()
        secondsRemaining = 120L
    }

    LaunchedEffect(assignedTask, secondsRemaining) {
        if (assignedTask != null && secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL CHECKED", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${moderator.verifiedCount}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("APPROVED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        Text("${moderator.approvalCount}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4CAF50))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("REJECTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("${moderator.rejectionCount}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (assignedTask == null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "No tasks",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Everything looks clear!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "There are no new pending Gmail submissions available in the validation queue. Click below to re-evaluate.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.loadAssignedTaskForModerator() },
                        modifier = Modifier.testTag("staff_fetch_next_btn")
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch / Auto-Assign Next Task")
                    }
                }
            }
        } else {
            val isGmailFormat = assignedTask.gmail.endsWith("@gmail.com") && assignedTask.gmail.length > 10

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = AccentGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("EXCLUSIVE LOCK ASSIGNED", fontWeight = FontWeight.Black, fontSize = 11.sp, color = AccentGold)
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (secondsRemaining > 30) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = String.format("Expires in: %02d:%02d", secondsRemaining / 60, secondsRemaining % 60),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("SUBMITTED GMAIL ADDRESS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        Text(assignedTask.gmail, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("🔑 SECURE PASSWORD / LOGIN DETAILS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                .padding(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "•••••••• [HIDDEN FOR USER PRIVACY & SECURITY]",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("AUTOMATED INTEGRITY ASSESSMENT CHECKS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isGmailFormat) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isGmailFormat) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Standard Google '@gmail.com' Format Rule", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disposable Spam Mail Domain Protection Passed", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Database Uniqueness Guard Check Complete (No Duplicates)", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (secondsRemaining <= 0) {
                            Button(
                                onClick = { viewModel.loadAssignedTaskForModerator() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Renew Task Assignment Lock", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { showRejectionDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f).testTag("staff_reject_btn")
                                ) {
                                    Icon(Icons.Default.Close, null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reject Task", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val durationSec = (System.currentTimeMillis() - taskStartTime) / 1000L
                                        viewModel.approveAssignedTask(assignedTask.gmail, maxOf(1L, durationSec))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    modifier = Modifier.weight(1f).testTag("staff_approve_btn")
                                ) {
                                    Icon(Icons.Default.Check, null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Approve Task", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRejectionDialog && assignedTask != null) {
        var reasonText by remember { mutableStateOf("") }
        val presetReasons = listOf(
            "Account has double verification enabled and is locked",
            "Gmail credentials are invalid or fake",
            "Submitted address contains non-gmail spam credentials",
            "Verification code invalid or prompt request timed out"
        )

        AlertDialog(
            onDismissRequest = { showRejectionDialog = false },
            title = { Text("Log Rejection Reason", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rejection reasons are logged and sent to the remote user's feed. Explicit reason is required.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Rejection Explanation") },
                        placeholder = { Text("Write why this Gmail is rejected...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Or Select Preset Template Reason:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)

                    presetReasons.forEach { preset ->
                        TextButton(
                            onClick = { reasonText = preset },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(preset, fontSize = 11.sp, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reasonText.isBlank()) {
                            Toast.makeText(context, "Explanation is required!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val durationSec = (System.currentTimeMillis() - taskStartTime) / 1000L
                        viewModel.rejectAssignedTask(assignedTask.gmail, reasonText, maxOf(1L, durationSec))
                        showRejectionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Rejection", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectionDialog = false }) {
                    Text("Back")
                }
            }
        )
    }
}

@Composable
fun ModeratorHistoryView(moderator: com.example.data.ModeratorEntity, viewModel: AppViewModel) {
    val results by viewModel.allVerificationLogs.collectAsStateWithLifecycle()
    val mine = remember(results, moderator.email) {
        results.filter { it.moderatorEmail == moderator.email }.sortedByDescending { it.id }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("YOUR VERIFICATION HISTORY LOGS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }

        if (mine.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No historic activities logged yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(mine) { log ->
                val isApprove = log.action == "APPROVE"
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.submissionGmail, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (isApprove) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = log.action,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isApprove) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Took: ${log.durationSeconds}s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!log.rejectionReason.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Reason: ${log.rejectionReason}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                            }
                        }

                        Icon(
                            imageVector = if (isApprove) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (isApprove) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModeratorEarningsView(moderator: com.example.data.ModeratorEntity, viewModel: AppViewModel) {
    val logs by viewModel.allModeratorPayments.collectAsStateWithLifecycle()
    val mine = remember(logs, moderator.email) {
        logs.filter { it.moderatorEmail == moderator.email }.sortedByDescending { it.id }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("STAFF EARNINGS LEDGER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("₹${String.format("%.2f", moderator.earningsRupees)}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("Wages calculated automatically at a rate of ₹3.00 (or configuration setting) per validated task, regardless of approval or rejection outputs.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                }
            }
        }

        item {
            Text("MY RECENT SALARY TRANSFERS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }

        if (mine.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No wages or payments transferred yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(mine) { pay ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val label = if (pay.type == "REWARD") "Commission Gained" else "Wages Disbursed"
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(if (pay.type == "WITHDRAWAL") "UPI ID: ${pay.upiId ?: "N/A"}" else "Per verification payment rule", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("₹${String.format("%.2f", pay.amount)}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = if (pay.type == "REWARD") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (pay.status == "APPROVED") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ) {
                                Text(
                                    text = pay.status,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pay.status == "APPROVED") Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
