package io.github.cosmichut

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.os.BatteryManager
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 行走检测前台服务。
 *
 * - 常驻前台（通知栏可见），监听加速度传感器计算方差判断是否行走
 * - 行走时启动行走锁定（LockActivity），静止后解除
 * - 来电/通话中抑制锁定（来电豁免）
 * - 检测阈值可在设置中调整（灵敏度）
 */
class WalkingService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START = "io.github.cosmichut.WALKING_START"
        const val ACTION_STOP = "io.github.cosmichut.WALKING_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "walking_detection"

        private const val WINDOW_MS = 2500L        // 方差判定的时间窗口（最近2.5秒，回退模式）
        private const val MIN_SAMPLES = 8          // 判定所需最少样本（回退模式）
        private const val CHECK_INTERVAL_MS = 1000L

        // 步态检测器模式（优先）：STEP_WINDOW_MS 内步数事件 ≥ 门槛即判定行走
        private const val STEP_WINDOW_MS = 6000L
        // 灵敏度阈值 → 6 秒窗口内所需最少步数（正常步频约 2 步/秒）
        private const val STEP_THRESHOLD_HIGH = 2
        private const val STEP_THRESHOLD_MID = 3
        private const val STEP_THRESHOLD_LOW = 5
        // 步态传感器"活跃"判定：当前窗口内 ≥ 2 个步事件才算在持续投递。
        // 部分 ROM 的步态检测器偶尔吐零星事件（多数时间不工作），
        // 若仅凭超时判断会被这种"假活"锁死在死模式
        private const val STEP_SENSOR_ACTIVE_MIN_EVENTS = 2

        // 迟滞缓冲（防呆）：持续行走 ENTER_DELAY_MS 才锁定（滤掉拿起/放下手机的误触发），
        // 停止行走 EXIT_DELAY_MS 才解除
        private const val ENTER_DELAY_MS = 5000L
        private const val EXIT_DELAY_MS = 2500L

        // 周期性判据（位移代理）：加速度计无法直接测位移（双重积分漂移），
        // 改用"往复摆动次数"——步行是 ~2Hz 规律摆动（2.5 秒窗口 ≥ 5 次过零），
        // 拿起/放下手机是一次性瞬态（1-3 次过零），被此条件排除
        private const val PERIODIC_MIN_CROSSINGS = 5
        private const val CROSSING_EPSILON = 0.15f  // 过零判定死区（m/s²），防噪声抖动

        // 进入倒计时的条纹容忍：证据中断小于该时长不清零（手持步行时信号
        // 会有边缘波动，单次误判不该让触发倒计时从头再数）
        private const val STREAK_BREAK_MS = 2000L

        fun start(ctx: Context) {
            val intent = Intent(ctx, WalkingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(ctx, intent)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, WalkingService::class.java).setAction(ACTION_STOP))
        }
    }

    private lateinit var sensorManager: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    // 时间戳 + 加速度模长；按时间窗口裁剪（而不是固定个数），
    // 保证停止行走后证据在 WINDOW_MS 内消失，解除延迟可预期
    private val magnitudes = ArrayDeque<Pair<Long, Float>>()
    // 步态检测器模式：步伐事件时间戳
    private val stepTimestamps = ArrayDeque<Long>()
    private var usingStepDetector = false
    private var isWalking = false
    private var walkingSince = 0L       // 连续行走证据的起点（0 = 当前无连续行走）
    private var lastWalkingEvidence = 0L // 最后一次检测到行走的时刻
    private var running = false          // 防重复注册传感器/计时器
    private var beatCounter = 0          // 心跳计数（每 30 次检测写一次）

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            evaluateWalking()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    @Suppress("DEPRECATION")
    private val callListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            val active = state != TelephonyManager.CALL_STATE_IDLE
            LockManager.setCallActive(this@WalkingService, active)
            if (active && LockManager.getLockType(this@WalkingService) == LockManager.TYPE_WALKING) {
                // 来电时解除行走锁定，允许接听
                LockManager.completeLock(this@WalkingService, success = true)
            }
            if (!active) {
                // 通话结束后重置检测计时：若仍在行走，2 秒后重新触发锁定
                walkingSince = 0L
                isWalking = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                startDetection()
            }
        }
        return START_STICKY
    }

    private fun startDetection() {
        if (running) return
        running = true
        LockManager.touchWalkingHeartbeat(this)

        // 服务重启（被 ROM 杀掉后由看门狗拉起）时若仍处于行走锁定，
        // 必须恢复 isWalking ——否则退出分支条件 isWalking=false 永远不满足，
        // 锁定永远无法解除，悬浮层会一直挂着
        if (LockManager.getLockType(this) == LockManager.TYPE_WALKING) {
            isWalking = true
            lastWalkingEvidence = System.currentTimeMillis()
        }

        // 优先使用硬件步态检测器，且优先唤醒型（部分 ROM 非唤醒型步态检测器
        // 熄屏/后台不投递事件）；加速度计始终注册作为兜底证据源
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepDetector != null) {
            usingStepDetector = true
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)
        } else {
            usingStepDetector = false
        }
        // 加速度计兜底：步态事件 30 秒未投递时自动改用方差判定
        // SENSOR_DELAY_UI（~60ms）提供足够细的颗粒度，配合 2.5 秒时间窗口
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        } else if (stepDetector == null) {
            return
        }

        // 熄屏后（手机在口袋）仍需持续接收传感器事件，持有部分唤醒锁
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SelfDiscipline::Walking")
        wakeLock?.acquire()

        // 通话状态监听（来电豁免）
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            tm.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE)
        }

        handler.post(checkRunnable)
    }

    private fun evaluateWalking() {
        val now = System.currentTimeMillis()

        // 心跳：证明服务存活，供首页展示真实运行状态
        if (++beatCounter % 30 == 0) LockManager.touchWalkingHeartbeat(this)

        // 步态传感器活跃性自适应：当前窗口内步事件不足 → 视为传感器未在
        // 投递（部分 ROM 偶尔吐零星事件），立即回退方差模式
        val useSteps = usingStepDetector &&
            stepTimestamps.size >= STEP_SENSOR_ACTIVE_MIN_EVENTS
        LockManager.setWalkingMode(this, if (useSteps) "step" else "variance")
        val walkingNow = if (useSteps) {
            evaluateByStepDetector(now)
        } else {
            evaluateByVariance(now)
        }
        applyHysteresis(now, walkingNow)
    }

    /** 步态检测器模式：最近 STEP_WINDOW_MS 内步数 ≥ 门槛 → 行走 */
    private fun evaluateByStepDetector(now: Long): Boolean {
        while (stepTimestamps.isNotEmpty() && now - stepTimestamps.first() > STEP_WINDOW_MS) {
            stepTimestamps.removeFirst()
        }
        if (stepTimestamps.isEmpty()) return false

        val threshold = when {
            LockManager.getWalkingThreshold(this) <= 1.2f -> STEP_THRESHOLD_HIGH
            LockManager.getWalkingThreshold(this) <= 2.0f -> STEP_THRESHOLD_MID
            else -> STEP_THRESHOLD_LOW
        }
        // 记录最近一次步伐作为行走证据（供退出迟滞使用）
        lastWalkingEvidence = stepTimestamps.last()
        return !isCharging() && stepTimestamps.size >= threshold
    }

    /** 方差法（回退）：最近 WINDOW_MS 加速度方差 > 阈值 → 行走 */
    private fun evaluateByVariance(now: Long): Boolean {
        while (magnitudes.isNotEmpty() && now - magnitudes.first().first > WINDOW_MS) {
            magnitudes.removeFirst()
        }
        if (magnitudes.size < MIN_SAMPLES) return false

        val values = magnitudes.map { it.second }
        // 3 点移动平均平滑：滤掉充电器电磁干扰等高频噪声（行走是 ~2Hz 低频信号，基本保留）
        val smoothed = FloatArray(values.size)
        for (i in values.indices) {
            val from = if (i > 0) i - 1 else 0
            val to = if (i < values.size - 1) i + 1 else values.size - 1
            smoothed[i] = (values[from] + values[i] + values[to]) / 3f
        }
        val mean = smoothed.average()
        val variance = smoothed.map { (it - mean).pow(2) }.average().toFloat()

        // 周期性（位移代理）：均值去心 + 施密特触发器统计过零次数。
        // 步行 ~2Hz 往复 → 过零次数多；拿起/放下 → 单次瞬态，过零少
        val eps = CROSSING_EPSILON
        var side = 0
        var crossings = 0
        for (v in smoothed) {
            val d = v - mean.toFloat()
            if (d > eps) {
                if (side == -1) crossings++
                side = 1
            } else if (d < -eps) {
                if (side == 1) crossings++
                side = -1
            }
        }

        // 复合判定：能量（方差）+ 周期性（往复次数）双条件
        val walking = !isCharging() &&
            variance > LockManager.getWalkingThreshold(this) &&
            crossings >= PERIODIC_MIN_CROSSINGS
        if (walking) lastWalkingEvidence = now
        return walking
    }

    /** 迟滞缓冲：持续行走 ENTER_DELAY_MS 才锁定，停止 EXIT_DELAY_MS 才解除 */
    private fun applyHysteresis(now: Long, walkingNow: Boolean) {
        if (walkingNow) {
            if (walkingSince == 0L) walkingSince = now
            // 持续行走满 ENTER_DELAY_MS 才触发锁定
            if (!isWalking && now - walkingSince >= ENTER_DELAY_MS) {
                isWalking = true
                // 通话中不触发锁定（来电豁免）
                if (!LockManager.isCallActive(this)) {
                    LockManager.startWalkingLock(this)
                    if (LockAccessibilityService.isEnabled(this)) {
                        // 无障碍悬浮层会响应状态广播自动覆盖到任意应用之上
                        // （不受后台启动 Activity 限制）
                    } else {
                        // 无障碍未开启时的兜底：尝试拉起锁定界面
                        try {
                            LockActivity.start(this)
                        } catch (_: Exception) {}
                    }
                }
            }
        } else {
            // 条纹容忍：证据中断在 STREAK_BREAK_MS 内不清零进入倒计时，
            // 避免手持步行的边缘波动导致倒计时反复重数
            if (now - lastWalkingEvidence > STREAK_BREAK_MS) walkingSince = 0L
            // 连续无行走证据满 EXIT_DELAY_MS 才解除锁定
            if (isWalking && now - lastWalkingEvidence >= EXIT_DELAY_MS) {
                isWalking = false
                if (LockManager.getLockType(this) == LockManager.TYPE_WALKING) {
                    LockManager.completeLock(this, success = true)
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            stepTimestamps.addLast(System.currentTimeMillis())
            while (stepTimestamps.size > 50) stepTimestamps.removeFirst()
            return
        }
        val magnitude = sqrt(
            event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2)
        )
        magnitudes.addLast(System.currentTimeMillis() to magnitude)
        // 兜底上限，防止采样率异常时队列无限增长
        while (magnitudes.size > 500) magnitudes.removeFirst()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun isCharging(): Boolean {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                LockManager.localized(this).getString(R.string.walking_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val l10n = LockManager.localized(this)
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(l10n.getString(R.string.app_name))
            .setContentText(l10n.getString(R.string.walking_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(checkRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            tm.listen(callListener, PhoneStateListener.LISTEN_NONE)
        } catch (_: Exception) {}

        // 服务停止时解除行走锁定（不解除手动锁定）
        if (LockManager.getLockType(this) == LockManager.TYPE_WALKING) {
            LockManager.completeLock(this, success = true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
