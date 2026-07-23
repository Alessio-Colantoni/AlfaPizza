package com.alfaproject.alfapizza.view_model.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfaproject.alfapizza.MainActivity
import com.alfaproject.alfapizza.MainActivity.Companion.isAdmin
import com.alfaproject.alfapizza.MainActivity.Companion.userCode
import com.alfaproject.alfapizza.model.User
import com.alfaproject.alfapizza.network.ServerApi
import com.alfaproject.alfapizza.network.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginViewModel : ViewModel() {
    fun authenticate(context: Context, email: String, password: String, callback: (Boolean) -> Unit) {
        isAdmin = false
        userCode = -1
        viewModelScope.launch {
            val loginBody = JSONObject()
            loginBody.put("email", email)
            loginBody.put("password", password)

            ServerApi.postJson(context, "/api/login", loginBody) { response ->
                if (response != null && response.optBoolean("success")) {
                    try {
                        val userJson = response.getJSONObject("user")
                        val gson = Gson()
                        val user = gson.fromJson(userJson.toString(), User::class.java)
                        val sessionEmail = user.email ?: email.trim()
                        val token = response.optString("token")
                        if (token.isBlank()) {
                            callback(false)
                            return@postJson
                        }

                        userCode = user.code
                        isAdmin = user.isAdmin
                        MainActivity.currentUserEmail = sessionEmail

                        // Salvataggio sessione persistente
                        val sessionManager = SessionManager(context)
                        sessionManager.saveSession(user.code, user.isAdmin, sessionEmail, token)

                        android.util.Log.d("ALFA_LOGIN", "Login success for $email")
                        callback(true)
                    } catch (e: Exception) {
                        android.util.Log.e("ALFA_LOGIN", "Error parsing login user", e)
                        callback(false)
                    }
                } else {
                    android.util.Log.e("ALFA_LOGIN", "Login failed or wrong credentials")
                    callback(false)
                }
            }
        }
    }
}
