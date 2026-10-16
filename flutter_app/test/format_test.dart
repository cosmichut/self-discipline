import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:self_discipline/l10n/strings.dart';
import 'package:self_discipline/utils/format.dart';

void main() {
  // formatMinutes 走 S 国际化，测试固定为中文环境
  setUpAll(() {
    S.overrideLocale.value = const Locale('zh');
  });
  tearDownAll(() {
    S.overrideLocale.value = null;
  });

  group('formatSeconds', () {
    test('formats minutes and seconds', () {
      expect(formatSeconds(0), '00:00');
      expect(formatSeconds(59), '00:59');
      expect(formatSeconds(60), '01:00');
      expect(formatSeconds(1500), '25:00');
      expect(formatSeconds(3661), '61:01');
    });

    test('clamps negative values to zero', () {
      expect(formatSeconds(-5), '00:00');
    });
  });

  group('formatMinutes (zh)', () {
    test('shows only minutes under one hour', () {
      expect(formatMinutes(0), '0 分钟');
      expect(formatMinutes(25), '25 分钟');
      expect(formatMinutes(59), '59 分钟');
    });

    test('shows hours and minutes', () {
      expect(formatMinutes(60), '1 小时 0 分钟');
      expect(formatMinutes(95), '1 小时 35 分钟');
    });

    test('clamps negative values to zero', () {
      expect(formatMinutes(-10), '0 分钟');
    });
  });

  group('formatMinutes (en)', () {
    test('english formatting', () {
      S.overrideLocale.value = const Locale('en');
      expect(formatMinutes(25), '25 min');
      expect(formatMinutes(95), '1h 35m');
      S.overrideLocale.value = const Locale('zh');
    });
  });

  group('S.tr', () {
    test('zh strings', () {
      S.overrideLocale.value = const Locale('zh');
      expect(S.tr('start_focus'), '开始专注');
    });

    test('en strings', () {
      S.overrideLocale.value = const Locale('en');
      expect(S.tr('start_focus'), 'Start Focus');
    });

    test('unknown key falls back to zh then key itself', () {
      S.overrideLocale.value = const Locale('en');
      expect(S.tr('__nonexistent__'), '__nonexistent__');
      S.overrideLocale.value = const Locale('zh');
    });
  });
}
