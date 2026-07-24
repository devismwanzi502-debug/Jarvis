package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AutomationRule
import com.example.ui.JarvisUiState
import com.example.ui.JarvisViewModel
import com.example.ui.VoiceState
import com.example.ui.components.ArcReactorVisualizer
import com.example.ui.theme.*
import java.util.*

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

@Composable
fun Modifier.movingRainbowBorder(
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "RainbowBorder")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RainbowOffset"
    )

    val rainbowBrush = Brush.linearGradient(
        colors = RainbowColors + RainbowColors,
        start = Offset(offset, offset),
        end = Offset(offset + 500f, offset + 500f)
    )

    return this.border(width = strokeWidth, brush = rainbowBrush, shape = shape)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: JarvisViewModel,
    uiState: JarvisUiState
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Chat/Voice, 1: Automations, 2: System Logs
    var textInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.processUserCommand(spokenText)
            }
        }
        viewModel.setVoiceState(VoiceState.IDLE)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .movingRainbowBorder(strokeWidth = 3.dp, shape = RoundedCornerShape(0.dp))
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "JARVIS Logo",
                                tint = CyberBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "JARVIS-X",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "AUTONOMOUS AGENT ACTIVE",
                                    color = CyberBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = "Accessibility Permission",
                                tint = if (uiState.isAccessibilityEnabled) StatusGreen else StatusRed
                            )
                        }
                        IconButton(
                            onClick = {
                                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notification Listener Permission",
                                tint = if (uiState.isNotificationListenerEnabled) StatusGreen else StatusRed
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = TextPrimary
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Voice Agent") },
                        label = { Text("Command Engine") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            indicatorColor = CyberBlue
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = "Automations") },
                        label = { Text("Automations") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            indicatorColor = CyberBlue
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = "Execution Logs") },
                        label = { Text("Agent Logs") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            indicatorColor = CyberBlue
                        )
                    )
                }
            },
            containerColor = DarkBackground
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTab) {
                    0 -> CommandAgentView(
                        uiState = uiState,
                        textInput = textInput,
                        onTextInputChange = { textInput = it },
                        onSend = {
                            if (textInput.isNotBlank()) {
                                viewModel.processUserCommand(textInput)
                                textInput = ""
                            }
                        },
                        onStartVoice = {
                            viewModel.setVoiceState(VoiceState.LISTENING)
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS Listening...")
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                viewModel.setVoiceState(VoiceState.IDLE)
                            }
                        },
                        onQuickCommand = { cmd -> viewModel.processUserCommand(cmd) }
                    )
                    1 -> AutomationsView(
                        rules = uiState.rules,
                        onToggle = { viewModel.toggleRule(it) },
                        onDelete = { viewModel.deleteRule(it) }
                    )
                    2 -> LogsView(
                        logs = uiState.logs,
                        onClear = { viewModel.clearLogs() }
                    )
                }
            }
        }
    }
}

@Composable
fun CommandAgentView(
    uiState: JarvisUiState,
    textInput: String,
    onTextInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    onQuickCommand: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Arc Reactor Core Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(16.dp))
                .movingRainbowBorder(strokeWidth = 1.5.dp, shape = RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArcReactorVisualizer(
                    voiceState = uiState.voiceState,
                    size = 140.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (uiState.voiceState) {
                        VoiceState.LISTENING -> "LISTENING TO VOICE COMMAND..."
                        VoiceState.PROCESSING -> "COMPUTING ACTION PLAN..."
                        VoiceState.SPEAKING -> "JARVIS SPEAKING..."
                        VoiceState.IDLE -> "SYSTEM READY • AWAITING COMMAND"
                    },
                    color = when (uiState.voiceState) {
                        VoiceState.LISTENING -> StatusGreen
                        else -> CyberBlue
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Command Shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickChip("Open YouTube", onClick = { onQuickCommand("Open YouTube") })
            QuickChip("Reply DM auto", onClick = { onQuickCommand("Every time I receive an Instagram DM respond politely and tell them I'll reply later.") })
            QuickChip("Search Chrome", onClick = { onQuickCommand("Open Chrome and search best Android coding tutorials") })
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Conversation History
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false
        ) {
            items(uiState.messages) { msg ->
                MessageBubble(msg)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Control Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextInputChange,
                placeholder = { Text("Ask JARVIS or give command...", color = TextSecondary) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberBlue,
                    unfocusedBorderColor = DarkCard,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(48.dp)
                    .background(CyberBlue, CircleShape)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = DarkBackground
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onStartVoice,
                modifier = Modifier
                    .size(48.dp)
                    .background(StatusGreen, CircleShape)
                    .testTag("voice_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = DarkBackground
                )
            }
        }
    }
}

@Composable
fun QuickChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = DarkCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBlueVariant)
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun MessageBubble(msg: com.example.ui.MessageItem) {
    val align = if (msg.isUser) Alignment.End else Alignment.Start
    val bgColor = if (msg.isUser) DarkCard else DarkSurface
    val borderColor = if (msg.isUser) AccentOrange else CyberBlue

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = if (msg.isUser) "USER COMMAND" else "JARVIS-X",
                    color = if (msg.isUser) AccentOrange else CyberBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg.text,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                msg.plan?.let { plan ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ACTION: ${plan.actionType.name}",
                        color = StatusGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AutomationsView(
    rules: List<AutomationRule>,
    onToggle: (AutomationRule) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Active Autonomous Background Rules",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Continuous triggers monitored by JARVIS-X Background Engine",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No automations created yet.\nSay e.g. 'Every time I receive a WhatsApp message tell them I'm busy'",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(rules) { rule ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Trigger: ${rule.triggerType} | App: ${rule.triggerSourceApp ?: "All"}",
                                    color = CyberBlue,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Action Payload: ${rule.actionPayload ?: "N/A"}",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { onToggle(rule) },
                                colors = SwitchDefaults.colors(checkedThumbColor = StatusGreen)
                            )
                            IconButton(onClick = { onDelete(rule.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogsView(
    logs: List<com.example.data.ExecutionLog>,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Agent Execution Logs",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Real-time audit trail of actions taken by JARVIS-X",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            TextButton(onClick = onClear) {
                Text("Clear", color = StatusRed)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(logs) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = log.ruleOrCommandTitle,
                                color = CyberBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = log.status,
                                color = if (log.status == "SUCCESS") StatusGreen else StatusRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.description,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
