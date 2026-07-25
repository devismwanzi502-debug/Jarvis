package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerType: String, // NOTIFICATION_APP, POWER_CONNECTED, VOICE_KEYWORD, SCHEDULE
    val triggerSourceApp: String? = null, // e.g. "com.instagram.android", "com.whatsapp"
    val triggerConditionText: String? = null, // e.g. "DM", "message"
    val actionType: String, // AUTO_REPLY_NOTIFICATION, OPEN_APP, EXECUTE_VOICE_COMMAND, SPEAK_TEXT
    val actionTargetApp: String? = null, // e.g. "com.spotify.music"
    val actionPayload: String? = null, // e.g. "I'm busy right now, talk later!"
    val replyMode: String = "SPECIFIC_TEXT", // SPECIFIC_TEXT or CHATBOT_AI
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "execution_logs")
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleOrCommandTitle: String,
    val description: String,
    val status: String, // SUCCESS, FAILED, PENDING
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_memories")
data class AgentMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)
