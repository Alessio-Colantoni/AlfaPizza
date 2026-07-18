package com.alfaproject.alfapizza.view_model.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfaproject.alfapizza.model.Swap
import com.alfaproject.alfapizza.model.User
import com.alfaproject.alfapizza.network.Constants
import com.alfaproject.alfapizza.network.ServerApi
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONObject

class AdminManageSwapRequestsViewModel : ViewModel() {
    private val list: MutableList<Swap> = mutableListOf()
    private val users: MutableMap<Int, String> = mutableMapOf()

    fun getInfo(context: Context, callback: (List<Swap>?, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.SWAPS) { jsonArray ->
                if (jsonArray == null) {
                    callback(null, false)
                    return@getJsonArray
                }

                val parsed = runCatching {
                    val gson = Gson()
                    list.clear()
                    for (i in 0 until jsonArray.length()) {
                        val swap = gson.fromJson(
                            jsonArray.getJSONObject(i).toString(),
                            Swap::class.java
                        )
                        if (swap.isReadyForAdmin) {
                            list.add(swap)
                        }
                    }
                }
                callback(list.toList().takeIf { parsed.isSuccess }, parsed.isSuccess)
            }
        }
    }

    fun getUsers(context: Context, callback: (Map<Int, String>?, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.USERS) { jsonArray ->
                if (jsonArray == null) {
                    callback(null, false)
                    return@getJsonArray
                }

                val parsed = runCatching {
                    val gson = Gson()
                    users.clear()
                    for (i in 0 until jsonArray.length()) {
                        val user = gson.fromJson(
                            jsonArray.getJSONObject(i).toString(),
                            User::class.java
                        )
                        users[user.code] = user.name + " " + user.surname
                    }
                }
                callback(users.toMap().takeIf { parsed.isSuccess }, parsed.isSuccess)
            }
        }
    }

    fun loadData(
        context: Context,
        callback: (swaps: List<Swap>?, users: Map<Int, String>?, requestSucceeded: Boolean) -> Unit
    ) {
        getInfo(context) { swaps, swapsSucceeded ->
            if (!swapsSucceeded || swaps == null) {
                callback(null, null, false)
                return@getInfo
            }
            getUsers(context) { loadedUsers, usersSucceeded ->
                callback(swaps, loadedUsers, usersSucceeded && loadedUsers != null)
            }
        }
    }

    fun approveSwap(context: Context, swap: Swap, callback: (List<Swap>, Boolean) -> Unit) {
        viewModelScope.launch {
            val swapId = swap._id.orEmpty()
            if (swapId.isEmpty()) {
                callback(list.toList(), false)
                return@launch
            }
            val body = JSONObject()
            body.put("swapId", swapId)

            ServerApi.postJson(context, "/api/swaps/approve", body) { response ->
                val success = response != null && response.optBoolean("success")
                if (success) {
                    list.remove(swap)
                }
                callback(list.toList(), success)
            }
        }
    }

    fun rejectSwap(context: Context, swap: Swap, callback: (List<Swap>, Boolean) -> Unit) {
        viewModelScope.launch {
            val swapId = swap._id.orEmpty()
            if (swapId.isEmpty()) {
                callback(list.toList(), false)
                return@launch
            }
            ServerApi.delete(context, "/api/swaps/$swapId") { success ->
                if (success) {
                    list.remove(swap)
                }
                callback(list.toList(), success)
            }
        }
    }
}
