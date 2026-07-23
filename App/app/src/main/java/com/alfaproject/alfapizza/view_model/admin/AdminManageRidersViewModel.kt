package com.alfaproject.alfapizza.view_model.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfaproject.alfapizza.model.User
import com.alfaproject.alfapizza.network.Constants
import com.alfaproject.alfapizza.network.ServerApi
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Random

class AdminManageRidersViewModel : ViewModel() {
    fun getRiders(context: Context, callback: (MutableList<User>?, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.USERS) { jsonArray ->
                if (jsonArray == null) {
                    callback(null, false)
                    return@getJsonArray
                }

                val usersList = mutableListOf<User>()
                val parsed = runCatching {
                    val gson = Gson()
                    for (i in 0 until jsonArray.length()) {
                        val user = gson.fromJson(jsonArray.getJSONObject(i).toString(), User::class.java)
                        if (!user.isAdmin) usersList.add(user)
                    }
                }
                callback(usersList.takeIf { parsed.isSuccess }, parsed.isSuccess)
            }
        }
    }

    fun addRider(
        context: Context,
        user: User,
        callback: (operationSucceeded: Boolean, riders: MutableList<User>?, refreshSucceeded: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val userJson = JSONObject(Gson().toJson(user))
            ServerApi.postJson(context, Constants.Endpoints.USERS, userJson) { response ->
                val success = response != null
                if (!success) {
                    callback(false, null, false)
                    return@postJson
                }
                getRiders(context) { riders, refreshSucceeded ->
                    callback(true, riders, refreshSucceeded)
                }
            }
        }
    }

    fun deleteRider(
        context: Context,
        user: User,
        callback: (operationSucceeded: Boolean, riders: MutableList<User>?, refreshSucceeded: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            ServerApi.delete(context, "/api/users/${user.code}") { success ->
                if (!success) {
                    callback(false, null, false)
                    return@delete
                }
                getRiders(context) { riders, refreshSucceeded ->
                    callback(true, riders, refreshSucceeded)
                }
            }
        }
    }

    fun generateRandomPassword(length: Int): String {
        val allowedChars = "0123456789"
        return (1..length).map { allowedChars.random() }.joinToString("")
    }

    fun generateRandomCode(): Int = Random().nextInt(900000) + 100000
}
