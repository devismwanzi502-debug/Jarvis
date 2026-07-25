package com.example.ai

data class MacroStep(
    val stepType: String, // "OPEN_APP", "SEARCH_INPUT", "CLICK_TEXT", "LIKE_POST", "SWIPE_INTERVAL"
    val target: String? = null,
    val intervalSeconds: Long = 3,
    val durationMinutes: Long = 1
)

data class ActionPlan(
    val actionType: ActionType,
    val targetApp: String? = null,
    val query: String? = null,
    val messageText: String? = null,
    val recipient: String? = null,
    val automationTitle: String? = null,
    val triggerType: String? = null,
    val triggerCondition: String? = null,
    val speechResponse: String,
    val macroSteps: List<MacroStep> = emptyList(),
    val replyMode: String = "SPECIFIC_TEXT", // SPECIFIC_TEXT or CHATBOT_AI
    val requiresConfirmation: Boolean = false
)

enum class ActionType {
    OPEN_APP,
    SEARCH_CHROME,
    SEND_MESSAGE,
    AUTO_REPLY_SETUP,
    CREATE_BACKGROUND_RULE,
    NAVIGATE_AND_CLICK,
    EXECUTE_MACRO_SEQUENCE,
    GENERAL_RESPONSE
}

