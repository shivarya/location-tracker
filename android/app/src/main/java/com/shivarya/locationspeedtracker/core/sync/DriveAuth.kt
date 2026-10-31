package com.shivarya.locationspeedtracker.core.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drive access through Google's Authorization API: only the `drive.appdata` scope, so SpeedTrack can reach nothing but
 * its own hidden app folder in the user's Drive. Needs an Android OAuth client for this package + signing SHA-1 in the
 * Cloud project (no client id or secret ships in the app).
 */
@Singleton
class DriveAuth @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val client get() = Identity.getAuthorizationClient(context)

    private fun request(account: String?) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .apply { if (account != null) setAccount(Account(account, ACCOUNT_TYPE)) }
        .build()

    /** A token, or (first time, or after access was removed) a consent screen the UI must show. */
    suspend fun authorize(account: String?): AuthorizationResult = client.authorize(request(account)).await()

    fun resultFromIntent(data: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    /** For background sync: the token while access is still granted, else null (the user has to reconnect). */
    suspend fun token(account: String): String? = authorize(account).takeIf { !it.hasResolution() }?.accessToken

    /** Drops a cached token Drive rejected, so the next [token] fetches a fresh one. */
    suspend fun clear(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }

    suspend fun revoke(account: String) {
        client.revokeAccess(
            RevokeAccessRequest.builder().setAccount(Account(account, ACCOUNT_TYPE)).setScopes(listOf(Scope(SCOPE))).build(),
        ).await()
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val ACCOUNT_TYPE = "com.google"
    }
}
