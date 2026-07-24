package com.example.ai

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
    val requiresConfirmation: Boolean = false
)

enum class ActionType {
    OPEN_APP,
    SEARCH_CHROME,
    SEND_MESSAGE,
    AUTO_REPLY_SETUP,
    CREATE_BACKGROUND_RULE,
    NAVIGATE_AND_CLICK,
    GENERAL_RESPONSE
}
