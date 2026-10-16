import 'package:flutter/services.dart';

/// 原生通道封装：锁定控制、统计、行走检测开关。
///
/// 所有状态与统计持久化在原生侧（LockManager），
/// Flutter 引擎被杀、手机重启后仍可恢复。
class NativeService {
  static const _channel = MethodChannel('io.github.cosmichut/channel');
  static const _events = EventChannel('io.github.cosmichut/events');

  // ---------- 锁定 ----------

  static Future<void> startLock(int minutes) =>
      _channel.invokeMethod('startLock', {'minutes': minutes});

  static Future<void> stopLock({required bool success}) =>
      _channel.invokeMethod('stopLock', {'success': success});

  /// 返回 {type: none|manual|walking, remaining_millis: int, duration_min: int}
  static Future<Map<String, dynamic>> getLockState() async {
    final result = await _channel.invokeMethod<Map>('getLockState');
    return result?.cast<String, dynamic>() ??
        {'type': 'none', 'remaining_millis': 0, 'duration_min': 0};
  }

  /// 锁定状态变化流（原生广播转发）
  static Stream<Map<String, dynamic>> get lockStateStream =>
      _events.receiveBroadcastStream().map(
            (e) => (e as Map).cast<String, dynamic>(),
          );

  // ---------- 统计 ----------

  static Future<Map<String, int>> getStats() async {
    final result = await _channel.invokeMethod<Map>('getStats');
    return result?.map((k, v) => MapEntry(k.toString(), (v as num).toInt())) ??
        {};
  }

  // ---------- 行走检测 ----------

  static Future<void> setWalkingEnabled(bool enabled) =>
      _channel.invokeMethod('setWalkingEnabled', {'enabled': enabled});

  static Future<bool> isWalkingEnabled() async =>
      await _channel.invokeMethod<bool>('isWalkingEnabled') ?? false;

  static Future<void> setWalkingThreshold(double threshold) =>
      _channel.invokeMethod('setWalkingThreshold', {'threshold': threshold});

  static Future<double> getWalkingThreshold() async =>
      await _channel.invokeMethod<double>('getWalkingThreshold') ?? 2.0;

  // ---------- 权限 ----------

  /// 返回 {accessibility, device_admin, notification, phone, battery_ignored}
  static Future<Map<String, bool>> getPermissionStatus() async {
    final result = await _channel.invokeMethod<Map>('getPermissionStatus');
    return result?.map((k, v) => MapEntry(k.toString(), v as bool)) ?? {};
  }

  static Future<void> openAccessibilitySettings() =>
      _channel.invokeMethod('openAccessibilitySettings');

  static Future<void> requestDeviceAdmin() =>
      _channel.invokeMethod('requestDeviceAdmin');

  static Future<void> removeDeviceAdmin() =>
      _channel.invokeMethod('removeDeviceAdmin');

  static Future<void> requestNotificationPermission() =>
      _channel.invokeMethod('requestNotificationPermission');

  static Future<void> requestPhonePermission() =>
      _channel.invokeMethod('requestPhonePermission');

  static Future<void> requestIgnoreBatteryOptimizations() =>
      _channel.invokeMethod('requestIgnoreBatteryOptimizations');

  /// 打开厂商自启动/后台运行管理页（荣耀/小米/OPPO/vivo，兜底应用详情页）
  static Future<void> openBackgroundSettings() =>
      _channel.invokeMethod('openBackgroundSettings');

  /// 从 APK 元数据读取版本号（永远与构建一致）
  static Future<String> getAppVersion() async =>
      await _channel.invokeMethod<String>('getAppVersion') ?? '';

  /// 当前行走检测模式："step"（硬件步态检测器）或 "variance"（方差回退）
  static Future<String> getWalkingMode() async =>
      await _channel.invokeMethod<String>('getWalkingMode') ?? 'variance';

  /// 同步语言覆盖到原生层（锁定页/悬浮层/通知/Toast 跟随 App 内语言设置）。
  /// code: 'zh' / 'en' / null（跟随系统）
  static Future<void> setLocaleOverride(String? code) =>
      _channel.invokeMethod('setLocaleOverride', {'code': code});

  // ---------- 应用限额 ----------

  /// 已安装的候选应用：[{package, name}]
  static Future<List<Map<String, String>>> getInstalledCandidates() async {
    final result = await _channel.invokeMethod<List>('getInstalledCandidates');
    return (result ?? [])
        .map((e) => (e as Map).map((k, v) => MapEntry('$k', '$v')))
        .toList();
  }

  /// 限额表与今日用量：{limits: {pkg: 分钟}, usage: {pkg: 秒}}
  static Future<(Map<String, int>, Map<String, int>)> getAppLimits() async {
    final result = await _channel.invokeMethod<Map>('getAppLimits');
    if (result == null) return (<String, int>{}, <String, int>{});
    Map<String, int> convert(String key) =>
        (result[key] as Map?)?.map((k, v) => MapEntry('$k', (v as num).toInt())) ??
        <String, int>{};
    return (convert('limits'), convert('usage'));
  }

  static Future<void> setAppLimit(String pkg, int minutes) => _channel
      .invokeMethod('setAppLimit', {'package': pkg, 'minutes': minutes});

  static Future<void> removeAppLimit(String pkg) =>
      _channel.invokeMethod('removeAppLimit', {'package': pkg});
}
