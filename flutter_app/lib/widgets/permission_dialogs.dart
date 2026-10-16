import 'package:flutter/material.dart';

import '../l10n/strings.dart';
import '../services/native_service.dart';

/// 无障碍服务开启引导对话框。
///
/// 侧载安装的应用在荣耀 MagicOS、小米 MIUI、原生 Android 13+ 上
/// 会被系统标记为「受限应用」，禁止直接开启无障碍服务，
/// 需要用户先在应用管理中「允许受限设置/解除限制」。
Future<void> showAccessibilityGuideDialog(BuildContext context) {
  debugPrint('SD: accessibility guide tapped');
  return showDialog(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(S.tr('acc_guide_title')),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              S.tr('acc_guide_step1'),
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 4),
            Text(S.tr('acc_guide_step1_body')),
            const SizedBox(height: 16),
            Text(
              S.tr('acc_guide_step2'),
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 4),
            Text(
              S.tr('acc_guide_step2_body'),
              style: const TextStyle(fontSize: 13, height: 1.5),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: Text(S.tr('cancel')),
        ),
        FilledButton(
          onPressed: () {
            Navigator.pop(ctx);
            NativeService.openAccessibilitySettings();
          },
          child: Text(S.tr('enable')),
        ),
      ],
    ),
  );
}
