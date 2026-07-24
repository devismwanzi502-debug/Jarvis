package com.example.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.JarvisDatabase
import com.example.data.JarvisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JarvisNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: JarvisRepository

    override fun onCreate() {
        super.onCreate()
        val db = JarvisDatabase.getDatabase(applicationContext)
        repository = JarvisRepository(db.automationDao(), db.executionLogDao(), db.memoryDao())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName ?: return
        val extras: Bundle = sbn.notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Skip internal/empty notifications
        if (title.isBlank() && text.isBlank()) return
        if (packageName == applicationContext.packageName) return

        Log.d("JARVIS_NOTIF", "Received notification from $packageName | Title: $title | Text: $text")

        serviceScope.launch {
            val enabledRules = repository.getEnabledRules()
            for (rule in enabledRules) {
                if (rule.triggerType == "NOTIFICATION_APP") {
                    val sourceApp = rule.triggerSourceApp
                    val appMatch = sourceApp.isNullOrBlank() ||
                            packageName.contains(sourceApp, ignoreCase = true) ||
                            sourceApp.contains("messaging", ignoreCase = true)

                    val conditionText = rule.triggerConditionText
                    val conditionMatch = conditionText.isNullOrBlank() ||
                            text.contains(conditionText, ignoreCase = true) ||
                            title.contains(conditionText, ignoreCase = true)

                    if (appMatch && conditionMatch) {
                        Log.i("JARVIS_AUTOMATION", "Trigger matched rule '${rule.title}' for $packageName")
                        val replyText = rule.actionPayload ?: "I'm busy right now, I'll get back to you soon."
                        
                        repository.logExecution(
                            title = "Auto-Reply Triggered",
                            description = "Replied to $title on $packageName: '$replyText'"
                        )

                        // Attempt Direct Notification Auto-Reply if available
                        attemptInlineReply(sbn, replyText)
                    }
                }
            }
        }
    }

    private fun attemptInlineReply(sbn: StatusBarNotification, replyText: String) {
        val actions = sbn.notification.actions ?: return
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                try {
                    val intent = android.content.Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, replyText)
                    android.app.RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                    action.actionIntent.send(applicationContext, 0, intent)
                    Log.i("JARVIS_NOTIF", "Successfully sent inline notification auto-reply!")
                    return
                } catch (e: Exception) {
                    Log.e("JARVIS_NOTIF", "Failed to send inline reply: ${e.message}")
                }
            }
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this.isNullOrBlank()
}
