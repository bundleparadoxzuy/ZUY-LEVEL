package com.example

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen {
    SPLASH,
    LOGIN,
    DASHBOARD
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF071A35) // #071A35 Background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ZUY_SESSION", Context.MODE_PRIVATE) }
    
    // Check key authenticity session on SPLASH entry
    LaunchedEffect(Unit) {
        delay(3000) // Splash delay of 3 seconds
        val isLoggedIn = sharedPrefs.getBoolean("is_logged_in", false)
        if (isLoggedIn) {
            currentScreen = Screen.DASHBOARD
        } else {
            currentScreen = Screen.LOGIN
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(500)) + slideInHorizontally(
                initialOffsetX = { 600 },
                animationSpec = tween(500)
            ) togetherWith fadeOut(animationSpec = tween(400)) + slideOutHorizontally(
                targetOffsetX = { -600 },
                animationSpec = tween(400)
            )
        },
        label = "ScreenTransition"
    ) { targetState ->
        when (targetState) {
            Screen.SPLASH -> SplashScreen()
            Screen.LOGIN -> LoginScreen(onLoginSuccess = {
                sharedPrefs.edit().putBoolean("is_logged_in", true).apply()
                currentScreen = Screen.DASHBOARD
            })
            Screen.DASHBOARD -> DashboardScreen(onLogout = {
                // To allow testing, can optionally clear or keep
                // User requirement: "Tidak perlu login ulang selama aplikasi belum dihapus"
                // But we maintain a simple session config reset option if they want
            })
        }
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "SplashGlow")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = jeepTween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071A35)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Display Generated App Icon
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFF1E5EFF), Color(0xFF00FF66))
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_app_icon_1782173531890),
                    contentDescription = "ZUY LEVEL Emblem",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Text(
                text = "ZUY LEVEL",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 4.sp,
                    shadow = Shadow(
                        color = Color(0xFF1E5EFF),
                        blurRadius = 15f
                    )
                ),
                modifier = Modifier.testTag("app_logo_title")
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "SOCKS5 LEVELING SYSTEM",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = alphaAnim),
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp
            )
            
            Spacer(modifier = Modifier.height(50.dp))
            
            CircularProgressIndicator(
                color = Color(0xFF1E5EFF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Ease animations
fun <T> jeepTween(duration: Int): TweenSpec<T> = tween(duration, easing = LinearOutSlowInEasing)

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var loginKey by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071A35))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Custom Gaming Card Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2347)), // #0B2347
                border = BorderStroke(1.dp, Color(0xFF1E5EFF).copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ZUY LEVEL",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            textAlign = TextAlign.Center,
                            shadow = Shadow(
                                color = Color(0xFF1E5EFF),
                                blurRadius = 12f
                            )
                        )
                    )
                    
                    Text(
                        text = "SOCKS5 LEVELING SYSTEM",
                        fontSize = 11.sp,
                        color = Color(0xFF1E5EFF),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

                    // Login Outlined Input field
                    OutlinedTextField(
                        value = loginKey,
                        onValueChange = { loginKey = it },
                        label = { Text("License Key", color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1E5EFF),
                            unfocusedBorderColor = Color(0xFF1E5EFF).copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF071A35),
                            unfocusedContainerColor = Color(0xFF071A35)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_key_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Gaming button with glow shadow on tap
                    val infiniteTransition = rememberInfiniteTransition(label = "BtnGlow")
                    val pulseGlowShadow by infiniteTransition.animateFloat(
                        initialValue = 4f,
                        targetValue = 14f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "BtnPulse"
                    )

                    Button(
                        onClick = {
                            if (loginKey == "ZUYLEVELV1") {
                                onLoginSuccess()
                            } else {
                                showErrorDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E5EFF), // #1E5EFF
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_button")
                            .drawBehind {
                                drawRect(
                                    color = Color(0xFF1E5EFF).copy(alpha = 0.2f),
                                    size = size,
                                    alpha = 0.7f
                                )
                            },
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Text(
                            text = "ACTIVATE SYSTEM",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Beautiful Credit Panel box exactly as requested
            Card(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2347).copy(alpha = 0.7f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "━━━━━━━━━━━━━━━",
                        color = Color(0xFF1E5EFF).copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = "Developer : ZUY",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "TikTok : @codexxh",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable {
                            // Can add browser intent or clipboard copy
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Telegram : @ZuyFFID",
                        color = Color(0xFF1E5EFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = "━━━━━━━━━━━━━━━",
                        color = Color(0xFF1E5EFF).copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = Color(0xFFFF3B3B),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Access Denied",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Invalid License Key",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showErrorDialog = false },
                    modifier = Modifier.testTag("dismiss_dialog_button")
                ) {
                    Text("OK", color = Color(0xFF1E5EFF), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0B2347),
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }
}

@Composable
fun DashboardScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Server states from Manager flow
    val isOnline by SocksServerManager.isOnline.collectAsState()
    val totalMatches by SocksServerManager.totalMatches.collectAsState()
    val runtimeSeconds by SocksServerManager.runtimeSeconds.collectAsState()
    val logs by SocksServerManager.logs.collectAsState()
    
    // User unique UID generator
    val userUid = remember {
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (androidId.isNullOrEmpty()) "ZUY-81A2D9X" else "ZUY-" + androidId.take(8).uppercase()
        } catch (e: Exception) {
            "ZUY-99882AA"
        }
    }

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto Scroll Logs to bottom when changes occur
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            lazyListState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF071A35),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 20.dp, end = 20.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ZUY LEVEL",
                            style = TextStyle(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                shadow = Shadow(
                                    color = Color(0xFF1E5EFF).copy(alpha = 0.8f),
                                    blurRadius = 8f
                                )
                            )
                        )
                        Text(
                            text = "SOCKS5 LEVELING SYSTEM",
                            fontSize = 10.sp,
                            color = Color(0xFF1E5EFF),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    // Simple Live State Circle with breath animations
                    val infiniteTransition = rememberInfiniteTransition(label = "StatusBreath")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "LightPulse"
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOnline) Color(0xFF00FF66).copy(alpha = 0.15f) else Color(0xFFFF3B3B).copy(alpha = 0.15f),
                        border = BorderStroke(
                            1.dp,
                            if (isOnline) Color(0xFF00FF66).copy(alpha = 0.4f) else Color(0xFFFF3B3B).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isOnline) Color(0xFF00FF66).copy(alpha = pulseAlpha) 
                                        else Color(0xFFFF3B3B).copy(alpha = pulseAlpha)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOnline) "● ONLINE" else "● OFFLINE",
                                color = if (isOnline) Color(0xFF00FF66) else Color(0xFFFF3B3B), // Online #00FF66, Offline #FF3B3B
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Statistics Cards 2x2 Grid Layout
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "UID User",
                        value = userUid,
                        icon = Icons.Default.Person,
                        modifier = Modifier.weight(1f),
                        onValueClick = {
                            clipboardManager.setText(AnnotatedString(userUid))
                            SocksServerManager.addLog("UID Copied to clipboard!")
                        }
                    )
                    StatCard(
                        title = "Status SOCKS5",
                        value = if (isOnline) "Port 10221" else "Inactive",
                        icon = Icons.Default.Share,
                        modifier = Modifier.weight(1f),
                        highlightColor = if (isOnline) Color(0xFF00FF66) else Color.White.copy(alpha = 0.6f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "Total Match",
                        value = "$totalMatches",
                        icon = Icons.Default.Star,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Runtime",
                        value = formatRuntime(runtimeSeconds),
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Realtime Log Console Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2347)),
                border = BorderStroke(1.dp, Color(0xFF1E5EFF).copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Console",
                                tint = Color(0xFF1E5EFF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOG CONSOLE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        Text(
                            text = "CLEAR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E5EFF),
                            modifier = Modifier
                                .clickable {
                                    // Clear logs
                                }
                                .padding(4.dp)
                        )
                    }
                    
                    Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Console empty. Press START LEVEL to activate bot server.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF07182D), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            items(logs) { log ->
                                LogLine(log)
                            }
                        }
                    }
                }
            }

            // Action START/STOP Gaming Buttons Block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Button(
                    onClick = {
                        if (isOnline) {
                            SocksServerManager.stopServer()
                        } else {
                            SocksServerManager.startServer()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnline) Color(0xFFFF3B3B) else Color(0xFF1E5EFF),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("action_toggle_level")
                        .border(
                            BorderStroke(
                                1.dp,
                                if (isOnline) Color(0xFFFF7B7B).copy(alpha = 0.6f) 
                                else Color(0xFF00FF66).copy(alpha = 0.4f)
                            ),
                            RoundedCornerShape(14.dp)
                        ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = "Action",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOnline) "STOP LEVEL" else "START LEVEL",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    highlightColor: Color = Color.White,
    onValueClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2347)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E5EFF).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF1E5EFF),
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = value,
                    fontSize = 14.sp,
                    color = highlightColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = if (onValueClick != null) Modifier.clickable { onValueClick() } else Modifier
                )
            }
        }
    }
}

@Composable
fun LogLine(log: String) {
    val styledText = remember(log) {
        buildAnnotatedString {
            if (log.contains("Match Found!")) {
                withStyle(SpanStyle(color = Color(0xFF00FF66), fontWeight = FontWeight.Bold)) {
                    append(log)
                }
            } else if (log.contains("binding error") || log.contains("stuck")) {
                withStyle(SpanStyle(color = Color(0xFFFF3B3B), fontWeight = FontWeight.Bold)) {
                    append(log)
                }
            } else if (log.contains("captured") || log.contains("Restarting")) {
                withStyle(SpanStyle(color = Color(0xFF1E5EFF), fontWeight = FontWeight.Medium)) {
                    append(log)
                }
            } else {
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.85f))) {
                    append(log)
                }
            }
        }
    }
    
    Text(
        text = styledText,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 3.dp).fillMaxWidth()
    )
}

fun formatRuntime(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hrs, mins, secs)
}
