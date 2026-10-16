package io.github.cosmichut

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView

/**
 * 锁定界面：全屏覆盖，显示倒计时，是"紧急退出"唯一入口。
 *
 * 防逃逸设计：
 * - 手动锁定时启用 startLockTask 屏幕固定（上滑手势失效）
 * - onPause 立即抢回前台，弥补无障碍事件的延迟
 * - 无障碍服务（LockAccessibilityService）最终兜底
 *
 * 紧急退出为底部中间不可见触控区（退出方式在主界面"开始专注"下方前置告知），
 * 长按 5 秒 + 确认后退出。
 */
class LockActivity : Activity() {

    companion object {
        fun start(ctx: Context) {
            val intent = Intent(ctx, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            ctx.startActivity(intent)
        }

        private const val EXIT_HOLD_MS = 20000L
    }

    // 应用应用内语言覆盖（原生文案跟随 App 内语言设置）
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LockManager.localized(newBase))
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countdownText: TextView
    private lateinit var titleText: TextView
    private lateinit var holdProgress: ProgressBar

    private var holdStart = 0L
    private var finishing = false
    private var screenOff = false

    /** 息屏/亮屏监听：息屏期间不抢前台（省电），亮屏后自动恢复锁定界面 */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOff = true
                Intent.ACTION_SCREEN_ON -> {
                    screenOff = false
                    if (!finishing &&
                        LockManager.getLockType(this@LockActivity) != LockManager.TYPE_NONE
                    ) {
                        try {
                            start(this@LockActivity)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (finishing) return
            val remaining = LockManager.getRemainingMillis(this@LockActivity)
            val type = LockManager.getLockType(this@LockActivity)

            if (type == LockManager.TYPE_NONE) {
                finishLock()
                return
            }

            if (type == LockManager.TYPE_MANUAL) {
                if (remaining <= 0) {
                    // completeLock 已在 getLockType 中补记
                    finishLock()
                    return
                }
                val totalSec = remaining / 1000
                countdownText.text = String.format("%02d:%02d", totalSec / 60, totalSec % 60)
            }
            handler.postDelayed(this, 250)
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (LockManager.getLockType(this@LockActivity) == LockManager.TYPE_NONE) {
                finishLock()
            } else {
                updateTitle()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 锁屏之上也可显示：息屏后亮屏，锁定界面直接出现在锁屏之上，无需解锁。
        // 注意：不使用 turnScreenOn / FLAG_TURN_SCREEN_ON / KEEP_SCREEN_ON——
        // 这些标志会导致电源键息屏后屏幕被强制重新点亮。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        // 沉浸式全屏，隐藏状态栏/导航栏
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        setContentView(buildContentView())
        updateTitle()
        tryScreenPinning()

        // 息屏/亮屏监听注册在 onCreate（onPause 会注销就收不到息屏事件了）
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, screenFilter)
        }
    }

    /**
     * 屏幕固定：仅设备所有者（ADB 设置）时启用——静默固定、无弹窗。
     * 普通模式不固定（每次都有系统确认框，太繁琐），
     * 由"onPause 延迟抢回 + 无障碍服务"提供防逃逸。
     */
    private fun tryScreenPinning() {
        if (LockManager.getLockType(this) != LockManager.TYPE_MANUAL) return
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, DeviceAdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(admin, arrayOf(packageName))
                startLockTask()
            }
        } catch (_: Exception) {
            // 忽略，由无障碍服务兜底
        }
    }

    private fun safeStopPinning() {
        try {
            stopLockTask()
        } catch (_: Exception) {}
    }

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#102027"))
            setPadding(64, 64, 64, 48)
        }

        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))

        titleText = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(titleText)

        countdownText = TextView(this).apply {
            textSize = 72f
            setTextColor(Color.parseColor("#4FC3F7"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 48)
        }
        root.addView(countdownText)

        val hintText = TextView(this).apply {
            text = getString(R.string.lock_hint)
            textSize = 16f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }
        root.addView(hintText)

        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))

        // 长按进度条：仅按住时可见
        holdProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        root.addView(holdProgress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            8
        ).apply { setMargins(96, 0, 96, 16) })

        // 紧急退出：底部中间不可见触控区，长按 5 秒触发（按住时显示进度条反馈）。
        // 界面不留文字提示——退出方式在"开始专注"按钮下方已前置告知，
        // 避免锁屏期间持续诱惑用户放弃。
        val exitZone = View(this).apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        holdStart = System.currentTimeMillis()
                        holdProgress.visibility = View.VISIBLE
                        handler.post(holdTick)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(holdTick)
                        holdProgress.progress = 0
                        holdProgress.visibility = View.GONE
                    }
                }
                true
            }
        }
        val zoneWidth = (resources.displayMetrics.density * 240).toInt()
        val zoneHeight = (resources.displayMetrics.density * 64).toInt()
        root.addView(exitZone, LinearLayout.LayoutParams(zoneWidth, zoneHeight))

        return root
    }

    private val holdTick = object : Runnable {
        override fun run() {
            // 行走锁定 1 秒即可解除（真实行走场景可能需要紧急用手机），专注 20 秒
            val holdMs = if (LockManager.getLockType(this@LockActivity) ==
                LockManager.TYPE_WALKING
            ) 1000L else EXIT_HOLD_MS
            val held = System.currentTimeMillis() - holdStart
            holdProgress.progress = (held * 100 / holdMs).toInt().coerceAtMost(100)
            if (held >= holdMs) {
                handler.removeCallbacks(this)
                holdProgress.progress = 0
                holdProgress.visibility = View.GONE
                confirmEmergencyExit()
            } else {
                handler.postDelayed(this, 50)
            }
        }
    }

    private fun confirmEmergencyExit() {
        val isManual = LockManager.getLockType(this) == LockManager.TYPE_MANUAL
        val message = if (isManual) {
            val remainingMin = (LockManager.getRemainingMillis(this) / 60_000).toInt() + 1
            getString(R.string.lock_exit_message_manual, remainingMin)
        } else {
            getString(R.string.lock_exit_message_walking)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.lock_exit_title)
            .setMessage(message)
            .setNegativeButton(R.string.lock_exit_keep, null)
            .setPositiveButton(R.string.lock_exit_confirm) { _, _ ->
                LockManager.completeLock(this, success = false)
                finishLock()
            }
            .show()
    }

    private fun updateTitle() {
        when (LockManager.getLockType(this)) {
            LockManager.TYPE_WALKING -> {
                titleText.text = getString(R.string.lock_title_walking)
                countdownText.text = "🚶"
                countdownText.textSize = 96f
            }
            else -> {
                titleText.text = getString(R.string.lock_title_focus)
                countdownText.textSize = 72f
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(LockManager.ACTION_LOCK_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stateReceiver, filter)
        }
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
        // 注意：这里不做"抢回前台"——
        // 1. 防逃逸由无障碍服务负责（50ms 事件超时，足够快）
        // 2. 按电源键息屏时抢前台会点亮屏幕，且与息屏广播存在竞态
        // 亮屏恢复由 screenReceiver 的 ACTION_SCREEN_ON 处理
    }

    private fun finishLock() {
        finishing = true
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        safeStopPinning()
        finish()
    }

    @Deprecated("锁定期间禁用返回键")
    override fun onBackPressed() {
        // 锁定期间禁止返回
    }
}
