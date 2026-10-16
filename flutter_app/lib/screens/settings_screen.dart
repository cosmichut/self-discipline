import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../l10n/strings.dart';
import '../services/native_service.dart';
import '../utils/error_log.dart';
import '../widgets/permission_dialogs.dart';
import 'app_limits_screen.dart';

/// 设置页：灵敏度、语言、权限管理、卸载引导。
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  Map<String, bool> _permStatus = {};
  double _threshold = 2.0;
  String _appVersion = '';
  bool _walkingEnabled = false;

  // 灵敏度档位：值越小越灵敏。(短标签 key, 描述 key, 阈值)
  static const _sensitivityOptions = [
    ('sens_high', 'sens_high_desc', 1.2),
    ('sens_mid', 'sens_mid_desc', 2.0),
    ('sens_low', 'sens_low_desc', 3.0),
  ];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final status = await NativeService.getPermissionStatus();
    final threshold = await NativeService.getWalkingThreshold();
    final version = await NativeService.getAppVersion();
    final walking = await NativeService.isWalkingEnabled();
    if (mounted) {
      setState(() {
        _permStatus = status;
        _threshold = threshold;
        _appVersion = version;
        _walkingEnabled = walking;
      });
    }
  }

  /// 行走检测开关（与首页卡片共用原生状态）；关闭时灵敏度选项禁用
  Future<void> _toggleWalking(bool value) async {
    if (value && _permStatus['notification'] != true) {
      await NativeService.requestNotificationPermission();
      await _load();
      if (_permStatus['notification'] != true) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(S.tr('need_notification'))),
          );
        }
        return;
      }
    }
    await NativeService.setWalkingEnabled(value);
    setState(() => _walkingEnabled = value);
  }

  String get _sensitivityLabel {
    for (final opt in _sensitivityOptions) {
      if ((opt.$3 - _threshold).abs() < 0.01) return S.tr(opt.$2);
    }
    return S.tr(_sensitivityOptions[1].$2);
  }

  Future<void> _setLocale(String? code) async {
    final prefs = await SharedPreferences.getInstance();
    if (code == null) {
      await prefs.remove('locale_override');
      S.overrideLocale.value = null;
    } else {
      await prefs.setString('locale_override', code);
      S.overrideLocale.value = Locale(code);
    }
    // 同步到原生层：锁定界面/悬浮层/通知文案跟随 App 内语言
    await NativeService.setLocaleOverride(code);
  }

  String? get _currentLocaleCode {
    final v = S.overrideLocale.value;
    return v?.languageCode; // null = 跟随系统
  }

  /// 整行可点的灵敏度选择（底部弹窗）
  Future<void> _showSensitivityPicker() async {
    debugPrint('SD: sensitivity picker tapped');
    final selected = await showModalBottomSheet<double>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(S.tr('select_sensitivity'),
                  style: Theme.of(ctx).textTheme.titleMedium),
            ),
            ..._sensitivityOptions.map((opt) {
              final isSelected = (opt.$3 - _threshold).abs() < 0.01;
              return ListTile(
                title: Text(S.tr(opt.$2)),
                trailing: isSelected
                    ? const Icon(Icons.check, color: Colors.blue)
                    : null,
                onTap: () => Navigator.pop(ctx, opt.$3),
              );
            }),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (selected != null) {
      await NativeService.setWalkingThreshold(selected);
      setState(() => _threshold = selected);
    }
  }

  /// 整行可点的语言选择（底部弹窗，'system' 哨兵值代表跟随系统）
  Future<void> _showLanguagePicker() async {
    debugPrint('SD: language picker tapped');
    const options = <(String, String)>[
      ('system', 'system'),
      ('中文', 'zh'),
      ('English', 'en'),
    ];
    final selected = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(S.tr('select_language'),
                  style: Theme.of(ctx).textTheme.titleMedium),
            ),
            ...options.map((opt) {
              final current = _currentLocaleCode ?? 'system';
              final isSelected = opt.$2 == current;
              final label = opt.$2 == 'system' ? S.tr('lang_system') : opt.$1;
              return ListTile(
                title: Text(label),
                trailing: isSelected
                    ? const Icon(Icons.check, color: Colors.blue)
                    : null,
                onTap: () => Navigator.pop(ctx, opt.$2),
              );
            }),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (selected != null) {
      await _setLocale(selected == 'system' ? null : selected);
    }
  }

  String get _currentLanguageLabel {
    switch (_currentLocaleCode) {
      case 'zh':
        return '中文';
      case 'en':
        return 'English';
      default:
        return S.tr('lang_system');
    }
  }

  Future<void> _showAboutDialog() async {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(S.tr('app_title')),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (_appVersion.isNotEmpty) Text('v$_appVersion'),
            const SizedBox(height: 12),
            Text(S.tr('about_open_source')),
            const SizedBox(height: 4),
            Text(S.tr('about_no_network')),
            const SizedBox(height: 12),
            Text('${S.tr('about_repo_label')}：',
                style: const TextStyle(color: Colors.grey, fontSize: 12)),
            const SelectableText(
              'github.com/cosmichut/self-discipline',
              style: TextStyle(fontSize: 13, color: Colors.blue),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(S.tr('got_it')),
          ),
        ],
      ),
    );
  }

  Future<void> _showUninstallGuide() async {
    final hasAdmin = _permStatus['device_admin'] == true;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(S.tr('uninstall_title')),
        content: Text(S.tr(
            hasAdmin ? 'uninstall_body_admin' : 'uninstall_body_plain')),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(S.tr('cancel')),
          ),
          if (hasAdmin)
            TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text(S.tr('deactivate_admin'),
                  style: const TextStyle(color: Colors.red)),
            ),
        ],
      ),
    );

    if (confirmed == true) {
      await NativeService.removeDeviceAdmin();
      await _load();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(S.tr('admin_deactivated'))),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(S.tr('settings'))),
      body: ListView(
        children: [
          _SectionHeader(S.tr('walking_detection')),
          SwitchListTile(
            secondary: const Icon(Icons.directions_walk),
            title: Text(S.tr('walking_detection')),
            subtitle: Text(S.tr('walking_detection_sub')),
            value: _walkingEnabled,
            onChanged: _toggleWalking,
          ),
          ListTile(
            leading: const Icon(Icons.tune),
            title: Text(S.tr('sensitivity')),
            subtitle: Text(_sensitivityLabel),
            trailing: const Icon(Icons.chevron_right),
            enabled: _walkingEnabled,
            onTap: _walkingEnabled ? _showSensitivityPicker : null,
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.app_blocking),
            title: Text(S.tr('app_limits')),
            subtitle: Text(S.tr('app_limits_sub')),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const AppLimitsScreen()),
            ),
          ),
          const Divider(),
          _SectionHeader(S.tr('perm_management')),
          _buildPermTile(
            icon: Icons.lock,
            title: S.tr('perm_accessibility'),
            subtitle: S.tr('core_perm_sub'),
            granted: _permStatus['accessibility'] == true,
            onTap: () async {
              await showAccessibilityGuideDialog(context);
              // 从系统设置返回后刷新
              Future.delayed(const Duration(seconds: 1), _load);
            },
          ),
          _buildPermTile(
            icon: Icons.notifications,
            title: S.tr('perm_notification'),
            granted: _permStatus['notification'] == true,
            onTap: () async {
              await NativeService.requestNotificationPermission();
              _load();
            },
          ),
          _buildPermTile(
            icon: Icons.phone,
            title: S.tr('perm_phone'),
            subtitle: S.tr('phone_perm_sub'),
            granted: _permStatus['phone'] == true,
            onTap: () async {
              await NativeService.requestPhonePermission();
              _load();
            },
          ),
          _buildPermTile(
            icon: Icons.admin_panel_settings,
            title: S.tr('perm_admin'),
            subtitle: S.tr('admin_perm_sub'),
            granted: _permStatus['device_admin'] == true,
            onTap: () async {
              await NativeService.requestDeviceAdmin();
              _load();
            },
          ),
          _buildPermTile(
            icon: Icons.battery_saver,
            title: S.tr('perm_battery'),
            subtitle: S.tr('battery_perm_sub'),
            granted: _permStatus['battery_ignored'] == true,
            onTap: () async {
              await NativeService.requestIgnoreBatteryOptimizations();
              _load();
            },
          ),
          ListTile(
            leading: const Icon(Icons.rocket_launch),
            title: Text(S.tr('background_protection')),
            subtitle: Text(S.tr('background_protection_sub')),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => NativeService.openBackgroundSettings(),
          ),
          const Divider(),
          _SectionHeader(S.tr('others')),
          ListTile(
            leading: const Icon(Icons.language),
            title: Text(S.tr('language')),
            subtitle: Text(_currentLanguageLabel),
            trailing: const Icon(Icons.chevron_right),
            onTap: _showLanguagePicker,
          ),
          ListTile(
            leading: const Icon(Icons.delete_outline, color: Colors.red),
            title: Text(S.tr('uninstall'),
                style: const TextStyle(color: Colors.red)),
            subtitle: Text(S.tr('uninstall_sub')),
            onTap: _showUninstallGuide,
          ),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: Text(S.tr('about')),
            subtitle: Text('${_appVersion.isEmpty ? '' : 'v$_appVersion · '}${S.tr('about_sub')}'),
            onTap: _showAboutDialog,
          ),
          ValueListenableBuilder<String>(
            valueListenable: ErrorLog.last,
            builder: (context, err, _) {
              if (err.isEmpty) return const SizedBox.shrink();
              return ListTile(
                leading: const Icon(Icons.bug_report, color: Colors.red),
                title: const Text('诊断信息',
                    style: TextStyle(color: Colors.red)),
                subtitle: Text(err,
                    maxLines: 6, overflow: TextOverflow.ellipsis),
                onTap: () {
                  showDialog(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: const Text('诊断信息'),
                      content: SingleChildScrollView(
                        child: SelectableText(err),
                      ),
                      actions: [
                        TextButton(
                          onPressed: () {
                            ErrorLog.clear();
                            Navigator.pop(ctx);
                          },
                          child: Text(S.tr('got_it')),
                        ),
                      ],
                    ),
                  );
                },
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildPermTile({
    required IconData icon,
    required String title,
    String? subtitle,
    required bool granted,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Icon(icon, color: granted ? Colors.green : Colors.grey),
      title: Text(title),
      subtitle: subtitle != null ? Text(subtitle) : null,
      trailing: granted
          ? const Icon(Icons.check_circle, color: Colors.green)
          : TextButton(onPressed: onTap, child: Text(S.tr('enable'))),
      onTap: granted ? null : onTap,
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 13,
          color: Theme.of(context).colorScheme.primary,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}
