package be.bendardenne.jellyfin.aaos

import android.accounts.Account
import android.accounts.AccountManager
import android.os.Bundle
import be.bendardenne.jellyfin.aaos.auth.Authenticator

enum class ServerType {
    JELLYFIN,
    SUBSONIC
}

class JellyfinAccountManager(private val accountManager: AccountManager) {

    companion object {
        const val ACCOUNT_TYPE = Authenticator.ACCOUNT_TYPE
        const val TOKEN_TYPE = "$ACCOUNT_TYPE.access_token"
        const val USERDATA_SERVER_KEY = "$ACCOUNT_TYPE.server"
        const val USERDATA_SERVER_TYPE_KEY = "$ACCOUNT_TYPE.server_type"
    }

    private val account: Account?
        get() = accountManager.getAccountsByType(ACCOUNT_TYPE).firstOrNull()

    val username: String?
        get() = account?.name

    val server: String?
        get() = account?.let { accountManager.getUserData(it, USERDATA_SERVER_KEY) }

    val serverType: ServerType
        get() {
            val typeStr = account?.let { accountManager.getUserData(it, USERDATA_SERVER_TYPE_KEY) }
            return try {
                if (typeStr != null) ServerType.valueOf(typeStr) else ServerType.JELLYFIN
            } catch (e: Exception) {
                ServerType.JELLYFIN
            }
        }

    val token: String?
        get() = account?.let { accountManager.peekAuthToken(it, TOKEN_TYPE) }

    val isAuthenticated: Boolean
        get() = token != null

    fun storeAccount(server: String, username: String, token: String, serverType: ServerType = ServerType.JELLYFIN): Account {
        // Find existing account, if any
        var account = accountManager.getAccountsByType(ACCOUNT_TYPE).firstOrNull {
            accountManager.getUserData(it, USERDATA_SERVER_KEY).equals(server) &&
                    it.name.equals(username)
        }

        if (account == null) {
            account = Account(username, ACCOUNT_TYPE)
            accountManager.addAccountExplicitly(
                account,
                "",     // We don't keep the password, just the auth token.
                Bundle().also {
                    it.putString(USERDATA_SERVER_KEY, server)
                    it.putString(USERDATA_SERVER_TYPE_KEY, serverType.name)
                }
            )
        } else {
            accountManager.setUserData(account, USERDATA_SERVER_TYPE_KEY, serverType.name)
        }

        accountManager.setAuthToken(account, TOKEN_TYPE, token)

        return account
    }

    fun deleteAccount() {
        account?.let { account ->
            try {
                accountManager.removeAccountExplicitly(account)
            } catch (e: Exception) {
                // Account might already be removed
            }
        }
    }
}