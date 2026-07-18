package com.alfaproject.alfapizza.view_model.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfaproject.alfapizza.MainActivity.Companion.isAdmin
import com.alfaproject.alfapizza.MainActivity.Companion.userCode
import com.alfaproject.alfapizza.model.MyCalendar
import com.alfaproject.alfapizza.model.Swap
import com.alfaproject.alfapizza.model.User
import com.alfaproject.alfapizza.model.Constraint
import com.alfaproject.alfapizza.network.Constants
import com.alfaproject.alfapizza.network.ServerApi
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.alfaproject.alfapizza.time.AppTime

class HomeViewModel : ViewModel() {
    lateinit var jsonarr: JSONArray
    lateinit var jsonarr2: JSONArray
    lateinit var jsonarr3: JSONArray
    var glob: Int = -1
    var calendars: MutableList<MyCalendar> = mutableListOf()
    var swaps: MutableList<Swap> = mutableListOf()
    var notifies: MutableList<String> = mutableListOf()
    var users: MutableMap<Int, String> = mutableMapOf()
    var constraints: MutableList<Constraint> = mutableListOf()
    var myShiftsThisWeekList: ArrayList<Int> = arrayListOf()
    var myShiftsNextWeekList: ArrayList<Int> = arrayListOf()
    lateinit var thisWeekCalendar: MyCalendar
    lateinit var nextWeekCalendar: MyCalendar

    fun getCalendars(context: Context, callback: (MutableList<MyCalendar>, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.CALENDARS) { jsonArray ->
                if (jsonArray != null) {
                    jsonarr = jsonArray
                    val gson = Gson()
                    calendars.clear()
                    for (i in 0 until jsonArray.length()) {
                        val calendar = gson.fromJson(
                            jsonArray.getJSONObject(i).toString(),
                            MyCalendar::class.java
                        )
                        calendars.add(calendar)
                        if (calendar.isNext) nextWeekCalendar = calendar
                        else thisWeekCalendar = calendar
                    }
                }
                callback(calendars, jsonArray != null)
            }
        }
    }

    fun getSwaps(context: Context, callback: (MutableList<Swap>, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.SWAPS) { jsonArray ->
                if (jsonArray != null) {
                    jsonarr3 = jsonArray
                    val gson = Gson()
                    swaps.clear()
                    for (i in 0 until jsonArray.length()) {
                        val swap = gson.fromJson(
                            jsonArray.getJSONObject(i).toString(),
                            Swap::class.java
                        )
                        swaps.add(swap)
                    }
                }
                callback(swaps, jsonArray != null)
            }
        }
    }

    fun getUsers(context: Context, callback: (MutableMap<Int, String>, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.USERS) { jsonArray ->
                if (jsonArray != null) {
                    jsonarr2 = jsonArray
                    val gson = Gson()
                    users.clear()
                    for (i in 0 until jsonArray.length()) {
                        val user = gson.fromJson(
                            jsonArray.getJSONObject(i).toString(),
                            User::class.java
                        )
                        if (user.code == userCode) {
                            glob = i
                        }
                        users[user.code] = user.name + " " + user.surname
                    }
                }
                callback(users, jsonArray != null)
            }
        }
    }

    fun getConstraints(context: Context, callback: (MutableList<Constraint>, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.CONSTRAINTS) { jsonArray ->
                if (jsonArray != null) {
                    val gson = Gson()
                    constraints.clear()
                    for (i in 0 until jsonArray.length()) {
                        constraints.add(gson.fromJson(jsonArray.getJSONObject(i).toString(), Constraint::class.java))
                    }
                }
                callback(constraints, jsonArray != null)
            }
        }
    }

    fun getNotifies(context: Context, callback: (MutableList<String>) -> Unit) {
        viewModelScope.launch {
            val allNotifies = com.alfaproject.alfapizza.MainActivity.allNotifies
            notifies.clear()

            if (!::jsonarr2.isInitialized || glob == -1) {
                callback(notifies)
                return@launch
            }

            val currentUser = jsonarr2.getJSONObject(glob)
            val lastUserAccessStr = currentUser.optString("lastAccess", "")
            val lastUserAccessDate = parseDateTime(lastUserAccessStr)

            if (::thisWeekCalendar.isInitialized) {
                val thisWeekUpdate = parseDateTime(thisWeekCalendar.lastUpdate)
                if (lastUserAccessDate.before(thisWeekUpdate)) {
                    notifies.add(allNotifies[0])
                }
            }

            if (::nextWeekCalendar.isInitialized) {
                val nextWeekUpdate = parseDateTime(nextWeekCalendar.lastUpdate)
                val publicationDay = nextWeekCalendar.publicationDay
                val today = getDayOfWeek()

                if (isAdmin) {
                    if (lastUserAccessDate.before(nextWeekUpdate)) notifies.add(allNotifies[1])
                } else {
                    val publicationDate = getCurrentWeekDate(publicationDay)
                    val visibleEventDate = if (nextWeekUpdate.after(publicationDate)) nextWeekUpdate else publicationDate
                    if (today >= publicationDay && lastUserAccessDate.before(visibleEventDate)) {
                        notifies.add(allNotifies[1])
                    }
                }
            }

            if (::jsonarr3.isInitialized) {
                var newSwapForAdmin = false
                var newSwapThisWeek = false
                var newSwapNextWeek = false

                for (i in 0 until jsonarr3.length()) {
                    val swapJson = jsonarr3.getJSONObject(i)
                    val requestDate = parseDateTime(swapJson.optString("requestDate", ""))
                    val acceptedDate = parseDateTime(swapJson.optString("acceptedAt", ""))

                    if (isAdmin) {
                        val adminEventDate = if (acceptedDate.after(requestDate)) acceptedDate else requestDate
                        if (swapJson.optBoolean("isReadyForAdmin") && lastUserAccessDate.before(adminEventDate)) {
                            newSwapForAdmin = true
                        }
                    } else if (swapJson.optInt("fromRider") != userCode && lastUserAccessDate.before(requestDate)) {
                        if (swapJson.optBoolean("isNext")) newSwapNextWeek = true
                        else newSwapThisWeek = true
                    }
                }

                if (newSwapForAdmin) notifies.add(allNotifies[2])
                if (newSwapThisWeek) notifies.add(allNotifies[3])
                if (newSwapNextWeek) notifies.add(allNotifies[4])
            }

            callback(notifies)
        }
    }

    fun updateUserAccess(context: Context, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!::jsonarr2.isInitialized || glob == -1) {
                callback(false)
                return@launch
            }

            val updateObj = JSONObject().apply {
                put("code", userCode)
                put("lastAccess", true) // Il server gestirà il timestamp
            }

            val updateArray = JSONArray().apply { put(updateObj) }

            ServerApi.postString(context, Constants.Endpoints.USERS, updateArray.toString()) { response ->
                callback(response != null)
            }
        }
    }

    private fun parseDateTime(dateStr: String): Date {
        if (dateStr.isBlank()) return Date(0)
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
                timeZone = AppTime.timeZone()
            }
            sdf.parse(dateStr) ?: Date(0)
        } catch (e: Exception) {
            Date(0)
        }
    }

    fun getDayOfWeek(): Int {
        return AppTime.dayOfWeek()
    }

    private fun getCurrentWeekDate(dayIndex: Int): Date {
        return AppTime.dateInCurrentWeek(dayIndex)
    }

    fun filterList(context: Context) {
        myShiftsThisWeekList.clear()
        myShiftsNextWeekList.clear()
        if (::thisWeekCalendar.isInitialized) {
            for (day in thisWeekCalendar.days) {
                for (shift in day.listShift) {
                    if (shift.code == userCode) myShiftsThisWeekList.add(day.day)
                }
            }
        }
        if (::nextWeekCalendar.isInitialized) {
            for (day in nextWeekCalendar.days) {
                for (shift in day.listShift) {
                    if (shift.code == userCode) myShiftsNextWeekList.add(day.day)
                }
            }
        }
    }

    fun generateCalendar(context: Context, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonObject(context, "/api/generateCalendar") { response ->
                callback(response != null)
            }
        }
    }

    fun generateCurrentCalendar(context: Context, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonObject(context, "/api/generateCurrentCalendar") { response ->
                callback(response != null)
            }
        }
    }

    fun saveCurrentCalendar(context: Context, updatedCalendar: MyCalendar, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val allCalendars = JSONArray()
            val gson = Gson()
            allCalendars.put(JSONObject(gson.toJson(updatedCalendar)))

            ServerApi.postString(context, Constants.Endpoints.CALENDARS, allCalendars.toString()) { response ->
                callback(response != null)
            }
        }
    }
}
