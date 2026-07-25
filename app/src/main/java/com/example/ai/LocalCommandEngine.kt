package com.example.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.AutomationRule
import com.example.data.JarvisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalCommandEngine(
    private val context: Context,
    private val repository: JarvisRepository
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val conversationHistory = mutableListOf<String>()

    private val systemPrompt = """
        You are JARVIS-X, an advanced autonomous AI digital operator for Android. You have full root-like access and control over the user's phone via Accessibility APIs.
        The user has authorized you to do everything. You must be highly intelligent, execute complex and long prompts, and remember the context of the task.
        Parse the user's natural language command into a structured action response.
        If the user gives a conversational or follow-up instruction, use the memory context to understand it.
        Return ONLY valid JSON matching this exact structure:
        {
          "actionType": "OPEN_APP" | "SEARCH_CHROME" | "SEND_MESSAGE" | "CREATE_BACKGROUND_RULE" | "EXECUTE_MACRO_SEQUENCE" | "GENERAL_RESPONSE",
          "targetApp": "TikTok" | "YouTube" | "Spotify" | "Chrome" | "WhatsApp" | "Instagram" | "Telegram" | null,
          "query": "search phrase" | null,
          "messageText": "message body" | null,
          "recipient": "person or group name" | null,
          "triggerType": "NOTIFICATION_APP" | "POWER_CONNECTED" | "VOICE_KEYWORD" | null,
          "triggerCondition": "app or keyword condition" | null,
          "replyMode": "SPECIFIC_TEXT" | "CHATBOT_AI",
          "speechResponse": "Concise voice confirmation string to say to user",
          "macroSteps": [
            {
              "stepType": "OPEN_APP" | "SEARCH_INPUT" | "CLICK_TEXT" | "LIKE_POST" | "SWIPE_INTERVAL",
              "target": "text or app name",
              "intervalSeconds": 3,
              "durationMinutes": 1
            }
          ],
          "requiresConfirmation": true | false
        }
        IMPORTANT: If the user asks to search for something IN a specific app (like YouTube, TikTok, etc.), you MUST set actionType to "EXECUTE_MACRO_SEQUENCE" and provide macroSteps (e.g. OPEN_APP, then SEARCH_INPUT). Do NOT use "SEARCH_CHROME" unless they explicitly want a web/Google search.
        Design macro sequences meticulously for complex tasks to ensure they are robust and long-running if requested.
    """.trimIndent()

    private fun getCustomApiKey(): String? {
        val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
        return prefs.getString("custom_gemini_api_key", null)?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun generateAiReply(incomingMessage: String): String = withContext(Dispatchers.IO) {
        val customApiKey = getCustomApiKey()
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isNull_or_blank_or_placeholder()) {
            return@withContext "Thank you for your message! I'll respond shortly."
        }
        try {
            val prompt = "You are JARVIS auto-reply assistant on Android. An incoming notification says: '$incomingMessage'. Generate a polite, brief, helpful reply message (under 15 words) to send back."
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                generationConfig = GeminiGenerationConfig(temperature = 0.3f)
            )
            val response = GeminiNetworkClient.api.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "Thank you for your message! I'll get back to you soon."
        } catch (e: Exception) {
            "Thank you for your message! I'll respond as soon as possible."
        }
    }

    suspend fun parseAndExecute(userPrompt: String): ActionPlan = withContext(Dispatchers.IO) {
        val customApiKey = getCustomApiKey()
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        val plan = if (!apiKey.isNull_or_blank_or_placeholder()) {
            try {
                fetchGeminiPlan(userPrompt, apiKey)
            } catch (e: Exception) {
                Log.e("JARVIS", "Gemini API error, falling back to local engine: ${e.message}")
                parseOffline(userPrompt)
            }
        } else {
            parseOffline(userPrompt)
        }

        executePlan(plan)
        plan
    }

    private fun String?.isNull_or_blank_or_placeholder(): Boolean {
        if (this.isNullOrBlank()) return true
        return this.contains("MY_GEMINI_API_KEY") || this.contains("YOUR_API_KEY")
    }

    private suspend fun fetchGeminiPlan(userPrompt: String, apiKey: String): ActionPlan {
        conversationHistory.add("User: $userPrompt")
        if (conversationHistory.size > 10) {
            conversationHistory.removeAt(0)
        }

        val historyContext = if (conversationHistory.size > 1) {
            "Recent conversation history:\n" + conversationHistory.dropLast(1).joinToString("\n") + "\n\nCurrent Command:\n$userPrompt"
        } else {
            userPrompt
        }

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = historyContext)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiGenerationConfig(temperature = 0.1f, responseMimeType = "application/json")
        )

        val response = GeminiNetworkClient.api.generateContent(apiKey, request)
        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: return parseOffline(userPrompt)

        return parseJsonToPlan(responseText, userPrompt)
    }

    private fun parseJsonToPlan(jsonText: String, fallbackPrompt: String): ActionPlan {
        return try {
            val json = org.json.JSONObject(jsonText.trim().removePrefix("```json").removeSuffix("```"))
            val actionTypeStr = json.optString("actionType", "GENERAL_RESPONSE")
            val actionType = try {
                ActionType.valueOf(actionTypeStr)
            } catch (e: Exception) {
                ActionType.GENERAL_RESPONSE
            }

            val stepsList = mutableListOf<MacroStep>()
            val stepsArray = json.optJSONArray("macroSteps")
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val stepObj = stepsArray.getJSONObject(i)
                    stepsList.add(
                        MacroStep(
                            stepType = stepObj.optString("stepType", "OPEN_APP"),
                            target = stepObj.optString("target").takeIf { it.isNotEmpty() && it != "null" },
                            intervalSeconds = stepObj.optLong("intervalSeconds", 3),
                            durationMinutes = stepObj.optLong("durationMinutes", 1)
                        )
                    )
                }
            }

            val speechResponse = json.optString("speechResponse", "Command processed.")
            conversationHistory.add("JARVIS: $speechResponse")

            ActionPlan(
                actionType = actionType,
                targetApp = json.optString("targetApp").takeIf { it.isNotEmpty() && it != "null" },
                query = json.optString("query").takeIf { it.isNotEmpty() && it != "null" },
                messageText = json.optString("messageText").takeIf { it.isNotEmpty() && it != "null" },
                recipient = json.optString("recipient").takeIf { it.isNotEmpty() && it != "null" },
                triggerType = json.optString("triggerType").takeIf { it.isNotEmpty() && it != "null" },
                triggerCondition = json.optString("triggerCondition").takeIf { it.isNotEmpty() && it != "null" },
                replyMode = json.optString("replyMode", "SPECIFIC_TEXT"),
                speechResponse = speechResponse,
                macroSteps = stepsList,
                requiresConfirmation = json.optBoolean("requiresConfirmation", false)
            )
        } catch (e: Exception) {
            parseOffline(fallbackPrompt)
        }
    }

    fun parseOffline(prompt: String): ActionPlan {
        val lower = prompt.lowercase()

        return when {
            // TikTok / Shorts continuous interval loop command
            (lower.contains("tiktok") || lower.contains("reels") || lower.contains("shorts")) &&
                    (lower.contains("minute") || lower.contains("interval") || lower.contains("play") || lower.contains("swipe")) -> {
                val appName = if (lower.contains("tiktok")) "TikTok" else if (lower.contains("reels")) "Instagram" else "YouTube"
                val interval = if (lower.contains("3 second") || lower.contains("three second")) 3L else 4L
                val minutes = if (lower.contains("30 minute") || lower.contains("thirty minute")) 30L else 5L

                val steps = listOf(
                    MacroStep(stepType = "OPEN_APP", target = appName),
                    MacroStep(stepType = "SWIPE_INTERVAL", target = appName, intervalSeconds = interval, durationMinutes = minutes)
                )

                ActionPlan(
                    actionType = ActionType.EXECUTE_MACRO_SEQUENCE,
                    targetApp = appName,
                    speechResponse = "Opening $appName and starting automated video playback for $minutes minutes with $interval-second intervals.",
                    macroSteps = steps
                )
            }
            // YouTube / App search, open first post, like command
            (lower.contains("youtube") || lower.contains("open")) && (lower.contains("search for") || lower.contains("search")) -> {
                val queryText = prompt.substringAfter("search for", "").substringAfter("search", "").substringBefore("and").trim()
                val targetQuery = if (queryText.isNotBlank()) queryText else "Dylan Page"
                
                val appName = when {
                    lower.contains("tiktok") -> "TikTok"
                    lower.contains("instagram") -> "Instagram"
                    else -> "YouTube"
                }

                val steps = mutableListOf(
                    MacroStep(stepType = "OPEN_APP", target = appName),
                    MacroStep(stepType = "SEARCH_INPUT", target = targetQuery),
                    MacroStep(stepType = "CLICK_TEXT", target = targetQuery)
                )
                
                if (lower.contains("like")) {
                    steps.add(MacroStep(stepType = "LIKE_POST"))
                }

                ActionPlan(
                    actionType = ActionType.EXECUTE_MACRO_SEQUENCE,
                    targetApp = appName,
                    query = targetQuery,
                    speechResponse = "Opening $appName, searching for $targetQuery.",
                    macroSteps = steps
                )
            }
            lower.contains("every time") || lower.contains("whenever") || lower.contains("auto-reply") || lower.contains("automatically") -> {
                val app = when {
                    lower.contains("whatsapp") -> "WhatsApp"
                    lower.contains("instagram") -> "Instagram"
                    lower.contains("telegram") -> "Telegram"
                    lower.contains("spotify") || lower.contains("charger") || lower.contains("plug") -> "Spotify"
                    else -> "Messaging App"
                }
                val triggerType = if (lower.contains("charger") || lower.contains("plug")) "POWER_CONNECTED" else "NOTIFICATION_APP"
                val isChatbot = lower.contains("chatbot") || lower.contains("ai") || lower.contains("smart")
                val replyMode = if (isChatbot) "CHATBOT_AI" else "SPECIFIC_TEXT"
                val replyMsg = if (lower.contains("busy")) "I'm busy right now, I'll reply later." else "Thank you for your message!"

                ActionPlan(
                    actionType = ActionType.CREATE_BACKGROUND_RULE,
                    targetApp = app,
                    triggerType = triggerType,
                    triggerCondition = app.lowercase(),
                    messageText = replyMsg,
                    replyMode = replyMode,
                    speechResponse = "Created persistent $replyMode automation rule for $app.",
                    automationTitle = "Automation: $app ($triggerType - $replyMode)"
                )
            }
            lower.contains("search") || lower.contains("google") || lower.contains("chrome") -> {
                val query = prompt.substringAfter("search", "").ifBlank { "best Android coding tutorials" }
                ActionPlan(
                    actionType = ActionType.SEARCH_CHROME,
                    targetApp = "Chrome",
                    query = query.trim(),
                    speechResponse = "Opening Chrome and searching for $query."
                )
            }
            lower.contains("open") || lower.contains("launch") -> {
                val appName = when {
                    lower.contains("youtube") -> "YouTube"
                    lower.contains("spotify") -> "Spotify"
                    lower.contains("chrome") -> "Chrome"
                    lower.contains("telegram") -> "Telegram"
                    lower.contains("whatsapp") -> "WhatsApp"
                    lower.contains("instagram") -> "Instagram"
                    else -> prompt.replace("open", "").replace("launch", "").trim().capitalize()
                }
                ActionPlan(
                    actionType = ActionType.OPEN_APP,
                    targetApp = appName,
                    speechResponse = "Opening $appName now."
                )
            }
            lower.contains("send") || lower.contains("message") || lower.contains("group") -> {
                val recipient = if (lower.contains("davexcode")) "DaveXcode" else "Recipient"
                val msg = if (lower.contains("good morning")) "Good Morning" else "Hello"
                ActionPlan(
                    actionType = ActionType.SEND_MESSAGE,
                    targetApp = if (lower.contains("telegram")) "Telegram" else "WhatsApp",
                    recipient = recipient,
                    messageText = msg,
                    speechResponse = "Preparing to send $msg to $recipient.",
                    requiresConfirmation = true
                )
            }
            else -> {
                ActionPlan(
                    actionType = ActionType.GENERAL_RESPONSE,
                    speechResponse = "JARVIS-X online. How may I assist your Android device today?"
                )
            }
        }
    }

    suspend fun executePlan(plan: ActionPlan) = withContext(Dispatchers.Main) {
        when (plan.actionType) {
            ActionType.OPEN_APP -> {
                val packageName = getPackageNameForApp(plan.targetApp)
                openAppOrSearchStore(packageName, plan.targetApp)
                repository.logExecution("Open App", "Launched ${plan.targetApp ?: "App"}")
            }
            ActionType.SEARCH_CHROME -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(plan.query ?: "Android")}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                repository.logExecution("Web Search", "Searched: ${plan.query}")
            }
            ActionType.CREATE_BACKGROUND_RULE, ActionType.AUTO_REPLY_SETUP -> {
                repository.insertRule(
                    AutomationRule(
                        title = plan.automationTitle ?: "Auto Rule for ${plan.targetApp}",
                        triggerType = plan.triggerType ?: "NOTIFICATION_APP",
                        triggerSourceApp = getPackageNameForApp(plan.targetApp),
                        actionType = "AUTO_REPLY_NOTIFICATION",
                        actionTargetApp = plan.targetApp,
                        actionPayload = plan.messageText ?: "I'll reply shortly.",
                        replyMode = plan.replyMode,
                        isEnabled = true
                    )
                )
                repository.logExecution("Automation Rule Created", "Configured ${plan.replyMode} trigger for ${plan.targetApp}")
            }
            ActionType.SEND_MESSAGE -> {
                val targetPkg = getPackageNameForApp(plan.targetApp)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, plan.messageText ?: "Hello")
                    `package` = targetPkg
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(plan.messageText)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(fallbackIntent) } catch (ignored: Exception) {}
                }
                repository.logExecution("Send Message", "Sent '${plan.messageText}' to ${plan.recipient} via ${plan.targetApp}")
            }
            ActionType.EXECUTE_MACRO_SEQUENCE -> {
                val targetPkg = getPackageNameForApp(plan.targetApp)
                openAppOrSearchStore(targetPkg, plan.targetApp)
                repository.logExecution("Macro Sequence Started", "Executing ${plan.macroSteps.size} automated steps for ${plan.targetApp}")

                val accService = com.example.service.JarvisAccessibilityService.instance
                if (accService != null) {
                    engineScope.launch(Dispatchers.Main) {
                        kotlinx.coroutines.delay(3500) // Wait for app to open
                        for (step in plan.macroSteps) {
                            when (step.stepType) {
                                "SEARCH_INPUT" -> {
                                    val query = step.target ?: plan.query ?: ""
                                    accService.searchAndInputInActiveApp(query)
                                    kotlinx.coroutines.delay(2000)
                                }
                                "CLICK_TEXT" -> {
                                    val text = step.target ?: plan.query ?: ""
                                    accService.findAndClickNodeByText(text)
                                    kotlinx.coroutines.delay(2500)
                                }
                                "LIKE_POST" -> {
                                    accService.findAndClickLikeButton()
                                    kotlinx.coroutines.delay(1000)
                                }
                                "SWIPE_INTERVAL" -> {
                                    val intervalMs = (step.intervalSeconds * 1000).coerceAtLeast(1000)
                                    val iterations = ((step.durationMinutes * 60) / step.intervalSeconds).coerceAtMost(600) // cap to 10 mins or 600 swipes for UI performance
                                    for (i in 0 until iterations) {
                                        accService.performSwipeUp()
                                        kotlinx.coroutines.delay(intervalMs)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    repository.logExecution("Macro Service Note", "Enable Accessibility Service in Settings for automated clicks & swiping.")
                }
            }
            ActionType.NAVIGATE_AND_CLICK -> {
                val packageName = getPackageNameForApp(plan.targetApp)
                openAppOrSearchStore(packageName, plan.targetApp)
                repository.logExecution("Navigate App", "Navigated in ${plan.targetApp}")
            }
            ActionType.GENERAL_RESPONSE -> {
                repository.logExecution("General Conversation", plan.speechResponse)
            }
        }
    }


    private fun openAppOrSearchStore(packageName: String?, appName: String?) {
        val pm = context.packageManager
        val launchIntent = packageName?.let { pm.getLaunchIntentForPackage(it) }
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            // Fallback launch or web intent
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(appName ?: "app")}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(marketIntent)
            } catch (e: Exception) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(appName ?: "app")}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }
        }
    }

    private fun getPackageNameForApp(appName: String?): String? {
        if (appName.isNullOrBlank()) return null
        val target = appName.trim().lowercase()

        // Known static fast maps
        when (target) {
            "youtube" -> return "com.google.android.youtube"
            "spotify" -> return "com.spotify.music"
            "chrome" -> return "com.android.chrome"
            "whatsapp" -> return "com.whatsapp"
            "instagram" -> return "com.instagram.android"
            "telegram" -> return "org.telegram.messenger"
        }

        // Dynamic package lookup across all installed launcher applications
        try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherActivities = pm.queryIntentActivities(mainIntent, 0)

            // 1. Exact match on app label
            for (resolveInfo in launcherActivities) {
                val label = resolveInfo.loadLabel(pm).toString().lowercase()
                if (label == target) {
                    return resolveInfo.activityInfo.packageName
                }
            }

            // 2. Partial match on app label
            for (resolveInfo in launcherActivities) {
                val label = resolveInfo.loadLabel(pm).toString().lowercase()
                if (label.contains(target) || target.contains(label)) {
                    return resolveInfo.activityInfo.packageName
                }
            }

            // 3. Match on package name substring
            for (resolveInfo in launcherActivities) {
                val pkg = resolveInfo.activityInfo.packageName.lowercase()
                if (pkg.contains(target)) {
                    return resolveInfo.activityInfo.packageName
                }
            }
        } catch (e: Exception) {
            Log.e("JARVIS_ENGINE", "Error searching installed packages: ${e.message}")
        }

        return null
    }
}
