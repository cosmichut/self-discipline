import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'l10n/strings.dart';
import 'screens/home_screen.dart';
import 'screens/onboarding_screen.dart';
import 'services/native_service.dart';
import 'utils/error_log.dart';

void main() {
  runZonedGuarded(() {
    WidgetsFlutterBinding.ensureInitialized();
    // 全局异常捕获：显示在设置页「诊断信息」，免 adb 排查真机问题
    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      ErrorLog.record(details.exceptionAsString());
    };
    // 恢复语言覆盖设置（null = 跟随系统），并同步到原生层
    SharedPreferences.getInstance().then((prefs) {
      final saved = prefs.getString('locale_override');
      if (saved == 'zh' || saved == 'en') {
        S.overrideLocale.value = Locale(saved!);
      }
      NativeService.setLocaleOverride(saved);
      runApp(SelfDisciplineApp());
    });
  }, (error, stack) {
    ErrorLog.record('$error');
  });
}

class SelfDisciplineApp extends StatelessWidget {
  const SelfDisciplineApp({super.key});

  @override
  Widget build(BuildContext context) {
    // 语言切换时重建整棵 UI 树
    return ValueListenableBuilder<Locale?>(
      valueListenable: S.overrideLocale,
      builder: (context, locale, _) {
        return MaterialApp(
          title: S.tr('app_title'),
          locale: locale,
          supportedLocales: const [Locale('zh'), Locale('en')],
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          theme: ThemeData(
            colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
            useMaterial3: true,
          ),
          home: const _EntryGate(),
          debugShowCheckedModeBanner: false,
        );
      },
    );
  }
}

/// 入口路由：首次使用先进入权限引导页。
class _EntryGate extends StatefulWidget {
  const _EntryGate();

  @override
  State<_EntryGate> createState() => _EntryGateState();
}

class _EntryGateState extends State<_EntryGate> {
  bool? _onboarded;

  @override
  void initState() {
    super.initState();
    SharedPreferences.getInstance().then((prefs) {
      setState(() => _onboarded = prefs.getBool('onboarded') ?? false);
    });
  }

  Future<void> _completeOnboarding() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('onboarded', true);
    // 行走检测默认开启（用户之后可在首页/设置中随时关闭，显式选择会被保留）
    await NativeService.setWalkingEnabled(true);
    setState(() => _onboarded = true);
  }

  @override
  Widget build(BuildContext context) {
    if (_onboarded == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (!_onboarded!) {
      return OnboardingScreen(onComplete: _completeOnboarding);
    }
    return const HomeScreen();
  }
}
