package io.github.cosmichut

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 锁定执行器 + 前台应用监控。
 *
 * 三种锁定形态：
 * - 手动锁定：非白名单应用前台时拉回 LockActivity
 * - 行走锁定：无障碍悬浮层覆盖（不受后台启动 Activity 限制）
 * - 应用限额：跟踪限额应用的前台时长，达到每日上限后悬浮层覆盖
 *
 * 白名单（手动锁定期间允许前台）：本应用、各厂商通话界面（来电豁免）、systemui。
 */
class LockAccessibilityService : AccessibilityService() {

    companion object {
        private val ALLOWED_PACKAGES = setOf(
            "io.github.cosmichut",
            // 通话界面（来电豁免）：AOSP / Google / 三星
            "com.android.incallui",
            "com.android.dialer",
            "com.android.phone",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.android.server.telecom",
            "com.android.systemui",
            "android",
        )

        // 紧急解除长按：行走 1 秒（真实行走场景可能需要紧急用手机）；
        // 专注 20 秒；应用限额 2 分钟（放弃成本递增）
        private const val EXIT_HOLD_WALKING_MS = 1000L
        private const val EXIT_HOLD_MS = 20000L
        private const val EXIT_HOLD_LIMIT_MS = 120_000L

        /** 行走检测看门狗间隔：无障碍服务是系统级常驻的，借它做巡检 */
        private const val WATCHDOG_INTERVAL_MS = 2 * 60_000L

        /** 用户是否已在系统设置中启用本服务 */
        fun isEnabled(ctx: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            // 不同 ROM 存储格式不同：完整包名或短格式（pkg/.ServiceName），统一用类名匹配
            return enabled.split(':').any { it.contains("LockAccessibilityService") }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var overlayView: View? = null
    private var overlayMode: String = ""   // "" / "walking" / "limit"
    private var overlayHoldProgress: ProgressBar? = null
    private var holdStart = 0L
    private var watchdogRunning = false

    // 前台应用跟踪（应用限额计时）
    private var fgPkg: String? = null
    private var fgSince = 0L
    // 限额悬浮层被紧急解除后，本次会话不再覆盖该应用（直到前台切换）
    private var limitDismissedFor: String? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshWatchdog()
            syncOverlay()
            if (LockManager.getLockType(this@LockAccessibilityService) == LockManager.TYPE_MANUAL) {
                LockActivity.start(context)
            }
        }
    }

    /**
     * 行走检测看门狗：心跳超时说明服务被 ROM 清理，自动拉起。
     * 仅在行走检测启用时运行（关闭开关即停，避免空转）。
     */
    private val watchdog = object : Runnable {
        override fun run() {
            if (LockManager.isWalkingEnabled(this@LockAccessibilityService) &&
                !LockManager.isWalkingServiceAlive(this@LockAccessibilityService)
            ) {
                WalkingService.start(this@LockAccessibilityService)
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun refreshWatchdog() {
        val needed = LockManager.isWalkingEnabled(this)
        if (needed && !watchdogRunning) {
            watchdogRunning = true
            handler.post(watchdog)
        } else if (!needed && watchdogRunning) {
            watchdogRunning = false
            handler.removeCallbacks(watchdog)
        }
    }

    override fun onServiceConnected() {
        val filter = IntentFilter(LockManager.ACTION_LOCK_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stateReceiver, filter)
        }
        refreshWatchdog()
        handler.post(fgPoller)
        syncOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        // 忽略我们自己的窗口事件（悬浮层/LockActivity）：
        // 否则悬浮层抢焦点 → 前台包名变成自己 → 判定"限额应用不在前台" → 撤销悬浮层
        // → 焦点还给目标应用 → 再盖悬浮层……无限循环造成高频闪烁
        if (pkg == packageName) return

        // 事件驱动的前台更新（快速通道；轮询 fgPoller 是可靠基准）
        if (pkg != fgPkg) {
            accumulateUsage(System.currentTimeMillis())
            fgPkg = pkg
            // 离开被解除的应用后，恢复其覆盖
            if (limitDismissedFor != null && pkg != limitDismissedFor) {
                limitDismissedFor = null
            }
        }
        syncOverlay()

        // 手动锁定：拉回非白名单应用
        if (LockManager.getLockType(this) != LockManager.TYPE_MANUAL) return
        if (pkg in ALLOWED_PACKAGES) return
        if (LockManager.isCallActive(this)) return
        LockActivity.start(this)
    }

    /**
     * 前台轮询器：每秒查询窗口栈获取真实前台应用。
     * 手势快速切换（左滑切走再滑回）可能不产生 WINDOW_STATE_CHANGED 事件，
     * 纯事件驱动会丢失状态导致悬浮层"消失不回来了"；轮询是可靠基准。
     */
    private val fgPoller = object : Runnable {
        override fun run() {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) {
                val now = System.currentTimeMillis()
                val pkg = currentForegroundPkg()
                if (pkg != null && pkg != packageName && pkg != fgPkg) {
                    accumulateUsage(now)
                    fgPkg = pkg
                    // 离开被解除的应用后，恢复其覆盖
                    if (limitDismissedFor != null && pkg != limitDismissedFor) {
                        limitDismissedFor = null
                    }
                }
                accumulateUsage(now)
                syncOverlay()
            }
            handler.postDelayed(this, 1000)
        }
    }

    /** 当前前台应用包名：取活跃的 TYPE_APPLICATION 窗口（不读窗口内容） */
    private fun currentForegroundPkg(): String? {
        return try {
            var fallback: String? = null
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val pkg = w.root?.packageName?.toString() ?: continue
                if (w.isActive) return pkg
                if (fallback == null) fallback = pkg
            }
            fallback
        } catch (_: Exception) {
            null
        }
    }

    // ---------- 应用限额：前台时长计时 ----------

    private fun isLimitedApp(pkg: String?): Boolean =
        pkg != null && LockManager.getAppLimits(this).containsKey(pkg)

    private fun accumulateUsage(now: Long) {
        val pkg = fgPkg
        if (pkg == null || !isLimitedApp(pkg)) {
            fgSince = now
            return
        }
        val elapsed = ((now - fgSince) / 1000).toInt()
        if (elapsed > 0) {
            LockManager.addUsageSeconds(this, pkg, elapsed)
        }
        fgSince = now
    }

    // ---------- 悬浮层（行走锁定 / 应用限额共用） ----------

    private fun syncOverlay() {
        when (LockManager.getLockType(this)) {
            LockManager.TYPE_WALKING -> ensureOverlay("walking", null)
            LockManager.TYPE_NONE -> {
                // 限额应用超额且正在前台 → 限额悬浮层
                // （紧急解除过的应用本次会话跳过，切换前台后恢复覆盖）
                val pkg = fgPkg
                if (pkg != null && pkg != limitDismissedFor &&
                    LockManager.isOverLimit(this, pkg)
                ) {
                    ensureOverlay("limit", pkg)
                } else {
                    hideOverlay()
                }
            }
            else -> hideOverlay() // 手动锁定走 LockActivity
        }
    }

    private fun ensureOverlay(mode: String, pkg: String?) {
        if (overlayView != null && overlayMode == mode) return
        hideOverlay()

        val view = buildOverlayView(mode, pkg)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NOT_FOCUSABLE：不抢窗口焦点——避免系统为悬浮层产生前台切换事件
            //（焦点切换会让前台包名变成我们自己，触发"盖上→撤销→盖上"闪烁循环）
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try {
            windowManager.addView(view, params)
            overlayView = view
            overlayMode = mode
        } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        overlayMode = ""
        overlayHoldProgress = null
    }

    private fun buildOverlayView(mode: String, pkg: String?): View {
        val ctx = this
        val l10n = LockManager.localized(ctx)   // 应用内语言覆盖
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2102027"))
            // 消费触摸事件，阻止操作下层应用（不抢焦点，焦点由窗口标志控制）
            isClickable = true
        }

        if (mode == "walking") {
            val emoji = TextView(ctx).apply {
                text = "🚶"
                textSize = 96f
                gravity = Gravity.CENTER
            }
            root.addView(emoji)
            val title = TextView(ctx).apply {
                text = l10n.getString(R.string.lock_title_walking)
                textSize = 28f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 0)
            }
            root.addView(title)
        } else {
            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg ?: "", 0)
                ).toString()
            } catch (_: Exception) {
                pkg ?: ""
            }
            val limitMin = LockManager.getAppLimits(ctx)[pkg] ?: 0
            val emoji = TextView(ctx).apply {
                text = "⏰"
                textSize = 96f
                gravity = Gravity.CENTER
            }
            root.addView(emoji)
            val title = TextView(ctx).apply {
                text = l10n.getString(R.string.limit_overlay_title, appName)
                textSize = 26f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(64, 32, 64, 0)
            }
            root.addView(title)
            val sub = TextView(ctx).apply {
                text = l10n.getString(R.string.limit_overlay_subtitle, limitMin)
                textSize = 16f
                setTextColor(Color.parseColor("#B0BEC5"))
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            }
            root.addView(sub)
        }

        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = 0
            visibility = View.GONE
        }
        overlayHoldProgress = progress
        root.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 8
        ).apply {
            setMargins(96, 0, 96, 0)
            topMargin = 96
        })

        // 紧急解除：底部提示文字即触控区（长按 20 秒/限额 2 分钟）。
        // 悬浮层是自动触发的，一旦误触发或卡死用户必须知道如何自救，
        // 因此提示可见（摩擦来自长按时长，而非隐藏方法）
        val hintRes =
            if (mode == "limit") R.string.overlay_exit_hint_limit
            else R.string.overlay_exit_hint_walking
        val exitZone = TextView(ctx).apply {
            text = l10n.getString(hintRes)
            textSize = 11f
            setTextColor(Color.parseColor("#4A5A63"))
            gravity = Gravity.CENTER
            setPadding(48, 40, 48, 40)
        }
        exitZone.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    holdStart = System.currentTimeMillis()
                    progress.visibility = View.VISIBLE
                    handler.post(overlayHoldTick)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(overlayHoldTick)
                    progress.progress = 0
                    progress.visibility = View.GONE
                }
            }
            true
        }
        root.addView(exitZone)

        return root
    }

    private val overlayHoldTick = object : Runnable {
        override fun run() {
            // 解除时长分档：限额 2 分钟 / 专注 20 秒 / 行走 1 秒
            val holdMs = when (overlayMode) {
                "limit" -> EXIT_HOLD_LIMIT_MS
                "walking" -> EXIT_HOLD_WALKING_MS
                else -> EXIT_HOLD_MS
            }
            val held = System.currentTimeMillis() - holdStart
            overlayHoldProgress?.progress = (held * 100 / holdMs).toInt().coerceAtMost(100)
            if (held >= holdMs) {
                handler.removeCallbacks(this)
                if (overlayMode == "walking") {
                    // 行走锁定不计成功/失败，直接解除
                    LockManager.completeLock(this@LockAccessibilityService, success = true)
                } else {
                    // 限额锁定：本次会话解除（切换前台后重新覆盖）
                    limitDismissedFor = fgPkg
                    hideOverlay()
                }
            } else {
                handler.postDelayed(this, 50)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
