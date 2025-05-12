/*
 * SPDX-FileCopyrightText: 2025 The LineageOS project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.gestures

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.session.MediaSessionLegacyHelper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.SystemClock
import android.os.UserHandle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.util.SparseIntArray
import android.view.Display
import android.view.KeyEvent
import com.android.internal.os.DeviceKeyHandler
import evervolv.provider.EVSettings
import org.lineageos.gestures.GestureConstants

import com.evervolv.platform.internal.R.bool.config_proximityCheckOnWake
import com.evervolv.platform.internal.R.bool.config_proximityCheckOnWakeEnabledByDefault
import com.evervolv.platform.internal.R.integer.config_proximityCheckTimeout

class GestureHandler(private val context: Context) : DeviceKeyHandler {
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val cameraManager = context.getSystemService(CameraManager::class.java)!!
    private val powerManager = context.getSystemService(PowerManager::class.java)!!
    private val sensorManager = context.getSystemService(SensorManager::class.java)!!
    private val vibrator = context.getSystemService(Vibrator::class.java)!!

    private val gestureWakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK, "Settings:GestureWakeLock"
    )

    private val proximityCheckOnWake =
        context.resources.getBoolean(config_proximityCheckOnWake)
    private val proximityCheckOnWakeDefault =
        context.resources.getBoolean(config_proximityCheckOnWakeEnabledByDefault)
    private val proximityTimeOut =
        context.resources.getInteger(config_proximityCheckTimeout)

    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val proximityWakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK, "Settings:ProximityWakeLock"
    )

    private val eventHandler = EventHandler(Looper.getMainLooper())
    private val keyCodeActions = SparseIntArray()
    private var torchEnabled = false

    private val packageContext = context.createPackageContext(
        GestureHandler::class.java.getPackage()!!.name, 0
    )

    private val sharedPreferences
        get() = packageContext.getSharedPreferences(
            packageContext.packageName + "_preferences",
            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
        )

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val keycodes = intent.getIntArrayExtra(
                GestureConstants.UPDATE_EXTRA_KEYCODE_MAPPING
            )
            val actions = intent.getIntArrayExtra(
                GestureConstants.UPDATE_EXTRA_ACTION_MAPPING
            )
        
            keyCodeActions.clear()
        
            if (keycodes != null && actions != null && keycodes.size == actions.size) {
                keycodes.indices.forEach { i ->
                    keyCodeActions.put(keycodes[i], actions[i])
                }
            }
        }
    }

    init {
        context.registerReceiver(
            broadcastReceiver,
            IntentFilter(GestureConstants.UPDATE_PREFS_ACTION),
            Context.RECEIVER_NOT_EXPORTED
        )
        cameraManager.registerTorchCallback(
            TorchModeCallback(),
            eventHandler
        )
    }

    override fun handleKeyEvent(event: KeyEvent): KeyEvent? {
        val action = keyCodeActions.get(event.getScanCode(), -1)
        if (action < GestureConstants.ACTION_NOTHING || event.action != KeyEvent.ACTION_UP || !isSetupComplete()) {
            return event
        } else if (action == GestureConstants.ACTION_NOTHING || eventHandler.hasMessages(GESTURE_REQUEST)) {
            return null
        } else {
            var msg = eventHandler.obtainMessage(GESTURE_REQUEST)
            msg.arg1 = action
            val duration = 2 * proximityTimeOut
            gestureWakeLock.acquire(duration.toLong())
            if (isProximityWakeEnabled()) {
                eventHandler.sendMessageDelayed(msg, duration.toLong())
                sensorManager.registerListener(object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (proximityWakeLock.isHeld()) {
                            proximityWakeLock.release()
                        }
                        sensorManager.unregisterListener(this)
                        if (!eventHandler.hasMessages(GESTURE_REQUEST)) {
                            // The sensor took too long; ignoring
                            return
                        }
                        eventHandler.removeMessages(GESTURE_REQUEST)
                        val maxRange = proximitySensor?.getMaximumRange() ?: return
                        if (event.values[0] >= maxRange) {
                            msg = eventHandler.obtainMessage(GESTURE_REQUEST)
                            msg.arg1 = action
                            eventHandler.sendMessage(msg)
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { }
                }, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST)
            } else {
                eventHandler.sendMessage(msg)
            }
            return null
        }
    }

    private fun isSetupComplete(): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.USER_SETUP_COMPLETE,
            0
        ) != 0
    }

    private fun isProximityWakeEnabled(): Boolean {
        val available = proximityCheckOnWake && proximitySensor != null
        return available && EVSettings.System.getInt(
            context.contentResolver,
            EVSettings.System.PROXIMITY_ON_WAKE,
            if (proximityCheckOnWakeDefault) 1 else 0
        ) == 1
    }

    private inner class TorchModeCallback : TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            val rearCameraId = getRearCameraId()
            if (cameraId != rearCameraId) return
            torchEnabled = enabled
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            val rearCameraId = getRearCameraId()
            if (cameraId != rearCameraId) return
            torchEnabled = false
        }
    }

    private inner class EventHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.arg1) {
                GestureConstants.ACTION_CAMERA -> launchCamera()
                GestureConstants.ACTION_FLASHLIGHT -> toggleFlashlight()
                GestureConstants.ACTION_BROWSER -> launchDefaultApp(Intent(Intent.ACTION_VIEW, Uri.parse("http:")))
                GestureConstants.ACTION_DIALER -> launchDefaultApp(Intent(Intent.ACTION_DIAL, null))
                GestureConstants.ACTION_EMAIL -> launchDefaultApp(Intent(Intent.ACTION_VIEW, Uri.parse("mailto:")))
                GestureConstants.ACTION_MESSAGES -> launchDefaultApp(Intent(Intent.ACTION_VIEW, Uri.parse("sms:")))
                GestureConstants.ACTION_PLAY_PAUSE_MUSIC -> handleMediaAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                GestureConstants.ACTION_PREVIOUS_TRACK -> handleMediaAction(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                GestureConstants.ACTION_NEXT_TRACK -> handleMediaAction(KeyEvent.KEYCODE_MEDIA_NEXT)
                GestureConstants.ACTION_VOLUME_DOWN -> handleVolumeAction(AudioManager.ADJUST_LOWER)
                GestureConstants.ACTION_VOLUME_UP -> handleVolumeAction(AudioManager.ADJUST_RAISE)
            }
        }
    }

    private fun launchCamera() {
        gestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION)
        context.sendBroadcastAsUser(
            Intent(evervolv.content.Intent.ACTION_SCREEN_CAMERA_GESTURE),
            UserHandle.CURRENT,
            android.Manifest.permission.STATUS_BAR_SERVICE
        )
        doHapticFeedback()
    }

    private fun toggleFlashlight() {
        val rearCameraId = getRearCameraId()
        if (rearCameraId != null) {
            gestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
            runCatching {
                cameraManager.setTorchMode(rearCameraId, !torchEnabled)
                torchEnabled = !torchEnabled
                doHapticFeedback()
            }
        }
    }

    private fun launchDefaultApp(action: Intent) {
        gestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        powerManager.wakeUp(
            SystemClock.uptimeMillis(),
            PowerManager.WAKE_REASON_GESTURE,
            TAG,
            Display.DEFAULT_DISPLAY
        )
        runCatching {
            val intent = getLaunchableIntent(action)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
            context.startActivityAsUser(
                intent,
                null,
                UserHandle(UserHandle.USER_CURRENT)
            )
            doHapticFeedback()
        }
    }

    private fun handleMediaAction(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                val helper = MediaSessionLegacyHelper.getHelper(context)
                if (helper == null) {
                    Log.w(TAG, "Unable to send media key event")
                    return
                }
                var event = KeyEvent(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    0
                )
                helper.sendMediaButtonEvent(
                    event, true
                )
                event = KeyEvent.changeAction(
                    event,
                    KeyEvent.ACTION_UP
                )
                helper.sendMediaButtonEvent(
                    event, true
                )
                doHapticFeedback()
            }
        }
    }

    private fun handleVolumeAction(direction: Int) {
        when (direction) {
            AudioManager.ADJUST_LOWER,
            AudioManager.ADJUST_RAISE -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                doHapticFeedback()
            }
        }
    }

    private fun doHapticFeedback() {
        if (!sharedPreferences.getBoolean(
            GestureConstants.KEY_TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK, true
        )) {
            return
        }

        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> vibrator.vibrate(
                MODE_VIBRATION_EFFECT,
                HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES
            )
            AudioManager.RINGER_MODE_NORMAL -> vibrator.vibrate(
                MODE_NORMAL_EFFECT,
                HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES
            )
        }
    }

    private fun getRearCameraId(): String? {
        runCatching {
            for (cameraId: String in cameraManager.cameraIdList) {
                val characteristics =
                        cameraManager.getCameraCharacteristics(cameraId)
                val orientation = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (orientation != CameraCharacteristics.LENS_FACING_BACK)
                    continue
                return cameraId
            }
        }
        return null
    }

    private fun getLaunchableIntent(intent: Intent): Intent? {
        val resInfo = context.packageManager.queryIntentActivities(
            intent, 0
        )
        return if (resInfo.isEmpty()) {
            null
        } else {
            context.packageManager.getLaunchIntentForPackage(
                resInfo[0].activityInfo.packageName
            )
        }
    }

    companion object {
        private const val TAG = "GestureHandler"

        private const val GESTURE_REQUEST = 0
        private const val GESTURE_WAKELOCK_DURATION = 3000L

        // Vibration attributes
        private val HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)

        // Vibration effects
        private val MODE_NORMAL_EFFECT = VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK)
        private val MODE_VIBRATION_EFFECT = VibrationEffect.get(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }
}
