package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.ai.LocalCommandEngine
import com.example.data.JarvisDatabase
import com.example.data.JarvisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JarvisForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: JarvisRepository
    private lateinit var commandEngine: LocalCommandEngine

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                Log.i("JARVIS_SERVICE", "Charger connected! Checking power automations...")
                serviceScope.launch {
                    val rules = repository.getEnabledRules()
                    for (rule in rules) {
                        if (rule.triggerType == "POWER_CONNECTED") {
                            repository.logExecution("Power Automation", "Charger connected. Triggering '${rule.title}'")
                            val appToLaunch = rule.actionTargetApp ?: "Spotify"
                            commandEngine.parseAndExecute("Open $appToLaunch")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val db = JarvisDatabase.getDatabase(applicationContext)
        repository = JarvisRepository(db.automationDao(), db.executionLogDao(), db.memoryDao())
        commandEngine = LocalCommandEngine(applicationContext, repository)

        val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        registerReceiver(powerReceiver, filter)

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        Log.i("JARVIS_SERVICE", "JARVIS-X Foreground Service started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(powerReceiver) } catch (ignored: Exception) {}
        Log.i("JARVIS_SERVICE", "JARVIS-X Foreground Service stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS-X Agent Background Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors background triggers, voice wake keywords, and device automations."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("JARVIS-X Digital Operator Active")
        .setContentText("Autonomous background engine & trigger monitoring running")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .build()

    companion object {
        const val CHANNEL_ID = "jarvis_service_channel"
        const val NOTIFICATION_ID = 1001
    }
}
