/// 格式化工具函数（纯函数，便于单元测试；文案走 S 国际化）
library;

import '../l10n/strings.dart';

/// 秒数 → "MM:SS"（与语言无关）
String formatSeconds(int seconds) {
  if (seconds < 0) seconds = 0;
  final m = seconds ~/ 60;
  final s = seconds % 60;
  return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
}

/// 分钟数 → 本地化时长文本（"1 小时 30 分钟" / "1h 30m"）
String formatMinutes(int minutes) {
  if (minutes < 0) minutes = 0;
  final h = minutes ~/ 60;
  final m = minutes % 60;
  if (h == 0) return S.trf('fmt_minutes', {'m': m});
  return S.trf('fmt_hours_minutes', {'h': h, 'm': m});
}
