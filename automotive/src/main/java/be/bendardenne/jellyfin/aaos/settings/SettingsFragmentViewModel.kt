package be.bendardenne.jellyfin.aaos.settings

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.bendardenne.jellyfin.aaos.JellyfinAccountManager
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import java.io.BufferedReader
import javax.inject.Inject

@HiltViewModel
class SettingsFragmentViewModel
@Inject constructor(private val accountManager: JellyfinAccountManager) : ViewModel() {

    @Inject
    lateinit var jellyfin: Jellyfin

    val toastEvent = MutableLiveData<ToastEvent>()

    fun versionString(): CharSequence =
        "SharkMarmalade: ${jellyfin.clientInfo?.version}, Jellyfin API: ${Jellyfin.apiVersion}"

    fun sendLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val process = Runtime.getRuntime().exec("logcat -t 5000 -s $LOG_MARKER")
                process.waitFor()

                val api = jellyfin.createApi()
                api.auth(accountManager)

                val content = process.inputStream.bufferedReader().use(BufferedReader::readText)
                api.clientLogApi.logFile(content)
            }

            toastEvent.postValue(ToastEvent())
        }
    }

    open class ToastEvent {
        var shown = false
            private set

        fun needsShowing(): Boolean {
            return if (shown) {
                false
            } else {
                shown = true
                true
            }
        }
    }
}