package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
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

import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class JarvisForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: JarvisRepository
    private lateinit var commandEngine: LocalCommandEngine
    private var speechRecognizer: SpeechRecognizer? = null

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

        startHotwordListener()
    }

    private fun startHotwordListener() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        val hasAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            Log.w("JARVIS_SERVICE", "RECORD_AUDIO permission not granted yet. Waiting before starting SpeechRecognizer...")
            serviceScope.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(5000)
                startHotwordListener()
            }
            return
        }

        serviceScope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@JarvisForegroundService)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (matches != null) {
                            for (match in matches) {
                                val lower = match.lowercase()
                                if (lower.contains("hey jarvis") || lower.contains("jarvis") || lower.contains("ok jarvis") || lower.contains("hi jarvis")) {
                                    Log.i("JARVIS_WAKE", "Wake-word 'Hey Jarvis' detected!")
                                    triggerWakeUp()
                                    break
                                }
                            }
                        }
                        serviceScope.launch(Dispatchers.Main) {
                            kotlinx.coroutines.delay(1000)
                            startHotwordListener()
                        }
                    }

                    override fun onError(error: Int) {
                        serviceScope.launch(Dispatchers.Main) {
                            kotlinx.coroutines.delay(3000)
                            startHotwordListener()
                        }
                    }

                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer?.startListening(intent)
            } catch (e: SecurityException) {
                Log.e("JARVIS_SERVICE", "SecurityException starting SpeechRecognizer: ${e.message}")
                serviceScope.launch(Dispatchers.Main) {
                    kotlinx.coroutines.delay(5000)
                    startHotwordListener()
                }
            } catch (e: Exception) {
                Log.e("JARVIS_SERVICE", "Hotword listener error: ${e.message}")
            }
        }
    }

    private fun triggerWakeUp() {
        serviceScope.launch {
            repository.logExecution("Wake-Word Triggered", "'Hey Jarvis' hotword detected. Launching voice assistant.")
        }
        val wakeIntent = Intent(applicationContext, com.example.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("EXTRA_START_VOICE", true)
        }
        startActivity(wakeIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { speechRecognizer?.destroy() } catch (ignored: Exception) {}
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
