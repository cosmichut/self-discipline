import 'package:flutter/material.dart';
import '../l10n/strings.dart';
import '../services/native_service.dart';
import '../widgets/permission_dialogs.dart';

/// 首次启动权限引导。
///
/// 核心权限：
/// - 无障碍服务（必需）：锁定期间阻止切换应用
/// - 通知权限（行走检测需要）：前台服务通知
/// - 电话权限（推荐）：来电豁免
/// - 设备管理员（推荐）：卸载保护
/// - 忽略电池优化（推荐）：保证行走检测不被系统杀掉
class OnboardingScreen extends StatefulWidget {
  final VoidCallback onComplete;

  const OnboardingScreen({super.key, required this.onComplete});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen>
    with WidgetsBindingObserver {
  Map<String, bool> _status = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final status = await NativeService.getPermissionStatus();
    if (mounted) setState(() => _status = status);
  }

  bool get _coreGranted => _status['accessibility'] == true;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(S.tr('perm_setup'))),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Text(
              S.tr('onboarding_intro'),
              style: const TextStyle(fontSize: 15, color: Colors.grey),
            ),
          ),
          _buildItem(
            icon: Icons.lock,
            title: S.tr('perm_accessibility'),
            subtitle: S.tr('perm_accessibility_sub'),
            granted: _status['accessibility'] == true,
            required: true,
            onTap: () => showAccessibilityGuideDialog(context),
            actionLabel: S.tr('enable'),
          ),
          _buildItem(
            icon: Icons.notifications,
            title: S.tr('perm_notification'),
            subtitle: S.tr('perm_notification_sub'),
            granted: _status['notification'] == true,
            onTap: () => NativeService.requestNotificationPermission(),
            actionLabel: S.tr('grant'),
          ),
          _buildItem(
            icon: Icons.phone,
            title: S.tr('perm_phone'),
            subtitle: S.tr('perm_phone_sub'),
            granted: _status['phone'] == true,
            onTap: () => NativeService.requestPhonePermission(),
            actionLabel: S.tr('grant'),
          ),
          _buildItem(
            icon: Icons.admin_panel_settings,
            title: S.tr('perm_admin'),
            subtitle: S.tr('perm_admin_sub'),
            granted: _status['device_admin'] == true,
            onTap: () => NativeService.requestDeviceAdmin(),
            actionLabel: S.tr('activate'),
          ),
          _buildItem(
            icon: Icons.battery_saver,
            title: S.tr('perm_battery'),
            subtitle: S.tr('perm_battery_sub'),
            granted: _status['battery_ignored'] == true,
            onTap: () => NativeService.requestIgnoreBatteryOptimizations(),
            actionLabel: S.tr('setup_action'),
          ),
          const SizedBox(height: 32),
          ElevatedButton(
            onPressed: _coreGranted ? widget.onComplete : null,
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 16),
            ),
            child: Text(_coreGranted
                ? S.tr('start_using')
                : S.tr('enable_accessibility_first')),
          ),
        ],
      ),
    );
  }

  Widget _buildItem({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool granted,
    required VoidCallback onTap,
    required String actionLabel,
    bool required = false,
  }) {
    return Card(
      child: ListTile(
        leading: Icon(icon, color: granted ? Colors.green : Colors.grey),
        title: Row(
          children: [
            Flexible(child: Text(title)),
            if (required)
              Container(
                margin: const EdgeInsets.only(left: 8),
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(S.tr('required_tag'),
                    style: const TextStyle(fontSize: 11, color: Colors.red)),
              ),
          ],
        ),
        subtitle: Text(subtitle, style: const TextStyle(fontSize: 12)),
        trailing: granted
            ? const Icon(Icons.check_circle, color: Colors.green)
            : TextButton(onPressed: onTap, child: Text(actionLabel)),
        onTap: granted ? null : onTap,
      ),
    );
  }
}
