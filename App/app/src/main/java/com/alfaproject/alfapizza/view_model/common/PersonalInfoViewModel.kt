package com.alfaproject.alfapizza.view_model.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfaproject.alfapizza.MainActivity
import com.alfaproject.alfapizza.MainActivity.Companion.userCode
import com.alfaproject.alfapizza.model.User
import com.alfaproject.alfapizza.network.Constants
import com.alfaproject.alfapizza.network.ServerApi
import com.alfaproject.alfapizza.network.SessionEvents
import com.alfaproject.alfapizza.network.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONObject

class PersonalInfoViewModel : ViewModel() {
    // Inizializzazione sicura: evitiamo lateinit per scongiurare crash all'avvio
    var currentUser: User? = null

    fun getInfo(context: Context, callback: (User?) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.USERS) { jsonArray ->
                if (jsonArray != null) {
                    val gson = Gson()
                    for (i in 0 until jsonArray.length()) {
                        val temp = gson.fromJson(jsonArray.getJSONObject(i).toString(), User::class.java)
                        if (temp.code == userCode) {
                            currentUser = temp
                            callback(temp)
                            return@getJsonArray
                        }
                    }
                }
                callback(null)
            }
        }
    }

    private fun updateProfile(context: Context, updateData: JSONObject, callback: (User?) -> Unit) {
        viewModelScope.launch {
            ServerApi.postJson(context, "/api/user/update-profile", updateData) { response ->
                if (response != null && response.optBoolean("success")) {
                    val updatedUserJson = response.getJSONObject("user")
                    val updatedUser = Gson().fromJson(updatedUserJson.toString(), User::class.java)
                    currentUser = updatedUser
                    callback(updatedUser)
                } else {
                    callback(null)
                }
            }
        }
    }

    fun updatePhone(context: Context, newPhone: String, callback: (User?) -> Unit) {
        updateProfile(context, JSONObject().apply { put("phone", newPhone) }, callback)
    }

    fun updateEmail(context: Context, newEmail: String, callback: (User?) -> Unit) {
        updateProfile(context, JSONObject().apply { put("email", newEmail) }) { updatedUser ->
            if (updatedUser != null) {
                MainActivity.currentUserEmail = updatedUser.email ?: newEmail
                SessionManager(context).updateEmail(updatedUser.email ?: newEmail)
            }
            callback(updatedUser)
        }
    }

    fun updatePassword(context: Context, newPass: String, callback: (User?) -> Unit) {
        updateProfile(context, JSONObject().apply { put("password", newPass) }) { updatedUser ->
            callback(updatedUser)
            if (updatedUser != null) SessionEvents.invalidate(context)
        }
    }
}
