package be.bendardenne.jellyfin.aaos.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.Jellyfin
import javax.inject.Inject

@HiltViewModel
class SettingsFragmentViewModel @Inject constructor() : ViewModel() {

    @Inject
    lateinit var jellyfin: Jellyfin

    fun versionString(): CharSequence =
        "SharkMarmalade: ${jellyfin.clientInfo?.version}, Jellyfin API: ${Jellyfin.apiVersion}"
}