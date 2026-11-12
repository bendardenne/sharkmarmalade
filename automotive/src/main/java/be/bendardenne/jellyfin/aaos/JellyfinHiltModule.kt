package be.bendardenne.jellyfin.aaos

import android.accounts.AccountManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo

@Module
@InstallIn(SingletonComponent::class)
class JellyfinHiltModule {

    @Provides
    fun provideJellyfin(@ApplicationContext appContext: Context): Jellyfin {
        val version =
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        val httpFactory = OkHttpFactory(
            OkHttpClient.Builder().addInterceptor(UserAgentInterceptor).build()
        )

        return createJellyfin {
            clientInfo = ClientInfo(appContext.getString(R.string.app_name), version ?: "unknown")
            deviceInfo = androidDevice(appContext)
            context = appContext
            apiClientFactory = httpFactory
            socketConnectionFactory = httpFactory
        }
    }

    @Provides
    fun provideAccountManager(@ApplicationContext appContext: Context): JellyfinAccountManager {
        return JellyfinAccountManager(AccountManager.get(appContext))
    }
}
