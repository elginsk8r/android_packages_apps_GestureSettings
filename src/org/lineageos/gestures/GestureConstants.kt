/*
 * SPDX-FileCopyrightText: 2025 The LineageOS project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.gestures

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import vendor.lineage.touch.V1_0.Gesture

object GestureConstants {

    const val KEY_TOUCHSCREEN_GESTURE: String = "touchscreen_gesture"
    const val KEY_TOUCHSCREEN_GESTURE_SETTINGS: String = KEY_TOUCHSCREEN_GESTURE + "_settings"
    const val KEY_TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK: String =
        KEY_TOUCHSCREEN_GESTURE + "_haptic_feedback"
    const val TOUCHSCREEN_GESTURE_TITLE: String = KEY_TOUCHSCREEN_GESTURE + "_%s_title"

    // Broadcast action for settings update
    const val UPDATE_PREFS_ACTION: String = "com.evervolv.toolbox.gestures.UPDATE_SETTINGS"
    // Broadcast extra: keycode mapping (int[]: key = gesture ID, value = keycode)
    const val UPDATE_EXTRA_KEYCODE_MAPPING: String = "keycode_mappings"
    // Broadcast extra: assigned actions (int[]: key = gesture ID, value = action)
    const val UPDATE_EXTRA_ACTION_MAPPING: String = "action_mappings"

    // Touchscreen gesture actions
    const val ACTION_NOTHING: Int = 0
    const val ACTION_FLASHLIGHT: Int = 1
    const val ACTION_CAMERA: Int = 2
    const val ACTION_BROWSER: Int = 3
    const val ACTION_DIALER: Int = 4
    const val ACTION_EMAIL: Int = 5
    const val ACTION_MESSAGES: Int = 6
    const val ACTION_PLAY_PAUSE_MUSIC: Int = 7
    const val ACTION_PREVIOUS_TRACK: Int = 8
    const val ACTION_NEXT_TRACK: Int = 9
    const val ACTION_VOLUME_DOWN: Int = 10
    const val ACTION_VOLUME_UP: Int = 11

    fun getDefaultGestureActions(
        context: Context,
        gestures: MutableList<Gesture>
    ): IntArray {
        val defaultActions = context.resources.getIntArray(
            R.array.config_defaultTouchscreenGestureActions
        )
        if (defaultActions.size >= gestures.size) {
            return defaultActions
        }

        val filledDefaultActions = IntArray(gestures.size)
        System.arraycopy(defaultActions, 0, filledDefaultActions, 0, defaultActions.size)
        return filledDefaultActions
    }

    fun buildActionList(
        context: Context,
        gestures: MutableList<Gesture>
    ): IntArray {
        val result = IntArray(gestures.size)
        val defaultActions: IntArray = getDefaultGestureActions(context, gestures)
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        for (gesture in gestures) {
            val key: String = buildPreferenceKey(gesture)
            val defaultValue = defaultActions[gesture.id].toString()
            result[gesture.id] = prefs.getString(key, defaultValue)!!.toInt()
        }
        return result
    }

    fun buildPreferenceKey(gesture: Gesture): String {
        return KEY_TOUCHSCREEN_GESTURE + "_" + gesture.id
    }

    fun sendUpdateBroadcast(
        context: Context,
        gestures: MutableList<Gesture>
    ) {
        val intent = Intent(UPDATE_PREFS_ACTION)
        val keycodes = IntArray(gestures.size)
        val actions: IntArray = buildActionList(context, gestures)
        for (gesture in gestures) {
            keycodes[gesture.id] = gesture.keycode
        }
        intent.putExtra(UPDATE_EXTRA_KEYCODE_MAPPING, keycodes)
        intent.putExtra(UPDATE_EXTRA_ACTION_MAPPING, actions)
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
        context.sendBroadcastAsUser(intent, android.os.Process.myUserHandle())
    }
}
