package com.alfaproject.alfapizza.view_model.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfaproject.alfapizza.model.WeekStructure
import com.alfaproject.alfapizza.network.Constants
import com.alfaproject.alfapizza.network.ServerApi
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AdminManageNextWeekViewModel : ViewModel() {
    // Stato persistente nel ViewModel per evitare perdite durante la rotazione o recomposition
    var currentWeek: WeekStructure? = null
    var nextWeek: WeekStructure? = null

    fun getInfo(
        context: Context,
        callback: (current: WeekStructure?, next: WeekStructure?, requestSucceeded: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.WEEK_STRUCTURE) { jsonArray ->
                if (jsonArray == null) {
                    callback(null, null, false)
                    return@getJsonArray
                }
                if (jsonArray.length() == 0) {
                    callback(null, null, true)
                    return@getJsonArray
                }

                val parsed = runCatching {
                    val gson = Gson()
                    val weeks = mutableListOf<WeekStructure>()
                    for (i in 0 until jsonArray.length()) {
                        weeks.add(gson.fromJson(jsonArray.getJSONObject(i).toString(), WeekStructure::class.java))
                    }
                    val parsedCurrent = weeks.find { !it.isNext }
                    val parsedNext = weeks.find { it.isNext }
                    if (parsedCurrent == null || parsedNext == null ||
                        parsedCurrent.listShift.size < 7 || parsedNext.listShift.size < 7
                    ) {
                        null
                    } else {
                        parsedCurrent to parsedNext
                    }
                }.getOrNull()

                if (parsed == null) {
                    callback(null, null, true)
                } else {
                    currentWeek = parsed.first
                    nextWeek = parsed.second
                    callback(parsed.first, parsed.second, true)
                }
            }
        }
    }

    private fun updateWeek(
        context: Context,
        updatedWeek: WeekStructure,
        callback: (WeekStructure?, Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val candidateCurrent = if (updatedWeek.isNext) currentWeek else updatedWeek
            val candidateNext = if (updatedWeek.isNext) updatedWeek else nextWeek
            if (candidateCurrent == null || candidateNext == null) {
                callback(null, false)
                return@launch
            }
            val arr = JSONArray().apply {
                put(JSONObject(Gson().toJson(candidateCurrent)))
                put(JSONObject(Gson().toJson(candidateNext)))
            }
            ServerApi.postString(context, Constants.Endpoints.WEEK_STRUCTURE, arr.toString()) { response ->
                if (response != null) {
                    currentWeek = candidateCurrent
                    nextWeek = candidateNext
                    callback(updatedWeek, true)
                } else {
                    callback(null, false)
                }
            }
        }
    }

    private fun selectedWeek(isNext: Boolean): WeekStructure? = if (isNext) nextWeek else currentWeek

    fun changeDay(context: Context, isNext: Boolean, input: Int, callback: (WeekStructure?, Boolean) -> Unit) {
        selectedWeek(isNext)?.let {
            val newWeek = it.copy(lastDayConstraint = input)
            updateWeek(context, newWeek, callback)
        } ?: callback(null, false)
    }

    fun changePublicationDay(
        context: Context,
        input: Int,
        callback: (WeekStructure?, WeekStructure?, Boolean) -> Unit
    ) {
        val current = currentWeek
        val next = nextWeek
        if (current == null || next == null) {
            callback(null, null, false)
            return
        }
        val candidateCurrent = current.copy(lastDayConstraint = input)
        val candidateNext = next.copy(lastDayConstraint = input)
        viewModelScope.launch {
            val arr = JSONArray().apply {
                put(JSONObject(Gson().toJson(candidateCurrent)))
                put(JSONObject(Gson().toJson(candidateNext)))
            }
            ServerApi.postString(context, Constants.Endpoints.WEEK_STRUCTURE, arr.toString()) { response ->
                if (response != null) {
                    currentWeek = candidateCurrent
                    nextWeek = candidateNext
                    callback(candidateCurrent, candidateNext, true)
                } else {
                    callback(null, null, false)
                }
            }
        }
    }

    fun changeDailyRider(context: Context, isNext: Boolean, index: Int, value: Int, callback: (WeekStructure?, Boolean) -> Unit) {
        selectedWeek(isNext)?.let {
            if (index !in it.listShift.indices) {
                callback(null, false)
                return
            }
            val newList = ArrayList(it.listShift)
            newList[index] = value
            val newWeek = it.copy(listShift = newList)
            updateWeek(context, newWeek, callback)
        } ?: callback(null, false)
    }

    fun changeMin(context: Context, isNext: Boolean, value: Int, callback: (WeekStructure?, Boolean) -> Unit) {
        selectedWeek(isNext)?.let {
            val newWeek = it.copy(minRider = value)
            updateWeek(context, newWeek, callback)
        } ?: callback(null, false)
    }

    fun changeMax(context: Context, isNext: Boolean, value: Int, callback: (WeekStructure?, Boolean) -> Unit) {
        selectedWeek(isNext)?.let {
            val newWeek = it.copy(maxRider = value)
            updateWeek(context, newWeek, callback)
        } ?: callback(null, false)
    }
}
