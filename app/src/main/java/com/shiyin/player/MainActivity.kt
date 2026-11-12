package com.shiyin.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.shiyin.player.data.AppStore
import com.shiyin.player.data.PlayHistory
import com.shiyin.player.player.PlayerBus
import com.shiyin.player.ui.ShiyinApp
import com.shiyin.player.util.CrashLog

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        AppStore.load(this)
        PlayHistory.load(this)
        PlayerBus.connect(this)
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { ShiyinApp() }
    }

    override fun onDestroy() {
        PlayerBus.disconnect()
        super.onDestroy()
    }
}
