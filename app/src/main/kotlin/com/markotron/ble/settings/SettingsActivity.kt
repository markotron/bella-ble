package com.markotron.ble.settings

import android.app.Activity
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import com.markotron.ble.R

/**
 * Created by markotron on 30/10/2017.
 */

class SettingsFragment : PreferenceFragment() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.preferences)
  }
}

class SettingsActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    fragmentManager
        .beginTransaction()
        .replace(android.R.id.content, SettingsFragment())
        .commit()
  }
}