import 'package:flutter/material.dart';

import '../l10n/strings.dart';
import '../services/native_service.dart';
import '../utils/format.dart';

class StatsScreen extends StatefulWidget {
  const StatsScreen({super.key});

  @override
  State<StatsScreen> createState() => _StatsScreenState();
}

class _StatsScreenState extends State<StatsScreen> {
  Map<String, int> _stats = {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final stats = await NativeService.getStats();
    if (mounted) setState(() => _stats = stats);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(S.tr('stats_title'))),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _buildStatCard(
              icon: Icons.timer,
              title: S.tr('total_focus'),
              value: formatMinutes(_stats['total_minutes'] ?? 0),
              color: Colors.blue,
            ),
            _buildStatCard(
              icon: Icons.local_fire_department,
              title: S.tr('consecutive_days'),
              value: '${_stats['consecutive_days'] ?? 0} ${S.tr('days_suffix')}',
              color: Colors.orange,
            ),
            Row(
              children: [
                Expanded(
                  child: _buildStatCard(
                    icon: Icons.check_circle,
                    title: S.tr('success_count'),
                    value: '${_stats['success_count'] ?? 0}',
                    color: Colors.green,
                  ),
                ),
                Expanded(
                  child: _buildStatCard(
                    icon: Icons.cancel,
                    title: S.tr('fail_count'),
                    value: '${_stats['fail_count'] ?? 0}',
                    color: Colors.red,
                  ),
                ),
              ],
            ),
            _buildStatCard(
              icon: Icons.directions_walk,
              title: S.tr('walking_interventions'),
              value: '${_stats['walking_interventions'] ?? 0} ${S.tr('times_suffix')}',
              color: Colors.teal,
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                S.tr('stats_footer'),
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.grey, fontSize: 12),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatCard({
    required IconData icon,
    required String title,
    required String value,
    required Color color,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          children: [
            Icon(icon, size: 40, color: color),
            const SizedBox(height: 8),
            Text(title,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 13, color: Colors.grey)),
            const SizedBox(height: 4),
            Text(
              value,
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
