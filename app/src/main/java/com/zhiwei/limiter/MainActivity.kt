package com.zhiwei.limiter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.zhiwei.limiter.ui.AppLimiterScreen
import com.zhiwei.limiter.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var notifPermissionAsked by mutableStateOf(false)

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            notifPermissionAsked = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationIfNeeded()
        requestIgnoreBatteryOptimization()

        setContent {
            MyApplicationTheme {
                AppLimiterScreen()
            }
        }
    }

    private fun askNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifPermissionAsked) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionAsked = true
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** 引导用户把本应用排除在电池优化之外，避免守护被系统掐掉。 */
    private fun requestIgnoreBatteryOptimization() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        } catch (_: Throwable) {
        }
    }
}