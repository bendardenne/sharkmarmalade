package be.bendardenne.jellyfin.aaos.settings

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.concurrent.futures.await
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import be.bendardenne.jellyfin.aaos.JellyfinMediaLibrarySessionCallback.Companion.LOGOUT_COMMAND
import be.bendardenne.jellyfin.aaos.JellyfinMusicService
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import kotlinx.coroutines.launch
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import be.bendardenne.jellyfin.aaos.R
import be.bendardenne.jellyfin.aaos.signin.SignInActivity
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

        val logout = findPreference<Preference>("logout")
        logout?.onPreferenceClickListener = Preference.OnPreferenceClickListener { pref ->
            logout()
            true
        }
    }

    private fun logout() {
        val appContext = requireContext().applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, JellyfinMusicService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()

        lifecycleScope.launch {
            try {
                val controller = future.await()
                // Stops playback and clears the play queue.
                controller.sendCustomCommand(SessionCommand(LOGOUT_COMMAND, Bundle()), Bundle()).await()
                controller.release()
            } catch (e: Exception) {
                Log.w(LOG_MARKER, "Failed to stop playback on logout", e)
            }
            viewModel.logout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.logUploadStatus.observe(viewLifecycleOwner) { status ->
            Snackbar.make(view, status, Snackbar.LENGTH_LONG).show()
        }

        viewModel.logoutStatus.observe(viewLifecycleOwner) { loggedOut ->
            if (loggedOut) {
                Snackbar.make(view, R.string.logged_out, Snackbar.LENGTH_LONG).show()
                val intent = Intent(requireContext(), SignInActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                requireContext().startActivity(intent)
                requireActivity().finish()
            }
        }
    }
}
