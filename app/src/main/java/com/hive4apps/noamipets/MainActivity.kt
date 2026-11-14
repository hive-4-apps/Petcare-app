package com.hive4apps.noamipets

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.hive4apps.noamipets.ui.MainViewModel
import com.hive4apps.noamipets.ui.NoamiPetsRoot
import com.hive4apps.noamipets.ui.theme.NoamiPetsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            NoamiPetsTheme {
                NoamiPetsRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}
