package be.bendardenne.jellyfin.aaos.settings

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.bendardenne.jellyfin.aaos.JellyfinAccountManager
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.auth
import com.google.common.base.Strings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import java.io.BufferedReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsFragmentViewModel
@Inject constructor(private val accountManager: JellyfinAccountManager) : ViewModel() {

    @Inject
    lateinit var jellyfin: Jellyfin

    val logUploadStatus = MutableLiveData<String>()

    fun versionString(): CharSequence =
        "SharkMarmalade: ${jellyfin.clientInfo?.version}, Jellyfin API: ${Jellyfin.apiVersion}"

    fun sendLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                logUploadStatus.postValue("Uploading...")

                val api = jellyfin.createApi()
                api.auth(accountManager)


                val process = try {
                    Runtime.getRuntime().exec("logcat -t 5000 -s $LOG_MARKER").also {
                        it.waitFor(10, TimeUnit.SECONDS)
                    }
                } catch (e: Exception) {
                    logUploadStatus.postValue(e.message)
                    return@withContext
                }

                val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
                if (!Strings.isNullOrEmpty(stderr)) {
                    logUploadStatus.postValue(stderr)
                    return@withContext
                }

                val content = process.inputStream.bufferedReader().use(BufferedReader::readText)
                val response = api.clientLogApi.logFile(content)

                logUploadStatus.postValue("Uploaded ${response.content.fileName}")
            }

        }
    }
}