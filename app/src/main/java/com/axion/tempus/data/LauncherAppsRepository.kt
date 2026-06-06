package com.axion.tempus.data

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.LruCache
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

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

    private val appRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appRefreshMutex = Mutex()
    private val searchLaunchCountsMutex = Mutex()
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            refreshAppsIfLoaded()
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshAppsIfLoaded()
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            refreshAppsIfLoaded()
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            refreshAppsIfLoaded()
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            refreshAppsIfLoaded()
        }
    }
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAppsIfLoaded()
        }
    }
    private val iconCacheSizeKb = run {
        val activityManager = appContext.getSystemService(ActivityManager::class.java)
        val memoryClassMb = activityManager?.memoryClass ?: 128
        ((memoryClassMb * 1024) / 32).coerceIn(2048, 4096)
    }
    private val iconCache = object : LruCache<String, Bitmap>(iconCacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    init {
        registerAppChangeListeners()
    }

    suspend fun ensureLoaded() {
        refreshInstalledApps(force = false)
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
        pinnedSlotsPreferences.edit {
            putString(PINNED_SLOT_KEYS, array.toString())
        }
    }

    private fun emptyPinnedSlots(): List<String?> = List(PINNED_SLOT_COUNT) { null }

    private fun normalizedPinnedSlots(): MutableList<String?> {
        val current = _pinnedSlotKeys.value
        return MutableList(PINNED_SLOT_COUNT) { i -> current.getOrNull(i) }
    }

    private suspend fun refreshInstalledApps(force: Boolean) = withContext(Dispatchers.IO) {
        appRefreshMutex.withLock {
            if (loaded && !force) return@withLock

            _apps.value = loadLauncherApps()
            if (force) {
                synchronized(iconCache) {
                    iconCache.evictAll()
                }
            }
            pruneInvalidPinnedSlotsLocked()
            loaded = true
        }
    }

    private fun refreshAppsIfLoaded() {
        if (!loaded) return
        appRefreshScope.launch {
            refreshInstalledApps(force = true)
        }
    }

    private fun loadLauncherApps(): List<LauncherApp> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return packageManager.queryIntentActivities(intent, 0)
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
    }

    private fun registerAppChangeListeners() {
        launcherApps?.let { service ->
            runCatching {
                service.registerCallback(launcherAppsCallback, Handler(Looper.getMainLooper()))
            }
        }

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                packageChangeReceiver,
                packageFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(packageChangeReceiver, packageFilter)
        }
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
        prefsWriteScope.launch {
            searchLaunchCountsMutex.withLock {
                val updatedCounts = _searchLaunchCounts.value.toMutableMap()
                val nextCount = (updatedCounts[key] ?: 0) + 1
                updatedCounts[key] = nextCount
                _searchLaunchCounts.value = updatedCounts
                searchLaunchCountsPreferences.edit {
                    putInt(key, nextCount)
                }
            }
        }
    }

    private fun loadIconBitmap(app: LauncherApp, sizePx: Int): Bitmap? {
        val drawable = resolveLauncherIconDrawable(app) ?: return null
        return rasterizeLauncherDrawable(drawable, sizePx)
    }

    /**
     * Renders the normal launcher icon (foreground + background for adaptive icons).
     * We intentionally do not use the adaptive "monochrome" layer (API 33+): on real devices many apps
     * ship layers that rasterize as unusable solid fills, and heuristics cannot reliably
     * distinguish them from good glyphs.
     */
    private fun rasterizeLauncherDrawable(drawable: Drawable, sizePx: Int): Bitmap {
        val d = drawable.mutate()
        d.setBounds(0, 0, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        d.draw(canvas)
        return bitmap
    }

    private fun resolveLauncherIconDrawable(app: LauncherApp) = try {
        packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
    } catch (_: PackageManager.NameNotFoundException) {
        try {
            packageManager.getApplicationIcon(app.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun iconKey(app: LauncherApp, sizePx: Int): String {
        return "${app.packageName}/${app.activityName}@$sizePx-v$ICON_CACHE_VERSION"
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
        /** Bump when icon loading strategy changes so stale bitmaps are not reused. */
        private const val ICON_CACHE_VERSION = 3
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
