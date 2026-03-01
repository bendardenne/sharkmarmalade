package be.bendardenne.jellyfin.aaos.settings

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import be.bendardenne.jellyfin.aaos.R
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var viewModel: SettingsFragmentViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        viewModel = ViewModelProvider(this)[SettingsFragmentViewModel::class.java]
        val version = findPreference<Preference>("version")
        version?.summary = viewModel.versionString()

        val uploadLogs = findPreference<Preference>("upload_logs")
        uploadLogs?.onPreferenceClickListener = Preference.OnPreferenceClickListener { pref ->
            viewModel.sendLogs()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.logUploadStatus.observe(viewLifecycleOwner) { status ->
            Snackbar.make(view, status, Snackbar.LENGTH_LONG).show()
        }
    }
}
