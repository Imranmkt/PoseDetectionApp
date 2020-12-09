package com.example.posedetectionapp.usecase.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.posedetectionapp.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}