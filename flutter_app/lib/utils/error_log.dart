import 'package:flutter/foundation.dart';

/// 应用内诊断：捕获全局异常，供设置页直接展示（免 adb 排查）。
class ErrorLog {
  ErrorLog._();

  static final ValueNotifier<String> last = ValueNotifier('');

  static void record(String message) {
    last.value = message.length > 1500
        ? '${message.substring(0, 1500)}…'
        : message;
    debugPrint('ErrorLog: $message');
  }

  static void clear() => last.value = '';
}
