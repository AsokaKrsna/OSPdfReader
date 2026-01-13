package com.ospdf.reader.data.cloud

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication state for Google Drive.
 */
sealed class AuthState {
    object NotSignedIn : AuthState()
    object SigningIn : AuthState()
    data class SignedIn(val account: GoogleSignInAccount) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Manages Google Drive authentication using Google Sign-In.
 */
@Singleton
class GoogleDriveAuth @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.NotSignedIn)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private var googleSignInClient: GoogleSignInClient? = null
    private var driveService: Drive? = null
    
    /**
     * Initializes the Google Sign-In client.
     */
    private fun getSignInClient(): GoogleSignInClient {
        if (googleSignInClient == null) {
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE))
                .build()
            
            googleSignInClient = GoogleSignIn.getClient(context, signInOptions)
        }
        return googleSignInClient!!
    }
    
    /**
     * Gets the sign-in intent to launch the Google Sign-In flow.
     */
    fun getSignInIntent(): Intent {
        _authState.value = AuthState.SigningIn
        return getSignInClient().signInIntent
    }
    
    /**
     * Handles the result from the sign-in activity.
     */
    suspend fun handleSignInResult(data: Intent?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("GoogleDriveAuth", "Handling sign-in result, data: $data")
            
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            
            // Check if the task completed successfully
            if (task.isSuccessful) {
                val account = task.result
                if (account != null) {
                    android.util.Log.d("GoogleDriveAuth", "Sign-in successful: ${account.email}")
                    initializeDriveService(account)
                    _authState.value = AuthState.SignedIn(account)
                    Result.success(Unit)
                } else {
                    android.util.Log.e("GoogleDriveAuth", "Sign-in failed: No account returned")
                    _authState.value = AuthState.Error("Sign-in failed: No account returned")
                    Result.failure(Exception("No account returned"))
                }
            } else {
                val exception = task.exception
                val errorMessage = when (exception) {
                    is com.google.android.gms.common.api.ApiException -> {
                        val statusCode = exception.statusCode
                        android.util.Log.e("GoogleDriveAuth", "Sign-in failed with status code: $statusCode")
                        when (statusCode) {
                            12500 -> "Sign-in cancelled or failed. Please check your Google Play Services."
                            12501 -> "Sign-in was cancelled by user."
                            12502 -> "Sign-in is currently in progress."
                            10 -> "Developer error: Check OAuth configuration in Google Cloud Console."
                            else -> "Sign-in failed with code $statusCode: ${exception.message}"
                        }
                    }
                    else -> "Sign-in failed: ${exception?.message ?: "Unknown error"}"
                }
                android.util.Log.e("GoogleDriveAuth", errorMessage, exception)
                _authState.value = AuthState.Error(errorMessage)
                Result.failure(exception ?: Exception(errorMessage))
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val errorMessage = "Sign-in failed (code ${e.statusCode}): ${e.message}"
            android.util.Log.e("GoogleDriveAuth", errorMessage, e)
            _authState.value = AuthState.Error(errorMessage)
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveAuth", "Sign-in exception", e)
            _authState.value = AuthState.Error("Sign-in failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Checks if user is already signed in silently.
     */
    suspend fun silentSignIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE))) {
                initializeDriveService(account)
                _authState.value = AuthState.SignedIn(account)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Signs out from Google.
     */
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            getSignInClient().signOut()
            driveService = null
            _authState.value = AuthState.NotSignedIn
        }
    }

    /**
     * Initializes the Drive API service.
     */
    private fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE)
        ).apply {
            selectedAccount = account.account
        }
        
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("OSPdfReader")
            .build()
    }
    
    /**
     * Gets the Drive service for API calls.
     */
    fun getDriveService(): Drive? = driveService
    
    /**
     * Checks if the user is currently signed in.
     */
    fun isSignedIn(): Boolean = _authState.value is AuthState.SignedIn
    
    /**
     * Gets the current signed-in account email.
     */
    fun getAccountEmail(): String? {
        return (_authState.value as? AuthState.SignedIn)?.account?.email
    }
}
