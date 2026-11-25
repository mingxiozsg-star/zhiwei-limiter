package com.zhiwei.limiter.shell

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 提权执行器。
 *
 * 通过 Shizuku 起独立的 shell 进程执行命令，拿到的权限等级与
 * `adb shell` 相同（uid 2000）。这样无需 root 也能：
 *  - 读取当前前台应用（dumpsys activity）
 *  - 强制停止指定应用（am force-stop）
 *  - 处理 appops / deviceidle 白名单
 *
 * 注意：Shizuku.newProcess 标注了 @RestrictTo(LIBRARY_GROUP)，
 * 编译期被 Kotlin 视为 private，只能用反射调用。
 */
object ShellExecutor {

    private val newProcessMethod by lazy {
        try {
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                .apply { isAccessible = true }
        } catch (t: Throwable) {
            null
        }
    }

    /** Shizuku 是否已安装并在运行 */
    fun isBinderAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    /** 是否已获得 Shizuku 授权 */
    fun hasPermission(): Boolean = try {
        if (!isBinderAvailable()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (isBinderAvailable() && !hasPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Throwable) {
        }
    }

    private fun startProcess(command: String): Process? = try {
        newProcessMethod?.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null
        ) as? Process
    } catch (t: Throwable) {
        null
    }

    /**
     * 执行一条 shell 命令，返回标准输出（合并 stderr）。
     * Shizuku 不可用时返回 null，调用方需自行降级。
     */
    fun exec(command: String, timeoutMs: Long = 8000): String? {
        if (!hasPermission()) return null
        val process = startProcess(command) ?: return null
        return try {
            val out = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { out.append(it).append('\n') }
                    }
                } catch (_: Throwable) {
                }
            }
            val errReader = Thread {
                try {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { out.append(it).append('\n') }
                    }
                } catch (_: Throwable) {
                }
            }
            reader.start()
            errReader.start()

            val finished = waitFor(process, timeoutMs)
            if (!finished) {
                try {
                    process.destroy()
                } catch (_: Throwable) {
                }
            }
            reader.join(500)
            errReader.join(500)
            out.toString()
        } catch (t: Throwable) {
            null
        }
    }

    private fun waitFor(process: Process, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                // Android 的 Process 没有 waitFor(timeout)，用退出值探测
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(30)
            } catch (_: Throwable) {
                return true
            }
        }
        return false
    }

    // ---------- 业务封装 ----------

    /**
     * 获取当前前台应用包名。
     * 优先 mResumedActivity，退化到 topResumedActivity / mFocusedApp。
     */
    fun currentForegroundPackage(): String? {
        val raw = exec("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -2")
            ?: return null
        return parseForeground(raw)
    }

    internal fun parseForeground(raw: String): String? {
        // 形如：mResumedActivity: ActivityRecord{... u0 com.xxx/.MainActivity t123}
        val regex = Regex("""([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+)/[A-Za-z0-9_.$]+""")
        val m = regex.find(raw) ?: return null
        return m.groupValues[1]
    }

    /** 强制停止应用（含全部进程） */
    fun forceStop(packageName: String): Boolean {
        if (packageName.isBlank() || packageName == "android") return false
        val out = exec("am force-stop $packageName") ?: return false
        return !out.contains("Error") && !out.contains("Exception")
    }

    /** 禁止后台运行 + 移出省电白名单（系统层限制自启动） */
    fun applySystemRestriction(packageName: String): List<String> {
        val results = mutableListOf<String>()
        exec("cmd appops set $packageName RUN_ANY_IN_BACKGROUND ignore")?.let {
            results.add("appops RUN_ANY_IN_BACKGROUND=ignore")
        }
        exec("cmd deviceidle whitelist -$packageName")?.let {
            if (it.contains("Removed")) results.add("移出省电白名单")
        }
        return results
    }

    /** 解除系统层限制 */
    fun clearSystemRestriction(packageName: String): List<String> {
        val results = mutableListOf<String>()
        exec("cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow")?.let {
            results.add("appops 恢复 allow")
        }
        exec("cmd deviceidle whitelist +$packageName")?.let {
            if (it.contains("Added")) results.add("恢复省电白名单")
        }
        return results
    }

    /** 当前在跑的进程包名集合，用于判断应用是否真的还活着 */
    fun runningPackages(): Set<String> {
        val raw = exec("ps -A -o NAME") ?: return emptySet()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.contains('.') }
            .map { it.substringBefore(':') }
            .filter { it.isNotBlank() }
            .toSet()
    }
}