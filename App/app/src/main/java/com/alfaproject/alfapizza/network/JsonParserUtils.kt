package com.alfaproject.alfapizza.network

import org.json.JSONArray
import org.json.JSONObject

object JsonParserUtils {
    /**
     * Extracts the records JSONArray from a JSONObject response, handling both
     * single-nested (response.record) and double-nested (response.record.record) structures.
     */
    fun extractRecords(response: JSONObject): JSONArray {
        // Case 1: record is an object that contains another record (JSONArray)
        val recordObj = response.optJSONObject("record")
        if (recordObj != null && recordObj.has("record")) {
            val innerRecord = recordObj.optJSONArray("record")
            if (innerRecord != null) return innerRecord
        }

        // Case 2: record is directly a JSONArray
        val recordArray = response.optJSONArray("record")
        if (recordArray != null) return recordArray

        // Fallback: return empty JSONArray instead of throwing to avoid crashes
        return JSONArray()
    }
}
