package com.axion.tempus.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LauncherAppsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val searchLaunchCountsPreferences: SharedPreferences =
        appContext.getSharedPreferences(SEARCH_USAGE_PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val pinnedSlotsPreferences: SharedPreferences =
        appContext.getSharedPreferences(PINNED_APPS_PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val apps: StateFlow<List<LauncherApp>> = _apps.asStateFlow()
    private val _searchLaunchCounts = MutableStateFlow(loadSearchLaunchCounts())
    val searchLaunchCounts: StateFlow<Map<String, Int>> = _searchLaunchCounts.asStateFlow()

    private val _pinnedSlotKeys = MutableStateFlow(loadPinnedSlotKeys())
    val pinnedSlotKeys: StateFlow<List<String?>> = _pinnedSlotKeys.asStateFlow()

    @Volatile
    private var loaded = false

    private val iconCache = object : LruCache<String, Bitmap>(96) {}

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        synchronized(this@LauncherAppsRepository) {
            if (loaded) return@withContext

            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            _apps.value = packageManager.queryIntentActivities(intent, 0)
                .map { resolveInfo ->
                    val label = resolveInfo.loadLabel(packageManager).toString()
                    LauncherApp(
                        packageName = resolveInfo.activityInfo.packageName,
                        activityName = resolveInfo.activityInfo.name,
                        label = label,
                        searchLabel = label.lowercase()
                    )
                }
                .distinctBy { app -> app.packageName to app.activityName }
                .sortedBy { app -> app.searchLabel }

            pruneInvalidPinnedSlotsLocked()

            loaded = true
        }
    }

    fun launcherAppForKey(key: String): LauncherApp? {
        return _apps.value.firstOrNull { searchLaunchKey(it) == key }
    }

    fun resolvedPinnedApps(): List<LauncherApp> {
        return _pinnedSlotKeys.value.mapNotNull { key ->
            key?.let { launcherAppForKey(it) }
        }
    }

    fun resolvedPinnedSlots(): List<LauncherApp?> {
        return _pinnedSlotKeys.value.map { key ->
            key?.let { launcherAppForKey(it) }
        }
    }

    fun setPinnedSlot(index: Int, app: LauncherApp?) {
        require(index in 0 until PINNED_SLOT_COUNT)
        val next = normalizedPinnedSlots()
        if (app != null) {
            val newKey = searchLaunchKey(app)
            for (i in next.indices) {
                if (i != index && next[i] == newKey) {
                    next[i] = null
                }
            }
            next[index] = newKey
        } else {
            next[index] = null
        }
        _pinnedSlotKeys.value = next
        persistPinnedSlotKeys()
    }

    fun clearPinnedSlot(index: Int) {
        setPinnedSlot(index, null)
    }

    private fun pruneInvalidSlotsLocked(validKeys: Set<String>) {
        val current = _pinnedSlotKeys.value
        if (current.none { key -> key != null && key !in validKeys }) return
        _pinnedSlotKeys.value = current.map { key -> key?.takeIf { it in validKeys } }
        persistPinnedSlotKeys()
    }

    private fun pruneInvalidPinnedSlotsLocked() {
        val validKeys = _apps.value.mapTo(HashSet()) { searchLaunchKey(it) }
        pruneInvalidSlotsLocked(validKeys)
    }

    private fun loadPinnedSlotKeys(): List<String?> {
        val stored = pinnedSlotsPreferences.getString(PINNED_SLOT_KEYS, null) ?: return emptyPinnedSlots()
        return try {
            val array = JSONArray(stored)
            List(PINNED_SLOT_COUNT) { index ->
                if (index >= array.length() || array.isNull(index)) {
                    null
                } else {
                    array.getString(index).takeIf { it.isNotEmpty() }
                }
            }
        } catch (_: Exception) {
            emptyPinnedSlots()
        }
    }

    private fun persistPinnedSlotKeys() {
        val array = JSONArray()
        _pinnedSlotKeys.value.forEach { key ->
            if (key == null) {
                array.put(null)
            } else {
                array.put(key)
            }
        }
        pinnedSlotsPreferences.edit()
            .putString(PINNED_SLOT_KEYS, array.toString())
            .apply()
    }

    private fun emptyPinnedSlots(): List<String?> = List(PINNED_SLOT_COUNT) { null }

    private fun normalizedPinnedSlots(): MutableList<String?> {
        val current = _pinnedSlotKeys.value
        return MutableList(PINNED_SLOT_COUNT) { i -> current.getOrNull(i) }
    }

    fun peekIcon(app: LauncherApp, sizePx: Int): Bitmap? {
        return synchronized(iconCache) {
            iconCache.get(iconKey(app, sizePx))
        }
    }

    suspend fun getIcon(app: LauncherApp, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = iconKey(app, sizePx)
        synchronized(iconCache) {
            iconCache.get(key)
        }?.let { return@withContext it }

        val bitmap = loadIconBitmap(app, sizePx) ?: return@withContext null

        synchronized(iconCache) {
            iconCache.put(key, bitmap)
        }

        bitmap
    }

    fun recordSearchLaunch(app: LauncherApp) {
        val key = searchLaunchKey(app)
        val updatedCounts = _searchLaunchCounts.value.toMutableMap()
        val nextCount = (updatedCounts[key] ?: 0) + 1
        updatedCounts[key] = nextCount

        _searchLaunchCounts.value = updatedCounts
        searchLaunchCountsPreferences.edit()
            .putInt(key, nextCount)
            .apply()
    }

    private fun loadIconBitmap(app: LauncherApp, sizePx: Int): Bitmap? {
        val drawable = try {
            packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
        } catch (_: PackageManager.NameNotFoundException) {
            try {
                packageManager.getApplicationIcon(app.packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        } ?: return null

        return drawable.toBitmap(sizePx, sizePx)
    }

    private fun iconKey(app: LauncherApp, sizePx: Int): String {
        return "${app.packageName}/${app.activityName}@$sizePx"
    }

    private fun searchLaunchKey(app: LauncherApp): String {
        return "${app.packageName}/${app.activityName}"
    }

    private fun loadSearchLaunchCounts(): Map<String, Int> {
        return searchLaunchCountsPreferences.all
            .mapNotNull { (key, value) ->
                val count = value as? Int ?: return@mapNotNull null
                key to count
            }
            .toMap()
    }

    companion object {
        private const val SEARCH_USAGE_PREFERENCES_NAME = "search_usage_counts"
        private const val PINNED_APPS_PREFERENCES_NAME = "pinned_launcher_apps"
        private const val PINNED_SLOT_KEYS = "pinned_slot_keys"
        private const val PINNED_SLOT_COUNT = 4

        @Volatile
        private var instance: LauncherAppsRepository? = null

        fun get(context: Context): LauncherAppsRepository {
            return instance ?: synchronized(this) {
                instance ?: LauncherAppsRepository(context).also { instance = it }
            }
        }
    }
}
