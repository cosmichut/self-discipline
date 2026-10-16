package io.github.cosmichut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启：恢复行走检测服务；若重启前仍处于锁定状态，恢复锁定界面。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // AlarmManager 看门狗不受自启动管理限制，是重启后最可靠的恢复通道
        WatchdogScheduler.schedule(context)

        if (LockManager.isWalkingEnabled(context)) {
            try {
                WalkingService.start(context)
            } catch (_: Exception) {}
        }

        // 无障碍服务曾开启但重启后失效（荣耀等 ROM 会撤销侧载应用的无障碍）→ 通知提醒
        if (LockManager.wasAccessibilityEnabled(context) &&
            !LockAccessibilityService.isEnabled(context)
        ) {
            AlertNotifier.notifyAccessibilityLost(context)
        }
        // 注意：Android 10+ 禁止后台拉起 Activity，不在此处恢复锁定界面。
        // 无障碍服务开机自动重启，会在下一次窗口切换事件时自动拉回锁定界面；
        // 手动锁定的截止时间已持久化，结算逻辑不受影响。
    }
}
