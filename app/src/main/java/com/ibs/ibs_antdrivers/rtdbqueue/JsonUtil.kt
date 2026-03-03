package com.ibs.ibs_antdrivers.rtdbqueue

import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal JSON helpers for the RTDB queue.
 *
 * We intentionally keep this lightweight (no extra dependencies).
 */
internal object JsonUtil {

    fun mapToJson(map: Map<String, Any?>): String {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            obj.put(k, wrap(v))
        }
        return obj.toString()
    }

    fun jsonToMap(json: String): Map<String, Any?> {
        val obj = JSONObject(json)
        val out = LinkedHashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = unwrap(obj.get(k))
        }
        return out
    }

    private fun wrap(v: Any?): Any? {
        return when (v) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val o = JSONObject()
                v.entries.forEach { (k, value) ->
                    if (k is String) o.put(k, wrap(value))
                }
                o
            }
            is List<*> -> {
                val arr = JSONArray()
                v.forEach { arr.put(wrap(it)) }
                arr
            }
            is Boolean, is Int, is Long, is Double, is Float, is String -> v
            is Number -> v.toDouble()
            else -> v.toString()
        }
    }

    private fun unwrap(v: Any?): Any? {
        return when (v) {
            null, JSONObject.NULL -> null
            is JSONObject -> {
                val out = LinkedHashMap<String, Any?>()
                val keys = v.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    out[k] = unwrap(v.get(k))
                }
                out
            }
            is JSONArray -> {
                val out = ArrayList<Any?>()
                for (i in 0 until v.length()) {
                    out.add(unwrap(v.get(i)))
                }
                out
            }
            else -> v
        }
    }
}

