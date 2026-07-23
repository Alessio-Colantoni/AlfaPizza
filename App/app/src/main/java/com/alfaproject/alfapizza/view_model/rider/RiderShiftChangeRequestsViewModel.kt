package com.alfaproject.alfapizza.view_model.rider

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfaproject.alfapizza.MainActivity.Companion.userCode
import com.alfaproject.alfapizza.model.MyCalendar
import com.alfaproject.alfapizza.model.Swap
import com.alfaproject.alfapizza.model.User
import com.alfaproject.alfapizza.network.Constants
import com.alfaproject.alfapizza.network.ServerApi
import com.alfaproject.alfapizza.network.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class RiderShiftChangeRequestsViewModel : ViewModel() {
    var receivedRequestsList: MutableList<Swap> = mutableListOf()
    var sentRequestsList: MutableList<Swap> = mutableListOf()
    var filteredlist: MutableList<Swap> = mutableListOf()
    var users: MutableMap<Int, String> = mutableMapOf()
    var myShiftsThisWeekList by mutableStateOf<List<Int>>(listOf<Int>())
    var myShiftsNextWeekList by mutableStateOf<List<Int>>(listOf<Int>())

    private fun getSwapId(swap: Swap): String = swap._id.orEmpty()

    private fun hasShiftForSwap(swap: Swap): Boolean {
        val shifts = if (swap.isNext) myShiftsNextWeekList else myShiftsThisWeekList
        return shifts.contains(swap.toDay) && !shifts.contains(swap.fromDay)
    }

    fun getSwaps(context: Context, callback: (MutableList<Swap>, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.SWAPS) { jsonArray ->
                val list = mutableListOf<Swap>()
                if (jsonArray != null) {
                    val gson = Gson()
                    for (i in 0 until jsonArray.length()) {
                        val swap = gson.fromJson(
                            jsonArray.getJSONObject(i).toString(),
                            Swap::class.java
                        )
                        list.add(swap)
                    }
                }
                callback(list, jsonArray != null)
            }
        }
    }

    fun getUsers(context: Context, callback: (MutableMap<Int, String>, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.USERS) { jsonArray ->
                if (jsonArray != null) {
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
                callback(users, jsonArray != null)
            }
        }
    }

    fun acceptSwap(context: Context, swap: Swap, callback: (MutableList<Swap>, Boolean) -> Unit) {
        viewModelScope.launch {
            val swapId = getSwapId(swap)
            if (swapId.isEmpty()) {
                callback(receivedRequestsList, false)
                return@launch
            }

            val body = JSONObject()
            body.put("swapId", swapId)

            ServerApi.postJson(context, "/api/swaps/accept", body) { response ->
                if (response != null && response.optBoolean("success")) {
                    receivedRequestsList.removeAll { getSwapId(it) == swapId }
                    filterList(context) { result, _ -> callback(result, true) }
                } else {
                    filterList(context) { result, _ -> callback(result, false) }
                }
            }
        }
    }

    fun filterList(context: Context, callback: (MutableList<Swap>, Boolean) -> Unit) {
        getSwaps(context) { result, success ->
            if (!success) {
                callback(receivedRequestsList, false)
                return@getSwaps
            }
            receivedRequestsList.clear()
            sentRequestsList.clear()
            for (item in result) {
                if (item.fromRider != userCode) {
                    if (!item.isReadyForAdmin && item.firstRiderAccepted == -1 && hasShiftForSwap(item)) {
                        receivedRequestsList.add(item)
                    }
                } else {
                    sentRequestsList.add(item)
                }
            }
            filteredlist = receivedRequestsList
            callback(receivedRequestsList, true)
        }
    }

    fun getCalendars(context: Context, callback: (MutableList<MyCalendar>, Boolean) -> Unit) {
        viewModelScope.launch {
            val sessionManager = SessionManager(context)
            val finalUserCode = sessionManager.getUserCode()

            ServerApi.getJsonArray(context, Constants.Endpoints.CALENDARS) { jsonArray ->
                val calendars = mutableListOf<MyCalendar>()
                if (jsonArray != null) {
                    val thisList = mutableListOf<Int>()
                    val nextList = mutableListOf<Int>()
                    val gson = Gson()
                    for (i in 0 until jsonArray.length()) {
                        val cal = gson.fromJson(jsonArray.getJSONObject(i).toString(), MyCalendar::class.java)
                        calendars.add(cal)
                        for (day in cal.days) {
                            for (shift in day.listShift) {
                                if (shift.code == finalUserCode) {
                                    if (cal.isNext) {
                                        if (!nextList.contains(day.day)) nextList.add(day.day)
                                    } else {
                                        if (!thisList.contains(day.day)) thisList.add(day.day)
                                    }
                                }
                            }
                        }
                    }
                    myShiftsThisWeekList = thisList
                    myShiftsNextWeekList = nextList
                    android.util.Log.d("SWAPS", "User $finalUserCode shifts - This: $myShiftsThisWeekList, Next: $myShiftsNextWeekList")
                }
                callback(calendars, jsonArray != null)
            }
        }
    }

    fun loadData(context: Context, callback: (Boolean) -> Unit) {
        getCalendars(context) { _, calendarsLoaded ->
            if (!calendarsLoaded) {
                callback(false)
                return@getCalendars
            }
            filterList(context) { _, swapsLoaded ->
                if (!swapsLoaded) {
                    callback(false)
                    return@filterList
                }
                getUsers(context) { _, usersLoaded -> callback(usersLoaded) }
            }
        }
    }

    fun newRequest(context: Context, swap: Swap, callback: (MutableList<Swap>, Boolean) -> Unit) {
        viewModelScope.launch {
            val sessionManager = SessionManager(context)
            val finalUserCode = sessionManager.getUserCode()
            val finalSwap = swap.copy(fromRider = finalUserCode)

            val gson = Gson()
            val payload = JSONArray().apply { put(JSONObject(gson.toJson(finalSwap))) }

            android.util.Log.d("SWAPS", "Sending Swap Request for rider $finalUserCode: ${payload.toString()}")

            ServerApi.postString(context, Constants.Endpoints.SWAPS, payload.toString()) { response ->
                if (response != null) {
                    android.util.Log.d("SWAPS", "Swap Request SUCCESS")
                    filterList(context) { result, _ -> callback(result, true) }
                } else {
                    android.util.Log.e("SWAPS", "Swap Request FAILED (Server returned null)")
                    filterList(context) { result, _ -> callback(result, false) }
                }
            }
        }
    }

    fun deleteSwap(context: Context, swap: Swap, callback: (MutableList<Swap>, Boolean) -> Unit) {
        viewModelScope.launch {
            val swapId = getSwapId(swap)
            if (swapId.isEmpty()) {
                callback(sentRequestsList, false)
                return@launch
            }

            ServerApi.delete(context, "/api/swaps/$swapId") { success ->
                if (success) {
                    sentRequestsList.removeAll { getSwapId(it) == swapId }
                }
                filterList(context) { result, _ -> callback(result, success) }
            }
        }
    }
}
