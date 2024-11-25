package be.bendardenne.jellyfin.aaos.signin

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import be.bendardenne.jellyfin.aaos.Constants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.JellyfinAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import javax.inject.Inject

@HiltViewModel
class SignInActivityViewModel @Inject constructor() : ViewModel() {

    @Inject
    lateinit var jellyfin: Jellyfin

    @Inject
    lateinit var accountManager: JellyfinAccountManager

    private val _loggedIn = MutableLiveData<Boolean>()
    val loggedIn: LiveData<Boolean> = _loggedIn

    suspend fun pingServer(serverUrl: String): Boolean {
        return try {
            Log.i(LOG_MARKER, "Pinging $serverUrl")
            val response = jellyfin.createApi(serverUrl).systemApi.getPingSystem()
            response.status == 200
        } catch (e: Exception) {
            Log.i(LOG_MARKER, "Error", e)
            false
        }
    }

    suspend fun login(server: String, username: String, password: String): Boolean {
        return try {
            val response =
                jellyfin.createApi(server).userApi.authenticateUserByName(username, password)

            if (response.status == 200) {
                Log.i(LOG_MARKER, "$username successfully authenticated")
                val account = accountManager.storeAccount(server, username, password)
                accountManager.updateToken(account, response.content.accessToken!!)

                _loggedIn.postValue(true)
            }

            response.status == 200
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Error", e)
            false
        }
    }

    companion object {
        internal const val JELLYFIN_SERVER_URL = "jellyfinServer"
    }
}
