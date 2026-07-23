package com.alfaproject.alfapizza.view_model.rider

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfaproject.alfapizza.MainActivity.Companion.userCode
import com.alfaproject.alfapizza.model.Constraint
import com.alfaproject.alfapizza.network.Constants
import com.alfaproject.alfapizza.network.ServerApi
import com.alfaproject.alfapizza.network.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class RiderManageYourConstraintsViewModel : ViewModel() {
    var constraints: MutableList<Constraint> = mutableListOf()
    private var hasLoadedConstraints = false
    private val mutationQueue = ConstraintMutationQueue()

    fun getInfo(context: Context, callback: (MutableList<Constraint>, Boolean) -> Unit) {
        viewModelScope.launch {
            ServerApi.getJsonArray(context, Constants.Endpoints.CONSTRAINTS) { jsonArray ->
                if (jsonArray != null) {
                    val gson = Gson()
                    constraints.clear()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val temp = gson.fromJson(obj.toString(), Constraint::class.java)

                        if (temp.riderCode == userCode) {
                            constraints.add(temp)
                        }
                    }
                    hasLoadedConstraints = true
                    callback(constraints.toMutableList(), true)
                } else {
                    hasLoadedConstraints = false
                    callback(constraints.toMutableList(), false)
                }
            }
        }
    }

    fun addConstraint(context: Context, constraint: Constraint, callback: (MutableList<Constraint>, Boolean) -> Unit) {
        viewModelScope.launch {
            val (updatedConstraints, success) = mutationQueue.run {
                if (!hasLoadedConstraints) {
                    return@run constraints.toMutableList() to false
                }

                val finalUserCode = SessionManager(context).getUserCode()
                val finalConstraint = constraint.copy(riderCode = finalUserCode, isNext = true)
                val candidate = replaceFutureConstraint(constraints.toList(), finalConstraint)
                val saved = postFutureConstraints(context, candidate)

                if (saved) {
                    constraints.clear()
                    constraints.addAll(candidate)
                }
                constraints.toMutableList() to saved
            }
            callback(updatedConstraints, success)
        }
    }

    fun removeConstraint(context: Context, constraint: Constraint, callback: (MutableList<Constraint>, Boolean) -> Unit) {
        viewModelScope.launch {
            val (updatedConstraints, success) = mutationQueue.run {
                if (!hasLoadedConstraints || !constraint.isNext) {
                    return@run constraints.toMutableList() to false
                }

                val candidate = removeFutureConstraint(constraints.toList(), constraint.day)
                val saved = postFutureConstraints(context, candidate)
                if (saved) {
                    constraints.clear()
                    constraints.addAll(candidate)
                }
                constraints.toMutableList() to saved
            }
            callback(updatedConstraints, success)
        }
    }

    private suspend fun postFutureConstraints(context: Context, candidate: List<Constraint>): Boolean {
        val gson = Gson()
        val payload = JSONArray().apply {
            futureConstraints(candidate).forEach { constraint ->
                put(JSONObject(gson.toJson(constraint)))
            }
        }
        val result = CompletableDeferred<Boolean>()
        ServerApi.postString(context, Constants.Endpoints.CONSTRAINTS, payload.toString()) { response ->
            result.complete(response != null)
        }
        return result.await()
    }
}
