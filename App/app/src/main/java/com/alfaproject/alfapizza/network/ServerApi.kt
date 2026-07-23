package com.alfaproject.alfapizza.network

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject

object ServerApi {

    private fun <T : Request<*>> T.withDefaultTimeout(): T {
        retryPolicy = DefaultRetryPolicy(
            Constants.NETWORK_TIMEOUT_MS,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        return this
    }

    private fun getAuthHeaders(context: Context): MutableMap<String, String> {
        val sessionManager = SessionManager(context)
        val headers = HashMap<String, String>()
        sessionManager.getAuthToken()?.takeIf { it.isNotBlank() }?.let { token ->
            headers["Authorization"] = "Bearer $token"
        }
        return headers
    }

    private fun handleError(context: Context, error: VolleyError) {
        if (error.networkResponse?.statusCode == 401) {
            SessionEvents.invalidate(context)
        }
    }

    fun getJsonArray(context: Context, path: String, callback: (JSONArray?) -> Unit) {
        val url = "${Constants.BASE_URL}$path"
        android.util.Log.d("SERVER_API", "GET Array: $url")
        val queue = Volley.newRequestQueue(context)
        val request = object : JsonArrayRequest(Method.GET, url, null,
            Response.Listener {
                android.util.Log.d("SERVER_API", "GET Array SUCCESS: $path")
                callback(it)
            },
            Response.ErrorListener {
                android.util.Log.e("SERVER_API", "GET Array ERROR: $path - ${it.message}")
                handleError(context, it)
                callback(null)
            }) {
            override fun getHeaders(): MutableMap<String, String> = getAuthHeaders(context)
        }.withDefaultTimeout()
        queue.add(request)
    }

    fun postJson(context: Context, path: String, body: JSONObject, callback: (JSONObject?) -> Unit) {
        val url = "${Constants.BASE_URL}$path"
        android.util.Log.d("SERVER_API", "POST Json: $url")
        val queue = Volley.newRequestQueue(context)
        val request = object : JsonObjectRequest(Method.POST, url, body,
            Response.Listener {
                android.util.Log.d("SERVER_API", "POST Json SUCCESS: $path")
                callback(it)
            },
            Response.ErrorListener {
                android.util.Log.e("SERVER_API", "POST Json ERROR: $path - Code: ${it.networkResponse?.statusCode} - Msg: ${it.message}")
                handleError(context, it)
                callback(null)
            }) {
            override fun getHeaders(): MutableMap<String, String> = getAuthHeaders(context)
        }.withDefaultTimeout()
        queue.add(request)
    }

    fun postString(context: Context, path: String, body: String, callback: (String?) -> Unit) {
        val url = "${Constants.BASE_URL}$path"
        android.util.Log.d("SERVER_API", "POST String: $url")
        val queue = Volley.newRequestQueue(context)
        val request = object : StringRequest(Method.POST, url,
            Response.Listener {
                android.util.Log.d("SERVER_API", "POST String SUCCESS: $path")
                callback(it)
            },
            Response.ErrorListener {
                android.util.Log.e("SERVER_API", "POST String ERROR: $path - Code: ${it.networkResponse?.statusCode} - Msg: ${it.message}")
                handleError(context, it)
                callback(null)
            }) {
            override fun getHeaders(): MutableMap<String, String> = getAuthHeaders(context)
            override fun getBodyContentType(): String = "application/json; charset=utf-8"
            override fun getBody(): ByteArray = body.toByteArray(Charsets.UTF_8)
        }.withDefaultTimeout()
        queue.add(request)
    }

    fun getJsonObject(context: Context, path: String, callback: (JSONObject?) -> Unit) {
        val url = "${Constants.BASE_URL}$path"
        android.util.Log.d("SERVER_API", "GET Object: $url")
        val queue = Volley.newRequestQueue(context)
        val request = object : JsonObjectRequest(Method.GET, url, null,
            Response.Listener {
                android.util.Log.d("SERVER_API", "GET Object SUCCESS: $path")
                callback(it)
            },
            Response.ErrorListener {
                android.util.Log.e("SERVER_API", "GET Object ERROR: $path - ${it.message}")
                handleError(context, it)
                callback(null)
            }) {
            override fun getHeaders(): MutableMap<String, String> = getAuthHeaders(context)
        }.withDefaultTimeout()
        queue.add(request)
    }

    fun delete(context: Context, path: String, callback: (Boolean) -> Unit) {
        val url = "${Constants.BASE_URL}$path"
        val queue = Volley.newRequestQueue(context)
        val request = object : StringRequest(Method.DELETE, url,
            Response.Listener { callback(true) },
            Response.ErrorListener {
                handleError(context, it)
                callback(false)
            }) {
            override fun getHeaders(): MutableMap<String, String> = getAuthHeaders(context)
        }.withDefaultTimeout()
        queue.add(request)
    }

    fun logout(context: Context, token: String?, callback: (Boolean) -> Unit) {
        if (token.isNullOrBlank()) {
            callback(false)
            return
        }
        val url = "${Constants.BASE_URL}/api/logout"
        val queue = Volley.newRequestQueue(context)
        val request = object : JsonObjectRequest(Method.POST, url, JSONObject(),
            Response.Listener { callback(it.optBoolean("success")) },
            Response.ErrorListener { callback(false) }) {
            override fun getHeaders(): MutableMap<String, String> = hashMapOf(
                "Authorization" to "Bearer $token"
            )
        }.withDefaultTimeout()
        queue.add(request)
    }
}
