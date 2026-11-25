package com.zhiwei.limiter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.zhiwei.limiter.MainActivity
import com.zhiwei.limiter.data.ConfigStore
import com.zhiwei.limiter.data.ManagedApp
import com.zhiwei.limiter.shell.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 守护服务。
 *
 * 工作方式：以固定间隔读取当前前台应用包名。
 * 受管应用一旦离开前台，且未授予「允许后台」权限，立即 am force-stop。
 *
 * 之所以用轮询而不是无障碍事件：无障碍服务长期驻留的开销远大于
 * 一次轻量 dumpsys 读取，而本应用的目标恰恰是给系统减负。
 */
class GuardService : Service() {

    private var scope: CoroutineScope? = null
    private var loopJob: Job? = null
    private lateinit var store: ConfigStore

    companion object {
        private const val CHANNEL_ID = "guard_channel"
        private const val NOTI_ID = 1101

        const val ACTION_START = "com.zhiwei.limiter.START"
        const val ACTION_STOP = "com.zhiwei.limiter.STOP"

        fun start(context: Context) {
            val i = Intent(context, GuardService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GuardService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = ConfigStore(this)
        createChannel()
        startForeground(NOTI_ID, buildNotification(0))
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLoop()
                store.serviceEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startLoop()
        }
        return START_STICKY
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        store.serviceEnabled = true
        loopJob = scope?.launch {
            var lastForeground: String? = null
            var handledCount = 0

            while (true) {
                val managed = store.loadApps().filter { it.enabled }

                if (managed.isEmpty()) {
                    delay(store.pollIntervalMs)
                    continue
                }

                val fg = ShellExecutor.currentForegroundPackage()

                if (fg != null && fg != lastForeground) {
                    lastForeground = fg
                    if (managed.any { it.packageName == fg }) {
                        store.appendLog("进入前台：${labelOf(managed, fg)}")
                    }
                }

                // 找出「受管、未授权后台、进程还活着但已不在前台」的应用
                val running = ShellExecutor.runningPackages()
                for (app in managed) {
                    if (app.packageName == fg) continue
                    if (app.allowBackground) continue
                    if (app.packageName !in running) continue

                    val ok = ShellExecutor.forceStop(app.packageName)
                    if (ok) {
                        handledCount++
                        store.appendLog("已强停：${app.appName}（离开前台）")
                        updateNotification(handledCount)
                    } else {
                        store.appendLog("强停失败：${app.appName}")
                    }
                }

                delay(store.pollIntervalMs)
            }
        }
    }

    private fun labelOf(list: List<ManagedApp>, pkg: String): String =
        list.firstOrNull { it.packageName == pkg }?.appName ?: pkg

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    override fun onDestroy() {
        stopLoop()
        scope?.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉本应用时，尝试恢复守护
        if (store.serviceEnabled) {
            startLoop()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- 通知 ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "后台限制守护",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                    description = "保持应用限制规则运行"
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(count: Int): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("应用限制运行中")
            .setContentText(if (count > 0) "已阻止 $count 次后台驻留" else "监控受管应用")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(count: Int) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTI_ID, buildNotification(count))
        } catch (_: Throwable) {
        }
    }
}