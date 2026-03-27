package com.sjtech.wearpod

import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
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
                    audioOutputController = container.audioOutputController,
                    volumeController = container.volumeController,
                    downloadScheduler = container.downloadScheduler,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        setContent {
            WearPodRoot(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncVolume()
        viewModel.syncAudioOutput()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (viewModel.currentScreen == com.sjtech.wearpod.ui.WearPodScreen.Player) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    viewModel.increaseVolume()
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    viewModel.decreaseVolume()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun WearPodRoot(viewModel: WearPodViewModel) {
    WearPodTheme {
        WearPodApp(viewModel = viewModel)
    }
}
