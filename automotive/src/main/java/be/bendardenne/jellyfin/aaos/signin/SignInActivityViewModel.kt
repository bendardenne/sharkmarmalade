package be.bendardenne.jellyfin.aaos.signin

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.bendardenne.jellyfin.aaos.JellyfinAccountManager
import be.bendardenne.jellyfin.aaos.ServerType
import be.bendardenne.jellyfin.aaos.SharkMarmaladeConstants.LOG_MARKER
import be.bendardenne.jellyfin.aaos.api.MediaTreeFactory
import be.bendardenne.jellyfin.aaos.api.createSubsonicApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.QuickConnectDto
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SignInActivityViewModel @Inject constructor() : ViewModel() {

    @Inject
    lateinit var jellyfin: Jellyfin

    @Inject
    lateinit var accountManager: JellyfinAccountManager

    @Inject
    lateinit var mediaTreeFactory: MediaTreeFactory

    private var quickConnectSecret: String = ""

    private val _loggedIn = MutableLiveData<Boolean>()
    val loggedIn: LiveData<Boolean> = _loggedIn

    private val _quickConnectCode = MutableLiveData<Int>()
    val quickConnectCode: LiveData<Int> = _quickConnectCode

    suspend fun pingServer(
        serverUrl: String,
        serverType: ServerType = ServerType.JELLYFIN
    ): Boolean {
        return try {
            Log.i(LOG_MARKER, "Pinging $serverUrl ($serverType)")

            withContext(Dispatchers.IO) {
                when (serverType) {
                    ServerType.JELLYFIN -> {
                        val response = jellyfin.createApi(serverUrl).systemApi.getPingSystem()
                        response.status == 200
                    }

                    ServerType.SUBSONIC -> {
                        true
                        // TODO
                      /*  try {
                            createSubsonicApi(serverUrl).use { api ->
                                api.ping()
                            }
                            true
                        } catch (e: Exception) {
                            Log.w(LOG_MARKER, "Subsonic ping failed", e)
                            false
                        }*/
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_MARKER, "Error", e)
            false
        }
    }

    fun startQuickConnect(serverUrl: String) {
        Log.i(LOG_MARKER, "Initiate QuickConnect")
        val api = jellyfin.createApi(serverUrl)

        viewModelScope.launch {
            val isEnabled = withContext(Dispatchers.IO) {
                api.quickConnectApi.getQuickConnectEnabled()
            }

            if (isEnabled.status != 200 || !isEnabled.content) {
                _quickConnectCode.value = -1
                return@launch
            }

            val response = withContext(Dispatchers.IO) {
                api.quickConnectApi.initiateQuickConnect()
            }

            if (response.status == 200) {
                quickConnectSecret = response.content.secret
                Log.d(LOG_MARKER, "QuickConnect initiated")
                _quickConnectCode.value = Integer.valueOf(response.content.code)

                do {
                    delay(1.seconds)
                    checkQuickConnect(serverUrl)
                } while (isActive)
            }
        }
    }

    private suspend fun checkQuickConnect(server: String) {
        val api = jellyfin.createApi(server)
        val response = withContext(Dispatchers.IO) {
            api.quickConnectApi.getQuickConnectState(quickConnectSecret)
        }

        Log.d(LOG_MARKER, "Checking QuickConnect")

        if (response.status == 200) {
            if (!response.content.authenticated) {
                return
            }

            val loginResponse = withContext(Dispatchers.IO) {
                api.userApi.authenticateWithQuickConnect(QuickConnectDto(response.content.secret))
            }

            if (loginResponse.status == 200) {
                loginSuccess(
                    server,
                    loginResponse.content.user?.name!!,
                    loginResponse.content.accessToken!!,
                    ServerType.JELLYFIN
                )
            }
        }
    }

    suspend fun login(
        server: String,
        username: String,
        password: String,
        serverType: ServerType = ServerType.JELLYFIN
    ): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                when (serverType) {
                    ServerType.JELLYFIN -> {
                        val response = jellyfin.createApi(server).userApi.authenticateUserByName(
                            username,
                            password
                        )
                        if (response.status == 200) {
                            loginSuccess(
                                server,
                                username,
                                response.content.accessToken!!,
                                serverType
                            )
                            true
                        } else {
                            false
                        }
                    }

                    ServerType.SUBSONIC -> {
                        createSubsonicApi(server, username, password).use { subsonicApi ->
                            subsonicApi.ping()
                            loginSuccess(server, username, password, serverType)
                        }
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_MARKER, "Error", e)
            false
        }
    }

    private fun loginSuccess(
        server: String,
        username: String,
        token: String,
        serverType: ServerType
    ) {
        Log.i(LOG_MARKER, "$username successfully authenticated on $serverType")
        accountManager.storeAccount(server, username, token, serverType)
        mediaTreeFactory.invalidate()
        _loggedIn.postValue(true)
    }

    companion object {
        internal const val JELLYFIN_SERVER_URL = "jellyfinServer"
        internal const val SERVER_TYPE_KEY = "serverType"
    }
}
