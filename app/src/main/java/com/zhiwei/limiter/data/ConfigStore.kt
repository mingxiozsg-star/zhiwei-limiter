package com.zhiwei.limiter.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 受管应用配置。
 *
 * @param packageName 包名
 * @param appName 应用显示名
 * @param allowAutoStart 是否授予自启动（true = 放行，false = 离开前台即强停）
 * @param allowBackground 是否允许驻留后台
 * @param enabled 该条目是否启用限制
 */
data class ManagedApp(
    val packageName: String,
    val appName: String,
    val allowAutoStart: Boolean = false,
    val allowBackground: Boolean = false,
    val enabled: Boolean = true
)

/**
 * 配置仓库。所有限制规则持久化在 SharedPreferences 里，格式为
 * `pkg|appName|allowAutoStart|allowBackground|enabled`，多行以换行分隔。
 */
class ConfigStore(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("limiter_config", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_APPS = "managed_apps"
        private const val KEY_SERVICE_ON = "service_enabled"
        private const val KEY_POLL_MS = "poll_interval_ms"
        private const val KEY_LOG = "event_log"
        private const val MAX_LOG = 200
    }

    var serviceEnabled: Boolean
        get() = sp.getBoolean(KEY_SERVICE_ON, false)
        set(value) = sp.edit().putBoolean(KEY_SERVICE_ON, value).apply()

    /** 前台检测轮询间隔，默认 800ms。越小反应越快，耗电越高。 */
    var pollIntervalMs: Long
        get() = sp.getLong(KEY_POLL_MS, 800L)
        set(value) = sp.edit().putLong(KEY_POLL_MS, value.coerceIn(300L, 3000L)).apply()

    fun loadApps(): List<ManagedApp> {
        val raw = sp.getString(KEY_APPS, null) ?: return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split('|')
                if (p.size < 5) return@mapNotNull null
                ManagedApp(
                    packageName = p[0],
                    appName = p[1],
                    allowAutoStart = p[2] == "1",
                    allowBackground = p[3] == "1",
                    enabled = p[4] == "1"
                )
            }
            .toList()
    }

    fun saveApps(apps: List<ManagedApp>) {
        val raw = apps.joinToString("\n") { a ->
            "${a.packageName}|${a.appName}|${if (a.allowAutoStart) 1 else 0}" +
                "|${if (a.allowBackground) 1 else 0}|${if (a.enabled) 1 else 0}"
        }
        sp.edit().putString(KEY_APPS, raw).apply()
    }

    fun upsert(app: ManagedApp) {
        val list = loadApps().toMutableList()
        val idx = list.indexOfFirst { it.packageName == app.packageName }
        if (idx >= 0) list[idx] = app else list.add(app)
        saveApps(list)
    }

    fun remove(packageName: String) {
        saveApps(loadApps().filterNot { it.packageName == packageName })
    }

    fun isManaged(packageName: String): Boolean =
        loadApps().any { it.packageName == packageName }

    // ---------- 事件日志 ----------

    fun appendLog(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val old = sp.getString(KEY_LOG, "") ?: ""
        val lines = (listOf("$ts  $line") + old.lineSequence().toList())
            .take(MAX_LOG)
            .joinToString("\n")
        sp.edit().putString(KEY_LOG, lines).apply()
    }

    fun readLog(): List<String> =
        (sp.getString(KEY_LOG, "") ?: "").lineSequence().filter { it.isNotBlank() }.toList()

    fun clearLog() = sp.edit().putString(KEY_LOG, "").apply()
}
