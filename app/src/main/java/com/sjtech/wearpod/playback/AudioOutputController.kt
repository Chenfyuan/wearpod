package com.sjtech.wearpod.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioOutputKind {
    SPEAKER,
    BLUETOOTH,
    WIRED,
    REMOTE,
    OTHER,
}

data class AudioOutputSnapshot(
    val label: String = "手表扬声器",
    val kind: AudioOutputKind = AudioOutputKind.SPEAKER,
) {
    val isExternal: Boolean
        get() = kind != AudioOutputKind.SPEAKER
}

class AudioOutputController(
    private val appContext: Context,
) {
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mediaRouter = MediaRouter2.getInstance(appContext)
    private val mutableState = MutableStateFlow(readSnapshot())
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }
    private val controllerCallback = object : MediaRouter2.ControllerCallback() {
        override fun onControllerUpdated(controller: MediaRouter2.RoutingController) {
            refresh()
        }
    }
    private val transferCallback = object : MediaRouter2.TransferCallback() {
        override fun onTransfer(
            oldController: MediaRouter2.RoutingController,
            newController: MediaRouter2.RoutingController,
        ) {
            refresh()
        }

        override fun onStop(controller: MediaRouter2.RoutingController) {
            refresh()
        }
    }

    val state: StateFlow<AudioOutputSnapshot> = mutableState.asStateFlow()

    init {
        audioManager.registerAudioDeviceCallback(
            deviceCallback,
            Handler(Looper.getMainLooper()),
        )
        mediaRouter.registerControllerCallback(appContext.mainExecutor, controllerCallback)
        mediaRouter.registerTransferCallback(appContext.mainExecutor, transferCallback)
    }

    fun refresh(): AudioOutputSnapshot {
        val snapshot = readSnapshot()
        mutableState.value = snapshot
        return snapshot
    }

    fun showSystemOutputSwitcher() {
        val shown = runCatching {
            mediaRouter.showSystemOutputSwitcher()
        }.getOrDefault(false)

        if (!shown) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
        refresh()
    }

    private fun readSnapshot(): AudioOutputSnapshot {
        val selectedRoute = mediaRouter
            .systemController
            ?.selectedRoutes
            ?.sortedByDescending(::routePriority)
            ?.firstOrNull()

        if (selectedRoute != null) {
            return selectedRoute.toSnapshot()
        }

        return audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .sortedByDescending(::devicePriority)
            .firstOrNull()
            ?.toSnapshot()
            ?: AudioOutputSnapshot()
    }

    private fun MediaRoute2Info.toSnapshot(): AudioOutputSnapshot {
        val routeName = name?.toString()?.trim().orEmpty()
        return when (type) {
            MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
            MediaRoute2Info.TYPE_BLE_HEADSET,
            MediaRoute2Info.TYPE_HEARING_AID,
            -> AudioOutputSnapshot(
                label = routeName.ifBlank { "蓝牙耳机" },
                kind = AudioOutputKind.BLUETOOTH,
            )

            MediaRoute2Info.TYPE_WIRED_HEADPHONES,
            MediaRoute2Info.TYPE_WIRED_HEADSET,
            MediaRoute2Info.TYPE_USB_HEADSET,
            MediaRoute2Info.TYPE_USB_ACCESSORY,
            MediaRoute2Info.TYPE_USB_DEVICE,
            -> AudioOutputSnapshot(
                label = routeName.ifBlank { "有线耳机" },
                kind = AudioOutputKind.WIRED,
            )

            MediaRoute2Info.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER,
            MediaRoute2Info.TYPE_REMOTE_CAR,
            MediaRoute2Info.TYPE_REMOTE_COMPUTER,
            MediaRoute2Info.TYPE_REMOTE_GAME_CONSOLE,
            MediaRoute2Info.TYPE_REMOTE_SMARTPHONE,
            MediaRoute2Info.TYPE_REMOTE_SMARTWATCH,
            MediaRoute2Info.TYPE_REMOTE_SPEAKER,
            MediaRoute2Info.TYPE_REMOTE_TABLET,
            MediaRoute2Info.TYPE_REMOTE_TABLET_DOCKED,
            MediaRoute2Info.TYPE_REMOTE_TV,
            MediaRoute2Info.TYPE_GROUP,
            MediaRoute2Info.TYPE_MULTICHANNEL_SPEAKER_GROUP,
            -> AudioOutputSnapshot(
                label = routeName.ifBlank { "远程播放" },
                kind = AudioOutputKind.REMOTE,
            )

            MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> AudioOutputSnapshot(
                label = "手表扬声器",
                kind = AudioOutputKind.SPEAKER,
            )

            else -> AudioOutputSnapshot(
                label = routeName.ifBlank { "当前输出" },
                kind = AudioOutputKind.OTHER,
            )
        }
    }

    private fun AudioDeviceInfo.toSnapshot(): AudioOutputSnapshot {
        val deviceName = productName?.toString()?.trim().orEmpty()
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            -> AudioOutputSnapshot(
                label = deviceName.ifBlank { "蓝牙耳机" },
                kind = AudioOutputKind.BLUETOOTH,
            )

            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            -> AudioOutputSnapshot(
                label = deviceName.ifBlank { "有线耳机" },
                kind = AudioOutputKind.WIRED,
            )

            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputSnapshot(
                label = "手表扬声器",
                kind = AudioOutputKind.SPEAKER,
            )

            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            AudioDeviceInfo.TYPE_DOCK,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            -> AudioOutputSnapshot(
                label = deviceName.ifBlank { "外部设备" },
                kind = AudioOutputKind.REMOTE,
            )

            else -> AudioOutputSnapshot(
                label = deviceName.ifBlank { "当前输出" },
                kind = AudioOutputKind.OTHER,
            )
        }
    }
}

private fun routePriority(route: MediaRoute2Info): Int = when (route.type) {
    MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
    MediaRoute2Info.TYPE_BLE_HEADSET,
    MediaRoute2Info.TYPE_HEARING_AID,
    -> 50

    MediaRoute2Info.TYPE_WIRED_HEADPHONES,
    MediaRoute2Info.TYPE_WIRED_HEADSET,
    MediaRoute2Info.TYPE_USB_HEADSET,
    MediaRoute2Info.TYPE_USB_ACCESSORY,
    MediaRoute2Info.TYPE_USB_DEVICE,
    -> 40

    MediaRoute2Info.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER,
    MediaRoute2Info.TYPE_REMOTE_CAR,
    MediaRoute2Info.TYPE_REMOTE_COMPUTER,
    MediaRoute2Info.TYPE_REMOTE_GAME_CONSOLE,
    MediaRoute2Info.TYPE_REMOTE_SMARTPHONE,
    MediaRoute2Info.TYPE_REMOTE_SMARTWATCH,
    MediaRoute2Info.TYPE_REMOTE_SPEAKER,
    MediaRoute2Info.TYPE_REMOTE_TABLET,
    MediaRoute2Info.TYPE_REMOTE_TABLET_DOCKED,
    MediaRoute2Info.TYPE_REMOTE_TV,
    MediaRoute2Info.TYPE_GROUP,
    MediaRoute2Info.TYPE_MULTICHANNEL_SPEAKER_GROUP,
    -> 30

    MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> 10
    else -> 0
}

private fun devicePriority(device: AudioDeviceInfo): Int = when (device.type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    -> 50

    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    -> 40

    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC,
    AudioDeviceInfo.TYPE_HDMI_EARC,
    AudioDeviceInfo.TYPE_DOCK,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    -> 30

    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 10
    else -> 0
}
