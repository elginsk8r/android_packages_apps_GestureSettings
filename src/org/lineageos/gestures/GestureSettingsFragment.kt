/*
 * SPDX-FileCopyrightText: 2025 The LineageOS project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.gestures

import android.content.Context
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.lineageos.gestures.GestureConstants
import vendor.lineage.touch.V1_0.Gesture
import vendor.lineage.touch.V1_0.ITouchscreenGesture

class GestureSettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.gesture_settings)
        requireActivity().actionBar?.setDisplayHomeAsUpEnabled(true)

        val service: ITouchscreenGesture = ITouchscreenGesture.getService(false)
        val actions: IntArray =
            GestureConstants.getDefaultGestureActions(
                requireContext(),
                service.supportedGestures
            )
        for (gesture in service.supportedGestures) {
            preferenceScreen.addPreference(
                TouchscreenGesturePreference(
                    requireContext(), service, gesture, actions[gesture.id]
                )
            )
        }
    }

    private class TouchscreenGesturePreference(
        private val context: Context,
        private val service: ITouchscreenGesture,
        private val gesture: Gesture,
        private val defaultAction: Int
    ) : ListPreference(context) {

        init {
            setKey(GestureConstants.buildPreferenceKey(gesture))
            setEntries(R.array.touchscreen_gesture_action_entries)
            setEntryValues(R.array.touchscreen_gesture_action_values)
            setDefaultValue(defaultAction.toString())
            setIcon(getIconDrawableResourceForAction(defaultAction))

            setSummary("%s")
            setDialogTitle(R.string.touchscreen_gesture_action_dialog_title)
            setTitle(
                GestureConstants.getLocalizedString(
                    context,
                    gesture.name,
                    GestureConstants.TOUCHSCREEN_GESTURE_TITLE
                )
            )
        }

        override fun callChangeListener(newValue: Any): Boolean {
            val action = newValue.toString().toInt()
            if (!service.setGestureEnabled(gesture, action > 0)) {
                return false
            }
            return super.callChangeListener(newValue)
        }

        override fun persistString(value: String): Boolean {
            if (!super.persistString(value)) {
                return false
            }
            setIcon(getIconDrawableResourceForAction(value.toInt()))
            GestureConstants.sendUpdateBroadcast(
                context, service.supportedGestures
            )
            return true
        }

        fun getIconDrawableResourceForAction(action: Int): Int {
            return when (action) {
                GestureConstants.ACTION_CAMERA -> R.drawable.ic_gesture_action_camera
                GestureConstants.ACTION_FLASHLIGHT -> R.drawable.ic_gesture_action_flashlight
                GestureConstants.ACTION_BROWSER -> R.drawable.ic_gesture_action_browser
                GestureConstants.ACTION_DIALER -> R.drawable.ic_gesture_action_dialer
                GestureConstants.ACTION_EMAIL -> R.drawable.ic_gesture_action_email
                GestureConstants.ACTION_MESSAGES -> R.drawable.ic_gesture_action_messages
                GestureConstants.ACTION_PLAY_PAUSE_MUSIC -> R.drawable.ic_gesture_action_play_pause
                GestureConstants.ACTION_PREVIOUS_TRACK -> R.drawable.ic_gesture_action_previous_track
                GestureConstants.ACTION_NEXT_TRACK -> R.drawable.ic_gesture_action_next_track
                GestureConstants.ACTION_VOLUME_DOWN -> R.drawable.ic_gesture_action_volume_down
                GestureConstants.ACTION_VOLUME_UP -> R.drawable.ic_gesture_action_volume_up
                else -> R.drawable.ic_gesture_action_none
            }
        }
    }
}
