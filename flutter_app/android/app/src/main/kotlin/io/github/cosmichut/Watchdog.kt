package io.github.cosmichut

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/**
 * 独立看门狗：AlarmManager 每 15 分钟巡检一次。
 *
 * 为什么需要它：无障碍服务看门狗很强，但荣耀等 ROM 会在重启后撤销
 * 侧载应用的无障碍服务——看门狗自己也死了。AlarmManager 不受
 * "自启动管理"限制，到点系统会唤醒 App 进程执行。
 *
 * 巡检内容：
 * 1. 行走检测开启但服务被杀 → 拉起
 * 2. 无障碍服务曾经开启但现在失效 → 发系统通知提醒用户（点击直达设置页）
 */
object WatchdogScheduler {

    private const val ACTION = "io.github.cosmichut.WATCHDOG"
    private const val INTERVAL_MS = 15 * 60_000L

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ACTION).setClass(ctx, WatchdogReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            pi
        )
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 1. 复活行走检测服务
        if (LockManager.isWalkingEnabled(context) &&
            !LockManager.isWalkingServiceAlive(context)
        ) {
            try {
                WalkingService.start(context)
            } catch (_: Exception) {}
        }

        // 2. 无障碍服务失效提醒（曾经开启过但现在没了 → 多半是 ROM 撤销）
        if (LockManager.wasAccessibilityEnabled(context) &&
            !LockAccessibilityService.isEnabled(context)
        ) {
            AlertNotifier.notifyAccessibilityLost(context)
        }

        // 链式续约（setAndAllowWhileIdle 是一次性的）
        WatchdogScheduler.schedule(context)
    }
}

/**
 * 服务失效告警通知：用户长期不打开 App 时，也能在通知栏发现问题。
 */
object AlertNotifier {

    private const val CHANNEL_ID = "service_alert"
    private const val ID_ACCESSIBILITY_LOST = 2001

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                LockManager.localized(ctx).getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    fun notifyAccessibilityLost(ctx: Context) {
        ensureChannel(ctx)
        val l10n = LockManager.localized(ctx)
        val tap = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(l10n.getString(R.string.app_name))
            .setContentText(l10n.getString(R.string.alert_accessibility_lost))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ID_ACCESSIBILITY_LOST, notification)
    }
}
