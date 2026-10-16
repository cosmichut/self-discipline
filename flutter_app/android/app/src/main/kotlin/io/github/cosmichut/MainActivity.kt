package io.github.cosmichut

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "io.github.cosmichut/channel"
    private val EVENTS = "io.github.cosmichut/events"

    private var eventSink: EventChannel.EventSink? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            sendLockState()
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startLock" -> {
                        val minutes = call.argument<Int>("minutes") ?: 25
                        LockManager.startManualLock(this, minutes)
                        LockActivity.start(this)
                        result.success(null)
                    }
                    "stopLock" -> {
                        val success = call.argument<Boolean>("success") ?: false
                        LockManager.completeLock(this, success)
                        result.success(null)
                    }
                    "getLockState" -> result.success(getLockStateMap())
                    "getStats" -> result.success(LockManager.getStats(this))
                    "setWalkingEnabled" -> {
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        LockManager.setWalkingEnabled(this, enabled)
                        if (enabled) WalkingService.start(this) else WalkingService.stop(this)
                        result.success(null)
                    }
                    "isWalkingEnabled" -> result.success(LockManager.isWalkingEnabled(this))
                    "setWalkingThreshold" -> {
                        val threshold = call.argument<Double>("threshold") ?: 2.0
                        LockManager.setWalkingThreshold(this, threshold.toFloat())
                        result.success(null)
                    }
                    "getWalkingThreshold" ->
                        result.success(LockManager.getWalkingThreshold(this).toDouble())
                    "getPermissionStatus" -> result.success(getPermissionStatus())
                    "openAccessibilitySettings" -> {
                        try {
                            // 附带组件名参数，支持的 ROM 会直接高亮本服务条目
                            val cn = ComponentName(this, LockAccessibilityService::class.java)
                            val args = android.os.Bundle().apply {
                                putString(":settings:fragment_args_key", cn.flattenToString())
                            }
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                putExtra(":settings:fragment_args_key", cn.flattenToString())
                                putExtra(":settings:show_fragment_args", args)
                            }
                            startActivity(intent)
                        } catch (_: Exception) {
                            // 部分 ROM 无障碍入口异常，兜底跳应用详情页
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            })
                        }
                        result.success(null)
                    }
                    "requestDeviceAdmin" -> {
                        requestDeviceAdmin()
                        result.success(null)
                    }
                    "removeDeviceAdmin" -> {
                        removeDeviceAdmin()
                        result.success(null)
                    }
                    "requestNotificationPermission" -> {
                        requestNotificationPermission()
                        result.success(null)
                    }
                    "requestPhonePermission" -> {
                        requestPhonePermission()
                        result.success(null)
                    }
                    "requestIgnoreBatteryOptimizations" -> {
                        requestIgnoreBatteryOptimizations()
                        result.success(null)
                    }
                    "openBackgroundSettings" -> {
                        openBackgroundSettings()
                        result.success(null)
                    }
                    "getAppVersion" -> {
                        result.success(packageManager.getPackageInfo(packageName, 0).versionName)
                    }
                    "getWalkingMode" -> {
                        result.success(LockManager.getWalkingMode(this))
                    }
                    "setLocaleOverride" -> {
                        val code = call.argument<String>("code")
                        LockManager.setLocaleOverride(this, code)
                        result.success(null)
                    }
                    "getInstalledCandidates" -> {
                        result.success(getAllInstalledApps())
                    }
                    "getAppLimits" -> {
                        val limits = LockManager.getAppLimits(this)
                        val usage = limits.keys.associateWith { LockManager.getUsageSecondsToday(this, it) }
                        result.success(mapOf(
                            "limits" to limits,
                            "usage" to usage,
                        ))
                    }
                    "setAppLimit" -> {
                        val pkg = call.argument<String>("package") ?: ""
                        val minutes = call.argument<Int>("minutes") ?: 30
                        if (pkg.isNotEmpty()) LockManager.setAppLimit(this, pkg, minutes)
                        result.success(null)
                    }
                    "removeAppLimit" -> {
                        val pkg = call.argument<String>("package") ?: ""
                        if (pkg.isNotEmpty()) LockManager.removeAppLimit(this, pkg)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENTS)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                    eventSink = events
                    sendLockState()
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            })
    }

    private fun getLockStateMap(): Map<String, Any> = mapOf(
        "type" to LockManager.getLockType(this),
        "remaining_millis" to LockManager.getRemainingMillis(this).coerceAtLeast(0),
        "duration_min" to LockManager.getLockDurationMin(this),
    )

    private fun sendLockState() {
        eventSink?.success(getLockStateMap())
    }

    private fun getPermissionStatus(): Map<String, Boolean> {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryIgnored = pm.isIgnoringBatteryOptimizations(packageName)

        val notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        val phoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

        return mapOf(
            "accessibility" to LockAccessibilityService.isEnabled(this),
            "device_admin" to dpm.isAdminActive(admin),
            "notification" to notifGranted,
            "phone" to phoneGranted,
            "battery_ignored" to batteryIgnored,
            "walking_service_running" to LockManager.isWalkingServiceAlive(this),
        )
    }

    /** 返回所有已安装且有启动图标的应用：[{package, name}]，按名称排序 */
    private fun getAllInstalledApps(): List<Map<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map {
                mapOf(
                    "package" to it.activityInfo.packageName,
                    "name" to it.loadLabel(packageManager).toString()
                )
            }
            .filter { it["package"] != packageName }  // 排除自身
            .distinctBy { it["package"] }
            .sortedBy { it["name"] }
    }

    /** 打开厂商自启动/后台运行管理页（荣耀/小米/OPPO/vivo，兜底应用详情页） */
    private fun openBackgroundSettings() {
        val intents = listOf(
            // 华为/荣耀：应用启动管理
            Intent().setComponent(ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            // 小米：自启动管理
            Intent().setComponent(ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            // 三星：设备维护（电池后台限制）
            Intent().setComponent(ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.lool.ui.SplashActivity")),
            // OPPO
            Intent().setComponent(ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            // vivo
            Intent().setComponent(ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun requestDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    LockManager.localized(this@MainActivity).getString(R.string.admin_explanation)
                )
            }
            startActivity(intent)
        }
    }

    private fun removeDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.removeActiveAdmin(admin)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun requestPhonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 1002)
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // 独立看门狗（AlarmManager）：不受自启动管理限制
        WatchdogScheduler.schedule(this)
        // 记录无障碍服务"曾经开启"状态：用于识别 ROM 撤销服务的场景并提醒用户
        if (LockAccessibilityService.isEnabled(this)) {
            LockManager.setAccessibilityWasEnabled(this, true)
        }
        // 行走检测已开启时，确保服务在运行（被杀后自动拉起；前台启动 FGS 不受限制）
        if (LockManager.isWalkingEnabled(this)) {
            WalkingService.start(this)
        }
        val filter = IntentFilter(LockManager.ACTION_LOCK_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stateReceiver, filter)
        }
        sendLockState()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
    }
}
