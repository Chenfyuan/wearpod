package com.sjtech.wearpod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sjtech.wearpod.ui.WearPodApp
import com.sjtech.wearpod.ui.WearPodViewModel
import com.sjtech.wearpod.ui.theme.WearPodTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<WearPodViewModel> {
        val container = (application as WearPodApplication).appContainer
        viewModelFactory {
            initializer {
                WearPodViewModel(
                    repository = container.repository,
                    playerGateway = container.playerGateway,
                    downloadScheduler = container.downloadScheduler,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WearPodRoot(viewModel)
        }
    }
}

@Composable
private fun WearPodRoot(viewModel: WearPodViewModel) {
    WearPodTheme {
        WearPodApp(viewModel = viewModel)
    }
}
