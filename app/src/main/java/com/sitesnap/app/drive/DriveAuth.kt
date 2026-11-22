package com.sitesnap.app.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

class DriveAuthException : Exception("Google Drive needs to be reconnected")

/**
 * Google Identity Services authorization for the `drive.file` scope, which only lets the app
 * see and manage files it created (the SiteSnap folder tree), not the user's whole Drive.
 */
object DriveAuth {
    private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    private val request: AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()

    /** May return a result with [AuthorizationResult.hasResolution] = true, meaning the consent UI must be shown. */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request).await()

    /** An access token without any UI, or null if the user has to grant access again. */
    suspend fun silentToken(context: Context): String? =
        runCatching { authorize(context) }.getOrNull()
            ?.takeIf { !it.hasResolution() }
            ?.accessToken

    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    @Suppress("DEPRECATION")
    fun email(result: AuthorizationResult): String? = result.toGoogleSignInAccount()?.email

    /** Turns a Google Play services failure into a message that says what to fix. */
    fun describe(error: Throwable): String {
        val api = error as? ApiException
            ?: return error.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed"
        val code = api.statusCode
        // Google reports a missing OAuth client as a generic error 8 whose text says UNREGISTERED_ON_API_CONSOLE.
        val unregistered = listOfNotNull(api.message, api.status.statusMessage).any { "UNREGISTERED_ON_API_CONSOLE" in it }
        return when {
            unregistered || code == CommonStatusCodes.DEVELOPER_ERROR ->
                "Google doesn't recognise this app yet. In Google Cloud, add an Android OAuth client " +
                    "for com.sitesnap.app with this app's SHA-1."
            code == CommonStatusCodes.CANCELED || code == 12501 -> "Google sign-in was cancelled."
            code == CommonStatusCodes.NETWORK_ERROR -> "No internet connection. Try again when you're online."
            code == CommonStatusCodes.SIGN_IN_REQUIRED -> "Add a Google account to this phone first (Settings > Passwords & accounts)."
            else -> "Google sign-in failed (error $code)."
        }
    }
}
