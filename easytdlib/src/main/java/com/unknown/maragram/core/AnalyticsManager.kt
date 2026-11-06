package com.unknown.maragram.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.unknown.maragram.easytdlib.BuildConfig
import java.util.UUID
import java.util.Calendar
import java.util.TimeZone
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Direct Turso Analytics Manager for Maragram.
 * Connects directly to Turso via HTTP API.
 */
object AnalyticsManager {
    private const val PREFS_NAME = "maragram_analytics"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val KEY_FIRST_SEEN = "first_seen"
    private const val KEY_LAST_UPLOAD_DATE = "last_upload_date"
    private const val KEY_WRITES_TODAY = "writes_today"
    private const val KEY_COUNTERS = "counters"
    
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()

    private var sessionStartTime: Long = 0

    // Stats for UI
    private val _totalUsers = MutableStateFlow(0L)
    val totalUsers = _totalUsers.asStateFlow()

    private val _milestoneReached = MutableStateFlow(false)
    val milestoneReached = _milestoneReached.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isNewInstall = getInstallationId() == null
        if (isNewInstall) {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, newId).apply()
            prefs.edit().putLong(KEY_FIRST_SEEN, System.currentTimeMillis()).apply()
            trackEvent("new_user", 1)
        }
        
        sessionStartTime = System.currentTimeMillis()
        trackEvent("sessions", 1)
        trackEvent("active_today", 1)
        
        // Start periodic sync and stats fetching
        scope.launch {
            initSchema()
            
            // Sync immediately on new install to register the user count
            if (isNewInstall) {
                sync()
            }
            
            fetchStats()
            
            while (isActive) {
                delay(30 * 1000) // Poll stats every 30s while app is open
                sync()
                fetchStats()
            }
        }
    }

    fun onAppBackgrounded() {
        val durationSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000
        if (durationSeconds > 0) trackEvent("session_seconds", durationSeconds)
        sync()
    }

    fun getInstallationId(): String? = prefs.getString(KEY_INSTALLATION_ID, null)

    fun trackEvent(key: String, increment: Long = 1) {
        val countersJson = prefs.getString(KEY_COUNTERS, "{}") ?: "{}"
        try {
            val json = JSONObject(countersJson)
            val current = json.optLong(key, 0L)
            json.put(key, current + increment)
            prefs.edit().putString(KEY_COUNTERS, json.toString()).apply()
        } catch (e: Exception) {
            Log.e("Analytics", "Failed to track event: $key", e)
        }
    }

    private fun getTursoHttpUrl(): String {
        val rawUrl = BuildConfig.TURSO_URL
        return if (rawUrl.startsWith("libsql://")) {
            "https://" + rawUrl.substring(9)
        } else rawUrl
    }

    private fun executeBatch(queries: List<Pair<String, List<Any>>>) {
        val url = getTursoHttpUrl() + "/v2/pipeline"
        val token = BuildConfig.TURSO_TOKEN
        if (url.contains("://") && url.length > 10 && token.isNotBlank()) {
            // Valid credentials
        } else {
            Log.e("Analytics", "Turso credentials missing or invalid")
            return
        }

        val requests = JSONArray()
        queries.forEach { (sql, args) ->
            val queryObj = JSONObject()
            queryObj.put("type", "execute")
            
            val stmt = JSONObject()
            stmt.put("sql", sql)
            val argsArr = JSONArray()
            args.forEach { arg ->
                val argObj = JSONObject()
                when (arg) {
                    is String -> { argObj.put("type", "text"); argObj.put("value", arg) }
                    is Long -> { argObj.put("type", "integer"); argObj.put("value", arg.toString()) }
                    is Int -> { argObj.put("type", "integer"); argObj.put("value", arg.toString()) }
                    else -> { argObj.put("type", "text"); argObj.put("value", arg.toString()) }
                }
                argsArr.put(argObj)
            }
            stmt.put("args", argsArr)
            queryObj.put("stmt", stmt)
            requests.put(queryObj)
        }

        val payload = JSONObject()
        payload.put("requests", requests)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        scope.launch {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("Analytics", "Direct Turso batch successful")
                        incrementWriteCount()
                        clearCounters()
                        // Refresh stats immediately after a successful write
                        fetchStats()
                    } else {
                        Log.e("Analytics", "Turso error: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("Analytics", "Network error during Turso sync", e)
            }
        }
    }

    fun sync() {
        if (getWritesToday() >= 10) {
            Log.d("Analytics", "Daily write limit reached.")
            return
        }

        val counters = getPendingCounters()
        if (counters.length() == 0) return

        val installationId = getInstallationId() ?: return
        val utcDate = getCurrentUtcDate()
        val appVersion = "1.0"

        val queries = mutableListOf<Pair<String, List<Any>>>()

        // 1. Upsert User
        queries.add("INSERT INTO users (installation_id, last_seen, app_version) VALUES (?, CURRENT_TIMESTAMP, ?) ON CONFLICT(installation_id) DO UPDATE SET last_seen=excluded.last_seen, app_version=excluded.app_version" to listOf(installationId, appVersion))

        // 2. Update Milestone if needed
        queries.add("UPDATE milestones SET reached_at = CURRENT_TIMESTAMP WHERE milestone = 25000 AND reached_at IS NULL AND (SELECT count(*) FROM users) >= 25000" to emptyList())

        // 3. Upsert Daily Usage
        val columnList = mutableListOf<String>()
        val valueList = mutableListOf<Any>()
        val updateParts = mutableListOf<String>()

        val supportedKeys = listOf(
            "sessions", "session_seconds", "messages_sent", "messages_received",
            "chats_opened", "messages_composed", "captures_created", "captures_opened",
            "captures_moved", "branches_created", "spatial_interactions", "zoom_actions",
            "searches", "layouts_used", "support_page_opened", "share_clicked"
        )

        supportedKeys.forEach { key ->
            val value = counters.optLong(key, 0L)
            if (value > 0) {
                columnList.add(key)
                valueList.add(value)
                updateParts.add("$key = daily_usage.$key + excluded.$key")
            }
        }

        if (columnList.isNotEmpty()) {
            val placeholders = columnList.map { "?" }.joinToString(", ")
            val sql = "INSERT INTO daily_usage (utc_date, installation_id, ${columnList.joinToString(", ")}) VALUES (?, ?, $placeholders) ON CONFLICT(utc_date, installation_id) DO UPDATE SET ${updateParts.joinToString(", ")}, updated_at=CURRENT_TIMESTAMP"
            val args = mutableListOf<Any>(utcDate, installationId)
            args.addAll(valueList)
            queries.add(sql to args)
        }

        executeBatch(queries)
    }

    private fun initSchema() {
        val queries = listOf(
            "CREATE TABLE IF NOT EXISTS users (installation_id TEXT PRIMARY KEY, first_seen DATETIME DEFAULT CURRENT_TIMESTAMP, last_seen DATETIME DEFAULT CURRENT_TIMESTAMP, app_version TEXT)" to emptyList<Any>(),
            "CREATE TABLE IF NOT EXISTS daily_usage (utc_date TEXT, installation_id TEXT, sessions INTEGER DEFAULT 0, session_seconds INTEGER DEFAULT 0, messages_sent INTEGER DEFAULT 0, messages_received INTEGER DEFAULT 0, chats_opened INTEGER DEFAULT 0, messages_composed INTEGER DEFAULT 0, captures_created INTEGER DEFAULT 0, captures_opened INTEGER DEFAULT 0, captures_moved INTEGER DEFAULT 0, branches_created INTEGER DEFAULT 0, spatial_interactions INTEGER DEFAULT 0, zoom_actions INTEGER DEFAULT 0, searches INTEGER DEFAULT 0, layouts_used INTEGER DEFAULT 0, support_page_opened INTEGER DEFAULT 0, share_clicked INTEGER DEFAULT 0, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (utc_date, installation_id))" to emptyList<Any>(),
            "CREATE TABLE IF NOT EXISTS milestones (milestone INTEGER PRIMARY KEY, reached_at DATETIME)" to emptyList<Any>(),
            "INSERT OR IGNORE INTO milestones (milestone) VALUES (25000)" to emptyList<Any>()
        )
        executeBatch(queries)
    }

    fun fetchStats() {
        val url = getTursoHttpUrl() + "/v2/pipeline"
        val token = BuildConfig.TURSO_TOKEN
        if (!url.contains("://") || token.isBlank()) return
        
        val requests = JSONArray()
        requests.put(JSONObject().apply {
            put("type", "execute")
            put("stmt", JSONObject().apply { put("sql", "SELECT count(*) as count FROM users") })
        })
        requests.put(JSONObject().apply {
            put("type", "execute")
            put("stmt", JSONObject().apply { put("sql", "SELECT reached_at FROM milestones WHERE milestone = 25000") })
        })

        val payload = JSONObject().apply { put("requests", requests) }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        scope.launch {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: return@use
                        val json = JSONObject(bodyStr)
                        val results = json.getJSONArray("results")
                        
                        // Parse Total Users (Query 0)
                        val res0 = results.getJSONObject(0).getJSONObject("response").getJSONObject("result")
                        val rows0 = res0.getJSONArray("rows")
                        val totalUsersVal = if (rows0.length() > 0) {
                            rows0.getJSONArray(0).getJSONObject(0).optString("value", "0").toLong()
                        } else 0L

                        // Parse Milestone (Query 1)
                        val res1 = results.getJSONObject(1).getJSONObject("response").getJSONObject("result")
                        val rows1 = res1.getJSONArray("rows")
                        val reachedAt = if (rows1.length() > 0) {
                            rows1.getJSONArray(0).getJSONObject(0).optString("value", "")
                        } else ""
                        
                        _totalUsers.value = totalUsersVal
                        _milestoneReached.value = reachedAt.isNotBlank() && reachedAt != "null"
                    }
                }
            } catch (e: Exception) {
                Log.e("Analytics", "Failed to fetch stats", e)
            }
        }
    }

    private fun getCurrentUtcDate(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return String.format(Locale.US, "%04d-%02d-%02d", 
            cal.get(Calendar.YEAR), 
            cal.get(Calendar.MONTH) + 1, 
            cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun checkDailyLimitReset() {
        val today = getCurrentUtcDate()
        val lastDate = prefs.getString(KEY_LAST_UPLOAD_DATE, "")
        if (today != lastDate) {
            prefs.edit()
                .putString(KEY_LAST_UPLOAD_DATE, today)
                .putInt(KEY_WRITES_TODAY, 0)
                .apply()
        }
    }

    fun getWritesToday(): Int {
        checkDailyLimitReset()
        return prefs.getInt(KEY_WRITES_TODAY, 0)
    }

    private fun incrementWriteCount() {
        val count = getWritesToday()
        prefs.edit().putInt(KEY_WRITES_TODAY, count + 1).apply()
    }

    private fun getPendingCounters(): JSONObject {
        val countersJson = prefs.getString(KEY_COUNTERS, "{}") ?: "{}"
        return JSONObject(countersJson)
    }

    private fun clearCounters() {
        prefs.edit().putString(KEY_COUNTERS, "{}").apply()
    }
}
