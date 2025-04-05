/*
 * SPDX-FileCopyrightText: 2025 The LineageOS project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.gestures

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import vendor.lineage.touch.V1_0.Gesture
import vendor.lineage.touch.V1_0.ITouchscreenGesture
import java.util.ArrayList

object BootReceiver : BroadcastReceiver() {
    private const val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val userManager = context.getSystemService(UserManager::class.java)!!
        if (!userManager.isSystemUser) {
            Log.d(TAG, "Not running as the primary user, skipping tunable restoration.")
            return
        }

        runCatching {
            val service: ITouchscreenGesture = ITouchscreenGesture.getService(false)
            val gestures: ArrayList<Gesture> = service.supportedGestures
            val actionList: IntArray = GestureConstants.buildActionList(context, gestures)
            for (gesture in gestures) {
                service.setGestureEnabled(gesture, actionList[gesture.id] > 0)
            }
            GestureConstants.sendUpdateBroadcast(context, gestures)
        }
    }
}
