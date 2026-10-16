import 'package:flutter/material.dart';

import '../l10n/strings.dart';
import '../services/native_service.dart';
import '../widgets/hold_to_confirm.dart';

/// 应用限额管理页：每日使用时长上限，超过后自动锁定。
class AppLimitsScreen extends StatefulWidget {
  const AppLimitsScreen({super.key});

  @override
  State<AppLimitsScreen> createState() => _AppLimitsScreenState();
}

class _AppLimitsScreenState extends State<AppLimitsScreen> {
  Map<String, int> _limits = {};
  Map<String, int> _usage = {};
  List<Map<String, String>> _candidates = [];

  static const _limitOptions = [10, 15, 30, 45, 60, 90, 120];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final (limits, usage) = await NativeService.getAppLimits();
    if (_candidates.isEmpty) {
      _candidates = await NativeService.getInstalledCandidates();
    }
    if (mounted) {
      setState(() {
        _limits = limits;
        _usage = usage;
      });
    }
  }

  String _appLabel(String pkg) {
    for (final c in _candidates) {
      if (c['package'] == pkg) return c['name'] ?? pkg;
    }
    final parts = pkg.split('.');
    return parts.isNotEmpty ? parts.last : pkg;
  }

  Future<void> _addLimit() async {
    final available = _candidates
        .where((c) => !_limits.containsKey(c['package']))
        .toList();
    if (available.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(S.tr('no_apps_found'))),
      );
      return;
    }

    final pkg = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,   // 关键：约束 sheet 不越过状态栏（路由默认移除顶部安全区）
      builder: (ctx) {
        var query = '';
        return StatefulBuilder(
          builder: (ctx, setSheetState) {
            final filtered = query.isEmpty
                ? available
                : available
                    .where((c) => (c['name'] ?? '')
                        .toLowerCase()
                        .contains(query.toLowerCase()))
                    .toList();
            return Padding(
              padding: EdgeInsets.only(
                bottom: MediaQuery.of(ctx).viewInsets.bottom,
              ),
              child: SizedBox(
                height: MediaQuery.of(ctx).size.height * 0.7,
                child: Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: TextField(
                        autofocus: true,
                        decoration: InputDecoration(
                          hintText: S.tr('search_apps'),
                          prefixIcon: const Icon(Icons.search),
                          border: const OutlineInputBorder(),
                          isDense: true,
                        ),
                        onChanged: (v) => setSheetState(() => query = v),
                      ),
                    ),
                    Expanded(
                      child: ListView(
                        children: filtered
                            .map((c) => ListTile(
                                  title: Text(c['name'] ?? c['package'] ?? ''),
                                  subtitle: Text(c['package'] ?? '',
                                      style: const TextStyle(
                                          fontSize: 11, color: Colors.grey)),
                                  onTap: () => Navigator.pop(ctx, c['package']),
                                ))
                            .toList(),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
    if (pkg == null || !mounted) return;

    final minutes = await showModalBottomSheet<int>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(S.tr('choose_limit'),
                  style: Theme.of(ctx).textTheme.titleMedium),
            ),
            ..._limitOptions.map((m) => ListTile(
                  title: Text('$m ${S.tr('minutes_suffix')}'),
                  onTap: () => Navigator.pop(ctx, m),
                )),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (minutes == null) return;

    await NativeService.setAppLimit(pkg, minutes);
    await _load();
  }

  /// 取消限额：提示后果 + 长按 2 分钟确认（摩擦不对称：取消要费劲）
  Future<void> _removeLimit(String pkg) async {
    final appName = _appLabel(pkg);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(S.tr('remove_limit_title')),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(S.trf('remove_limit_body_fmt', {'app': appName})),
            const SizedBox(height: 20),
            HoldToConfirmButton(
              duration: const Duration(minutes: 2),
              label: S.tr('hold_confirm_remove'),
              onConfirmed: () => Navigator.pop(ctx, true),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(S.tr('cancel')),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await NativeService.removeAppLimit(pkg);
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(S.tr('app_limits'))),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Text(
              S.tr('app_limits_sub'),
              style: const TextStyle(fontSize: 13, color: Colors.grey),
            ),
          ),
          if (_limits.isEmpty)
            Padding(
              padding: const EdgeInsets.all(32),
              child: Center(
                child: Text(
                  S.tr('app_limits_empty'),
                  style: const TextStyle(color: Colors.grey),
                ),
              ),
            ),
          ..._limits.entries.map((e) {
            final usedMin = (_usage[e.key] ?? 0) ~/ 60;
            return Card(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: ListTile(
                leading: const Icon(Icons.app_blocking),
                title: Text(_appLabel(e.key)),
                subtitle: Text(
                  '${S.trf('limit_per_day_fmt', {'m': e.value})} · '
                  '${S.trf('today_used_fmt', {'m': usedMin})}',
                ),
                trailing: TextButton(
                  onPressed: () => _removeLimit(e.key),
                  child: Text(S.tr('remove_limit'),
                      style: const TextStyle(color: Colors.red)),
                ),
              ),
            );
          }),
          Card(
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: ListTile(
              leading: const Icon(Icons.add_circle_outline),
              title: Text(S.tr('add_limit_app')),
              trailing: const Icon(Icons.chevron_right),
              onTap: _addLimit,
            ),
          ),
        ],
      ),
    );
  }
}
