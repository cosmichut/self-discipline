import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../l10n/strings.dart';
import '../services/native_service.dart';
import '../utils/format.dart';
import '../widgets/permission_dialogs.dart';
import 'app_limits_screen.dart';
import 'settings_screen.dart';
import 'stats_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  bool _walkingEnabled = false;
  int _lockDuration = 25;
  String _lockType = 'none';
  int _remainingSeconds = 0;

  Map<String, bool> _permStatus = {};
  StreamSubscription? _lockStateSub;
  Timer? _ticker;
  String _appVersion = '';
  String _walkingMode = 'variance';

  static const _durations = [15, 25, 45, 60, 90, 120];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _init();
  }

  Future<void> _init() async {
    final prefs = await SharedPreferences.getInstance();
    final walking = await NativeService.isWalkingEnabled();
    final state = await NativeService.getLockState();

    setState(() {
      _lockDuration = prefs.getInt('lock_duration') ?? 25;
      _walkingEnabled = walking;
      _applyLockState(state);
    });

    _lockStateSub = NativeService.lockStateStream.listen(_applyLockState);
    _refreshPermissions();
    NativeService.getAppVersion().then((v) {
      if (mounted) setState(() => _appVersion = v);
    });
    NativeService.getWalkingMode().then((m) {
      if (mounted) setState(() => _walkingMode = m);
    });
  }

  void _applyLockState(Map<String, dynamic> state) {
    if (!mounted) return;
    setState(() {
      _lockType = state['type'] as String? ?? 'none';
      _remainingSeconds =
          ((state['remaining_millis'] as num? ?? 0) / 1000).ceil();
    });
    _ticker?.cancel();
    if (_lockType == 'manual') {
      var ticks = 0;
      _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
        if (!mounted) return;
        if (_remainingSeconds > 0) {
          setState(() => _remainingSeconds--);
        }
        // 本地递减可能与原生真实值漂移（后台挂起等），每 10 秒与原生对表一次
        if (++ticks % 10 == 0) {
          NativeService.getLockState().then(_applyLockState);
        }
      });
    }
  }

  Future<void> _refreshPermissions() async {
    final status = await NativeService.getPermissionStatus();
    final mode = await NativeService.getWalkingMode();
    if (mounted) {
      setState(() {
        _permStatus = status;
        _walkingMode = mode;
      });
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshPermissions();
      NativeService.getLockState().then(_applyLockState);
    }
  }

  Future<void> _startLock() async {
    if (_permStatus['accessibility'] != true) {
      showAccessibilityGuideDialog(context);
      return;
    }
    await NativeService.startLock(_lockDuration);
  }

  Future<void> _toggleWalking(bool value) async {
    if (value && _permStatus['notification'] != true) {
      await NativeService.requestNotificationPermission();
      await _refreshPermissions();
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

  @override
  Widget build(BuildContext context) {
    final isLocked = _lockType != 'none';

    return Scaffold(
      appBar: AppBar(
        title: Text(S.tr('app_title')),
        actions: [
          IconButton(
            icon: const Icon(Icons.bar_chart),
            tooltip: S.tr('stats'),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const StatsScreen()),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            tooltip: S.tr('settings'),
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const SettingsScreen()),
              );
              _refreshPermissions();
            },
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (_permStatus['accessibility'] != true)
              Card(
                color: Colors.orange.shade50,
                child: ListTile(
                  leading: const Icon(Icons.warning, color: Colors.orange),
                  title: Text(S.tr('acc_off_title')),
                  subtitle: Text(S.tr('acc_off_subtitle')),
                  onTap: () => showAccessibilityGuideDialog(context),
                ),
              ),
            _buildWalkingCard(),
            Card(
              child: ListTile(
                leading: const Icon(Icons.app_blocking, color: Colors.deepPurple),
                title: Text(S.tr('app_limits')),
                subtitle: Text(S.tr('app_limits_sub')),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const AppLimitsScreen()),
                ),
              ),
            ),
            const Spacer(),
            Icon(
              isLocked ? Icons.lock : Icons.lock_open,
              size: 72,
              color: isLocked ? Colors.blue : Colors.grey,
            ),
            const SizedBox(height: 16),
            if (isLocked) ...[
              Text(
                _lockType == 'manual'
                    ? S.tr('focusing')
                    : S.tr('walking_locked'),
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              if (_lockType == 'manual') ...[
                const SizedBox(height: 8),
                Text(
                  formatSeconds(_remainingSeconds),
                  style: Theme.of(context).textTheme.displayMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: Colors.blue,
                      ),
                ),
              ],
              const SizedBox(height: 8),
              Text(S.tr('lock_overlay_hint'),
                  style: const TextStyle(color: Colors.grey, fontSize: 12)),
            ] else ...[
              Text(S.tr('prepare_focus'),
                  style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 24),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.center,
                children: _durations.map((min) {
                  return ChoiceChip(
                    label: Text('$min ${S.tr('minutes_suffix')}'),
                    selected: _lockDuration == min,
                    onSelected: (_) async {
                      final prefs = await SharedPreferences.getInstance();
                      await prefs.setInt('lock_duration', min);
                      setState(() => _lockDuration = min);
                    },
                  );
                }).toList(),
              ),
              const SizedBox(height: 32),
              ElevatedButton.icon(
                onPressed: _startLock,
                icon: const Icon(Icons.play_arrow),
                label: Text(S.tr('start_focus')),
                style: ElevatedButton.styleFrom(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 48, vertical: 16),
                  textStyle: const TextStyle(fontSize: 18),
                ),
              ),
              const SizedBox(height: 12),
              Text(
                S.tr('emergency_hint'),
                style: const TextStyle(fontSize: 11, color: Colors.grey),
              ),
            ],
            const Spacer(),
            if (_appVersion.isNotEmpty)
              Text(
                'v$_appVersion',
                style: const TextStyle(fontSize: 11, color: Colors.grey),
              ),
          ],
        ),
      ),
    );
  }

  /// 行走检测大卡片（主打功能，置顶展示）。
  ///
  /// 防误关设计：开启状态下不显示开关，仅显示状态 + 小字提示"关闭请前往设置"，
  /// 且卡片不可点击——关闭入口只在设置页，故意让放弃路径变长（冲动冷却）。
  /// 服务异常时卡片可点击（一键重启，属于故障处理而非放弃路径）。
  Widget _buildWalkingCard() {
    final enabled = _walkingEnabled;
    // 开关开着但服务心跳超时 → 服务被系统杀了
    final serviceDead = enabled && _permStatus['walking_service_running'] == false;

    final Color color = !enabled
        ? Colors.grey
        : serviceDead
            ? Colors.orange
            : Colors.teal;

    final String status = !enabled
        ? S.tr('walking_off')
        : serviceDead
            ? S.tr('walking_dead')
            : '${S.tr('walking_running')}'
                '（${_walkingMode == 'step' ? S.tr('walking_mode_step') : S.tr('walking_mode_variance')}）';

    return Card(
      elevation: 3,
      color: !enabled
          ? null
          : serviceDead
              ? Colors.orange.shade50
              : Colors.teal.shade50,
      child: InkWell(
        onTap: serviceDead ? _restartWalkingService : null,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: !enabled ? Colors.grey.shade300 : color,
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.directions_walk,
                    size: 32, color: Colors.white),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(S.tr('walking_detection'),
                        style: const TextStyle(
                            fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 2),
                    Text(
                      status,
                      style: TextStyle(
                        fontSize: 12,
                        color: !enabled ? Colors.grey : color,
                      ),
                    ),
                    if (enabled && !serviceDead)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          S.tr('walking_close_hint'),
                          style: TextStyle(
                            fontSize: 10,
                            color: Colors.grey.shade500,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              if (!enabled)
                Transform.scale(
                  scale: 1.15,
                  child: Switch(
                    value: false,
                    onChanged: _toggleWalking,
                    activeColor: Colors.teal,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _restartWalkingService() async {
    await NativeService.setWalkingEnabled(true);
    await _refreshPermissions();
    if (!mounted) return;
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(S.tr('service_restarted')),
        content: Text(S.tr('service_restart_body')),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(S.tr('got_it')),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(ctx);
              NativeService.openBackgroundSettings();
            },
            child: Text(S.tr('go_settings')),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _lockStateSub?.cancel();
    _ticker?.cancel();
    super.dispose();
  }
}
