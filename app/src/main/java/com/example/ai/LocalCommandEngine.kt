package com.example.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.AutomationRule
import com.example.data.JarvisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalCommandEngine(
    private val context: Context,
    private val repository: JarvisRepository
) {

    private val systemPrompt = """
        You are JARVIS-X, an autonomous AI digital operator for Android.
        Parse the user's natural language command into a structured action response.
        Return ONLY valid JSON matching this exact structure:
        {
          "actionType": "OPEN_APP" | "SEARCH_CHROME" | "SEND_MESSAGE" | "CREATE_BACKGROUND_RULE" | "GENERAL_RESPONSE",
          "targetApp": "YouTube" | "Spotify" | "Chrome" | "WhatsApp" | "Instagram" | "Telegram" | null,
          "query": "search phrase" | null,
          "messageText": "message body" | null,
          "recipient": "person or group name" | null,
          "triggerType": "NOTIFICATION_APP" | "POWER_CONNECTED" | "VOICE_KEYWORD" | null,
          "triggerCondition": "app or keyword condition" | null,
          "speechResponse": "Concise voice confirmation string to say to user",
          "requiresConfirmation": true | false
        }
    """.trimIndent()

    private fun getCustomApiKey(): String? {
        val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
        return prefs.getString("custom_gemini_api_key", null)?.trim()?.takeIf { it.isNotBlank() }
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
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
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

            ActionPlan(
                actionType = actionType,
                targetApp = json.optString("targetApp").takeIf { it.isNotEmpty() && it != "null" },
                query = json.optString("query").takeIf { it.isNotEmpty() && it != "null" },
                messageText = json.optString("messageText").takeIf { it.isNotEmpty() && it != "null" },
                recipient = json.optString("recipient").takeIf { it.isNotEmpty() && it != "null" },
                triggerType = json.optString("triggerType").takeIf { it.isNotEmpty() && it != "null" },
                triggerCondition = json.optString("triggerCondition").takeIf { it.isNotEmpty() && it != "null" },
                speechResponse = json.optString("speechResponse", "Command processed."),
                requiresConfirmation = json.optBoolean("requiresConfirmation", false)
            )
        } catch (e: Exception) {
            parseOffline(fallbackPrompt)
        }
    }

    fun parseOffline(prompt: String): ActionPlan {
        val lower = prompt.lowercase()

        return when {
            lower.contains("every time") || lower.contains("whenever") || lower.contains("auto-reply") || lower.contains("automatically") -> {
                val app = when {
                    lower.contains("whatsapp") -> "WhatsApp"
                    lower.contains("instagram") -> "Instagram"
                    lower.contains("telegram") -> "Telegram"
                    lower.contains("spotify") || lower.contains("charger") || lower.contains("plug") -> "Spotify"
                    else -> "Messaging App"
                }
                val triggerType = if (lower.contains("charger") || lower.contains("plug")) "POWER_CONNECTED" else "NOTIFICATION_APP"
                val replyMsg = if (lower.contains("busy")) "I'm busy right now, I'll reply later." else "Thank you for your message!"

                ActionPlan(
                    actionType = ActionType.CREATE_BACKGROUND_RULE,
                    targetApp = app,
                    triggerType = triggerType,
                    triggerCondition = app.lowercase(),
                    messageText = replyMsg,
                    speechResponse = "Created persistent automation rule for $app.",
                    automationTitle = "Automation: $app ($triggerType)"
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
                        isEnabled = true
                    )
                )
                repository.logExecution("Automation Rule Created", "Configured trigger for ${plan.targetApp}")
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
