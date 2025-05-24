package be.bendardenne.jellyfin.aaos.settings

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import be.bendardenne.jellyfin.aaos.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var viewModel: SettingsFragmentViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        viewModel = ViewModelProvider(this)[SettingsFragmentViewModel::class.java]
        findPreference<Preference>("version")?.summary = viewModel.versionString()
    }
}

