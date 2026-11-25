package com.zhiwei.limiter.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** 一个可被限制的候选应用。 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val uid: Int
)

/** 从 PackageManager 读取已安装应用列表。 */
class AppRepository(private val context: Context) {

    fun loadAll(includeSystem: Boolean = false): List<AppEntry> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val packages = try {
            pm.getInstalledPackages(flags)
        } catch (t: Throwable) {
            emptyList()
        }

        val self = context.packageName

        return packages.asSequence()
            .mapNotNull { pi ->
                val ai = pi.applicationInfo ?: return@mapNotNull null
                if (pi.packageName == self) return@mapNotNull null
                val sys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (sys && !includeSystem) return@mapNotNull null
                AppEntry(
                    packageName = pi.packageName,
                    label = try {
                        pm.getApplicationLabel(ai).toString()
                    } catch (t: Throwable) {
                        pi.packageName
                    },
                    isSystem = sys,
                    uid = ai.uid
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun labelOf(packageName: String): String = try {
        val pm = context.packageManager
        val ai = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(ai).toString()
    } catch (t: Throwable) {
        packageName
    }
}