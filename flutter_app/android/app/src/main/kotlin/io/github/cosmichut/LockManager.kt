package io.github.cosmichut

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 锁定状态与统计的中心管理器。
 *
 * 所有状态持久化在原生 SharedPreferences 中，
 * 保证 Flutter 引擎被杀、手机重启后仍可恢复锁定与统计。
 */
object LockManager {

    const val ACTION_LOCK_STATE_CHANGED = "io.github.cosmichut.LOCK_STATE_CHANGED"

    private const val PREFS = "self_discipline_state"

    private const val KEY_LOCK_TYPE = "lock_type"           // none / manual / walking
    private const val KEY_LOCK_END_TIME = "lock_end_time"   // manual 锁定的截止毫秒
    private const val KEY_LOCK_DURATION_MIN = "lock_duration_min"
    private const val KEY_WALKING_ENABLED = "walking_enabled"
    private const val KEY_WALKING_THRESHOLD = "walking_threshold"
    private const val KEY_CALL_ACTIVE = "call_active"

    private const val KEY_SUCCESS_COUNT = "success_count"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_TOTAL_MINUTES = "total_minutes"
    private const val KEY_CONSECUTIVE_DAYS = "consecutive_days"
    private const val KEY_LAST_SUCCESS_DATE = "last_success_date"
    private const val KEY_WALKING_INTERVENTIONS = "walking_interventions"
    private const val KEY_WALKING_HEARTBEAT = "walking_heartbeat"
    private const val KEY_WALKING_MODE = "walking_mode"
    private const val KEY_ACCESSIBILITY_WAS_ENABLED = "accessibility_was_enabled"
    private const val KEY_APP_LIMITS = "app_limits"   // StringSet，元素格式 "pkg:minutes"
    private const val USAGE_PREFIX = "usage_"         // usage_{pkg}_{yyyy-MM-dd} = 秒
    private const val KEY_LOCALE_OVERRIDE = "locale_override"  // "zh" / "en" / null=跟随系统

    const val TYPE_NONE = "none"
    const val TYPE_MANUAL = "manual"
    const val TYPE_WALKING = "walking"

    const val DEFAULT_THRESHOLD = 3.0f   // 默认低灵敏度（真机反馈 2.0 易误触）

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- 锁定状态 ----------

    fun startManualLock(ctx: Context, durationMinutes: Int) {
        prefs(ctx).edit()
            .putString(KEY_LOCK_TYPE, TYPE_MANUAL)
            .putInt(KEY_LOCK_DURATION_MIN, durationMinutes)
            .putLong(KEY_LOCK_END_TIME, System.currentTimeMillis() + durationMinutes * 60_000L)
            .apply()
        notifyChanged(ctx)
    }

    fun startWalkingLock(ctx: Context) {
        if (getLockType(ctx) != TYPE_NONE) return // 手动锁定优先，不覆盖
        prefs(ctx).edit().putString(KEY_LOCK_TYPE, TYPE_WALKING).apply()
        prefs(ctx).edit().putInt(KEY_WALKING_INTERVENTIONS, getWalkingInterventions(ctx) + 1).apply()
        notifyChanged(ctx)
    }

    fun getLockType(ctx: Context): String {
        val type = prefs(ctx).getString(KEY_LOCK_TYPE, TYPE_NONE) ?: TYPE_NONE
        if (type == TYPE_MANUAL && getRemainingMillis(ctx) <= 0) {
            // 截止时间已过但未被正常结算（例如进程被杀），补记为成功
            completeLock(ctx, success = true)
            return TYPE_NONE
        }
        return type
    }

    fun getRemainingMillis(ctx: Context): Long {
        if (prefs(ctx).getString(KEY_LOCK_TYPE, TYPE_NONE) != TYPE_MANUAL) return 0
        return prefs(ctx).getLong(KEY_LOCK_END_TIME, 0) - System.currentTimeMillis()
    }

    fun getLockDurationMin(ctx: Context): Int =
        prefs(ctx).getInt(KEY_LOCK_DURATION_MIN, 0)

    /**
     * 结束当前锁定并结算统计。
     * 行走锁定不产生成功/失败记录（不是专注会话）。
     */
    fun completeLock(ctx: Context, success: Boolean) {
        val type = prefs(ctx).getString(KEY_LOCK_TYPE, TYPE_NONE) ?: TYPE_NONE
        if (type == TYPE_NONE) return

        if (type == TYPE_MANUAL) {
            val p = prefs(ctx)
            if (success) {
                val elapsed = p.getInt(KEY_LOCK_DURATION_MIN, 0)
                p.edit()
                    .putInt(KEY_SUCCESS_COUNT, p.getInt(KEY_SUCCESS_COUNT, 0) + 1)
                    .putInt(KEY_TOTAL_MINUTES, p.getInt(KEY_TOTAL_MINUTES, 0) + elapsed)
                    .apply()
                updateConsecutiveDays(ctx)
            } else {
                p.edit().putInt(KEY_FAIL_COUNT, p.getInt(KEY_FAIL_COUNT, 0) + 1).apply()
            }
        }

        prefs(ctx).edit()
            .putString(KEY_LOCK_TYPE, TYPE_NONE)
            .putLong(KEY_LOCK_END_TIME, 0)
            .apply()
        notifyChanged(ctx)
    }

    private fun updateConsecutiveDays(ctx: Context) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = fmt.format(Date())
        val p = prefs(ctx)
        val last = p.getString(KEY_LAST_SUCCESS_DATE, "") ?: ""
        if (last == today) return

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = fmt.format(cal.time)

        val days = if (last == yesterday) p.getInt(KEY_CONSECUTIVE_DAYS, 0) + 1 else 1
        p.edit()
            .putInt(KEY_CONSECUTIVE_DAYS, days)
            .putString(KEY_LAST_SUCCESS_DATE, today)
            .apply()
    }

    // ---------- 行走检测配置 ----------

    fun isWalkingEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WALKING_ENABLED, false)

    fun setWalkingEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_WALKING_ENABLED, enabled).apply()
        // 广播通知：无障碍服务的看门狗据此启停（避免未启用时空转）
        notifyChanged(ctx)
    }

    fun getWalkingThreshold(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_WALKING_THRESHOLD, DEFAULT_THRESHOLD)

    fun setWalkingThreshold(ctx: Context, threshold: Float) {
        prefs(ctx).edit().putFloat(KEY_WALKING_THRESHOLD, threshold).apply()
    }

    // ---------- 通话状态（用于来电豁免） ----------

    fun setCallActive(ctx: Context, active: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CALL_ACTIVE, active).apply()
    }

    fun isCallActive(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CALL_ACTIVE, false)

    // ---------- 统计读取 ----------

    fun getStats(ctx: Context): Map<String, Int> {
        val p = prefs(ctx)
        return mapOf(
            "success_count" to p.getInt(KEY_SUCCESS_COUNT, 0),
            "fail_count" to p.getInt(KEY_FAIL_COUNT, 0),
            "total_minutes" to p.getInt(KEY_TOTAL_MINUTES, 0),
            "consecutive_days" to p.getInt(KEY_CONSECUTIVE_DAYS, 0),
            "walking_interventions" to p.getInt(KEY_WALKING_INTERVENTIONS, 0),
        )
    }

    fun getWalkingInterventions(ctx: Context): Int =
        prefs(ctx).getInt(KEY_WALKING_INTERVENTIONS, 0)

    // ---------- 行走服务心跳（用于检测服务是否被系统杀掉） ----------

    fun touchWalkingHeartbeat(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_WALKING_HEARTBEAT, System.currentTimeMillis()).apply()
    }

    /** 90 秒内心跳存在视为服务存活 */
    fun isWalkingServiceAlive(ctx: Context): Boolean {
        val last = prefs(ctx).getLong(KEY_WALKING_HEARTBEAT, 0)
        return last > 0 && System.currentTimeMillis() - last < 90_000
    }

    // ---------- 行走检测模式（"step" / "variance"，诊断展示用） ----------

    fun setWalkingMode(ctx: Context, mode: String) {
        if (getWalkingMode(ctx) == mode) return
        prefs(ctx).edit().putString(KEY_WALKING_MODE, mode).apply()
    }

    fun getWalkingMode(ctx: Context): String =
        prefs(ctx).getString(KEY_WALKING_MODE, "variance") ?: "variance"

    // ---------- 无障碍服务"曾经开启过"标记（用于识别 ROM 撤销服务的情况） ----------

    fun setAccessibilityWasEnabled(ctx: Context, was: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ACCESSIBILITY_WAS_ENABLED, was).apply()
    }

    fun wasAccessibilityEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ACCESSIBILITY_WAS_ENABLED, false)

    // ---------- 语言覆盖（应用内语言切换同步到原生层） ----------

    fun setLocaleOverride(ctx: Context, code: String?) {
        if (code == null) {
            prefs(ctx).edit().remove(KEY_LOCALE_OVERRIDE).apply()
        } else {
            prefs(ctx).edit().putString(KEY_LOCALE_OVERRIDE, code).apply()
        }
    }

    /**
     * 应用语言覆盖后的上下文：原生界面（锁定页/悬浮层/通知/Toast）的文案
     * 跟随应用内语言设置，而非仅跟随系统语言。
     */
    fun localized(ctx: Context): Context {
        val code = prefs(ctx).getString(KEY_LOCALE_OVERRIDE, null) ?: return ctx
        val config = android.content.res.Configuration(ctx.resources.configuration)
        config.setLocale(java.util.Locale(code))
        return ctx.createConfigurationContext(config)
    }

    // ---------- 应用每日限额 ----------

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** 限额表：包名 → 每日分钟数 */
    fun getAppLimits(ctx: Context): Map<String, Int> =
        (prefs(ctx).getStringSet(KEY_APP_LIMITS, emptySet()) ?: emptySet())
            .mapNotNull {
                val i = it.lastIndexOf(':')
                if (i <= 0) null else it.substring(0, i) to (it.substring(i + 1).toIntOrNull() ?: 0)
            }
            .filter { it.second > 0 }
            .toMap()

    fun setAppLimit(ctx: Context, pkg: String, minutes: Int) {
        val map = getAppLimits(ctx).toMutableMap()
        map[pkg] = minutes
        prefs(ctx).edit()
            .putStringSet(KEY_APP_LIMITS, map.map { "${it.key}:${it.value}" }.toSet())
            .apply()
    }

    fun removeAppLimit(ctx: Context, pkg: String) {
        val map = getAppLimits(ctx).toMutableMap()
        map.remove(pkg)
        prefs(ctx).edit()
            .putStringSet(KEY_APP_LIMITS, map.map { "${it.key}:${it.value}" }.toSet())
            .apply()
    }

    fun getUsageSecondsToday(ctx: Context, pkg: String): Int =
        prefs(ctx).getInt("$USAGE_PREFIX${pkg}_${today()}", 0)

    fun addUsageSeconds(ctx: Context, pkg: String, seconds: Int) {
        if (seconds <= 0) return
        val p = prefs(ctx)
        val key = "$USAGE_PREFIX${pkg}_${today()}"
        p.edit().putInt(key, p.getInt(key, 0) + seconds).apply()
        // 顺带清理非当日的历史用量键
        val t = today()
        val stale = p.all.keys.filter { it.startsWith(USAGE_PREFIX) && !it.endsWith(t) }
        if (stale.isNotEmpty()) {
            val e = p.edit()
            stale.forEach { e.remove(it) }
            e.apply()
        }
    }

    /** 该应用今日是否已达限额 */
    fun isOverLimit(ctx: Context, pkg: String): Boolean {
        val limit = getAppLimits(ctx)[pkg] ?: return false
        return getUsageSecondsToday(ctx, pkg) >= limit * 60
    }

    private fun notifyChanged(ctx: Context) {
        val intent = Intent(ACTION_LOCK_STATE_CHANGED).setPackage(ctx.packageName)
        ctx.sendBroadcast(intent)
    }
}
