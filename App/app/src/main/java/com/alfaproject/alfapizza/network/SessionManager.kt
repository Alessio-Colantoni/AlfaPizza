package com.alfaproject.alfapizza.network

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alfaproject.alfapizza.MainActivity

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "AlfaPizzaSecurePrefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSession(userCode: Int, isAdmin: Boolean, email: String, token: String) {
        prefs.edit().apply {
            putInt("user_code", userCode)
            putBoolean("is_admin", isAdmin)
            putString("user_email", email)
            putString("auth_token", token)
            apply()
        }
    }

    fun getUserCode(): Int = prefs.getInt("user_code", -1)
    fun isAdmin(): Boolean = prefs.getBoolean("is_admin", false)
    fun getUserEmail(): String? = prefs.getString("user_email", null)
    fun getAuthToken(): String? = prefs.getString("auth_token", null)

    fun updateEmail(email: String) {
        prefs.edit().putString("user_email", email).apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = getUserCode() != -1 && !getAuthToken().isNullOrBlank()
}

object SessionEvents {
    var unauthorizedVersion by mutableIntStateOf(0)
        private set

    fun invalidate(context: Context) {
        SessionManager(context.applicationContext).clearSession()
        MainActivity.userCode = -1
        MainActivity.isAdmin = false
        MainActivity.currentUserEmail = null
        unauthorizedVersion++
    }
}
