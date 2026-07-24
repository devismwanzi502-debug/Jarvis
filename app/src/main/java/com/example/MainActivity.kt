package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.ai.LocalCommandEngine
import com.example.data.JarvisDatabase
import com.example.data.JarvisRepository
import com.example.service.JarvisForegroundService
import com.example.ui.JarvisViewModel
import com.example.ui.JarvisViewModelFactory
import com.example.ui.screens.MainScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: JarvisViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = JarvisDatabase.getDatabase(applicationContext)
        val repository = JarvisRepository(db.automationDao(), db.executionLogDao(), db.memoryDao())
        val commandEngine = LocalCommandEngine(applicationContext, repository)

        val factory = JarvisViewModelFactory(repository, commandEngine, applicationContext)
        viewModel = ViewModelProvider(this, factory)[JarvisViewModel::class.java]

        // Request runtime permissions
        requestRequiredPermissions()

        // Start Foreground Service
        startJarvisForegroundService()

        setContent {
            JarvisTheme {
                Surface(color = DarkBackground) {
                    val uiState by viewModel.uiState.collectAsState()
                    MainScreen(viewModel = viewModel, uiState = uiState)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkServicePermissions()
    }

    private fun checkServicePermissions() {
        val isAccEnabled = isAccessibilityServiceEnabled(this)
        val isNotifEnabled = isNotificationListenerEnabled(this)
        viewModel.updatePermissionStatuses(isAccEnabled, isNotifEnabled)
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        }
    }

    private fun startJarvisForegroundService() {
        try {
            val serviceIntent = Intent(this, JarvisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val prefString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return prefString.contains(context.packageName)
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(context.packageName) == true
    }
}
