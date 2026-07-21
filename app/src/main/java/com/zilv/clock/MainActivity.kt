package com.zilv.clock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zilv.clock.ui.ClockApp
import com.zilv.clock.ui.ClockTheme
import com.zilv.clock.ui.ClockViewModel

class MainActivity : ComponentActivity() {
    private val model: ClockViewModel by viewModels { ClockViewModel.factory((application as ClockApplication).repository, (application as ClockApplication).settings) }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            ClockTheme(state.isDarkMode) { ClockApp(model, state) }
        }
    }
}
