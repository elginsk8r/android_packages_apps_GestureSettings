/*
 * SPDX-FileCopyrightText: 2025 The LineageOS project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.gestures

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class GestureSettingsActivity : FragmentActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.collapsing_toolbar_base_layout)
        supportFragmentManager.beginTransaction().replace(
            R.id.content_frame,
            GestureSettingsFragment()
        ).commit()
    }
}
