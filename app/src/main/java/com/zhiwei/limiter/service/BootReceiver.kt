package com.zhiwei.limiter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhiwei.limiter.data.ConfigStore

/** 开机自启：若守护开关为开启状态，自动恢复服务。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val store = ConfigStore(context)
        if (store.serviceEnabled) {
            try {
                GuardService.start(context)
            } catch (_: Throwable) {
            }
        }
    }
}