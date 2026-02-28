package be.bendardenne.jellyfin.aaos

import org.jellyfin.sdk.api.client.ApiClient

/**
 * Extension function which applies credentials to a Jellyfin ClientApi and returns
 * the associated headers to use in custom requests.
 */
fun ApiClient.auth(accountManager: JellyfinAccountManager): Map<String, String> {
    update(
        baseUrl = accountManager.server,
        accessToken = accountManager.token
    )

    return mapOf(
        "Authorization" to "MediaBrowser Client=\"${clientInfo.name}\", " +
                "Device=\"${deviceInfo.name}\", " +
                "DeviceId=\"${deviceInfo.id}\", " +
                "Version=\"${clientInfo.version}\", " +
                "Token=\"${accessToken}\""
    )
}