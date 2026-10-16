import 'package:flutter/widgets.dart';

/// 轻量国际化：中文（默认）+ English。
///
/// 默认跟随系统语言，设置页可手动覆盖（持久化到 SharedPreferences）。
/// 使用：S.tr('key')；含占位符用 S.trf('fmt_hours_minutes', {'h': 1, 'm': 30})。
class S {
  S._();

  /// null = 跟随系统
  static final ValueNotifier<Locale?> overrideLocale = ValueNotifier(null);

  static bool get isZh {
    final locale =
        overrideLocale.value ?? WidgetsBinding.instance.platformDispatcher.locale;
    return locale.languageCode.toLowerCase().startsWith('zh');
  }

  static String tr(String key) {
    final map = isZh ? _zh : _en;
    return map[key] ?? _zh[key] ?? key;
  }

  static String trf(String key, Map<String, Object> params) {
    var text = tr(key);
    params.forEach((k, v) {
      text = text.replaceAll('{$k}', '$v');
    });
    return text;
  }

  static const Map<String, String> _zh = {
    'app_title': '自律助手',
    // ---- 主页 ----
    'stats': '统计',
    'settings': '设置',
    'acc_off_title': '无障碍服务未开启',
    'acc_off_subtitle': '锁定功能不可用，点击去开启',
    'walking_detection': '行走检测',
    'walking_running': '运行中 · 走路时自动锁屏，来电可接听',
    'walking_off': '已关闭 · 走路看手机不会被拦截',
    'walking_dead': '服务未运行 · 可能被系统限制，点击处理',
    'walking_close_hint': '关闭请前往「设置」',
    'walking_detection_sub': '走路时自动锁屏，来电可接听',
    'walking_mode_step': '步态模式',
    'walking_mode_variance': '运动模式',
    'prepare_focus': '准备专注',
    'focusing': '专注中',
    'walking_locked': '行走锁定中',
    'lock_overlay_hint': '锁定界面全屏显示中，此页面仅作状态展示',
    'minutes_suffix': '分钟',
    'start_focus': '开始专注',
    'emergency_hint': '紧急退出：长按专注界面底部中间位置 20 秒',
    'service_restarted': '服务已重启',
    'service_restart_body': '如果行走检测经常被系统停止，请进行以下设置：\n\n'
        '1. 开启「忽略电池优化」（设置页）\n'
        '2. 允许后台自启动（点击下方按钮跳转）\n\n'
        '荣耀：手机管家 → 应用启动管理 → 自律助手 → 允许自启动和后台活动',
    'got_it': '知道了',
    'go_settings': '去设置',
    'need_notification': '行走检测需要通知权限来运行前台服务',
    // ---- 权限引导 ----
    'perm_setup': '权限设置',
    'onboarding_intro': '自律助手需要以下权限才能正常工作。\n'
        '本应用为开源软件，代码公开可审计；未申请网络权限，数据不可能离开本机。\n'
        '完成后将默认开启行走检测（走路自动锁屏，可随时关闭）。',
    'perm_accessibility': '无障碍服务',
    'perm_accessibility_sub': '必需 · 锁定期间阻止切换到其他应用（不读取屏幕内容）',
    'required_tag': '必需',
    'perm_notification': '通知权限',
    'perm_notification_sub': '行走检测需要 · 显示前台服务运行状态',
    'perm_phone': '电话权限',
    'perm_phone_sub': '推荐 · 来电时自动解除行走锁定，允许接听',
    'perm_admin': '设备管理员',
    'perm_admin_sub': '推荐 · 防止锁定期间通过卸载应用绕过（可随时停用）',
    'perm_battery': '忽略电池优化',
    'perm_battery_sub': '推荐 · 防止系统杀掉行走检测服务',
    'grant': '授权',
    'activate': '激活',
    'enable': '去开启',
    'setup_action': '设置',
    'start_using': '开始使用',
    'enable_accessibility_first': '请先开启无障碍服务',
    // ---- 设置 ----
    'sensitivity': '检测灵敏度',
    'sens_high': '高',
    'sens_high_desc': '高（轻微晃动即判定行走）',
    'sens_mid': '中',
    'sens_mid_desc': '中',
    'sens_low': '低',
    'sens_low_desc': '低（默认，仅明显行走才锁定）',
    'perm_management': '权限管理',
    'core_perm_sub': '锁定功能核心权限',
    'phone_perm_sub': '来电豁免',
    'admin_perm_sub': '卸载保护',
    'battery_perm_sub': '保证行走检测持续运行',
    'background_protection': '后台运行保护（自启动）',
    'background_protection_sub': '行走检测频繁停止时设置：跳转厂商自启动管理页',
    'others': '其他',
    'language': '语言 / Language',
    'lang_system': '跟随系统',
    'uninstall': '卸载应用',
    'uninstall_sub': '已激活设备管理员时需先停用',
    'uninstall_title': '卸载应用',
    'uninstall_body_admin': '设备管理员已激活，需先停用才能卸载。\n\n'
        '点击下方"停用设备管理员"，然后在系统设置中卸载应用。',
    'uninstall_body_plain': '当前未激活设备管理员，可直接在系统设置中卸载。',
    'cancel': '取消',
    'deactivate_admin': '停用设备管理员',
    'admin_deactivated': '设备管理员已停用，现在可以正常卸载',
    'about': '关于',
    'about_sub': '自律助手 · 免费 · 无广告 · 数据本地存储',
    'select_language': '选择语言',
    'select_sensitivity': '选择灵敏度',
    'about_open_source': '开源软件 · 代码完全公开可审计',
    'about_no_network': '未申请网络权限，数据不可能离开本机',
    'about_repo_label': '开源地址',
    // ---- 应用限额 ----
    'app_limits': '应用限额',
    'app_limits_sub': '超过每日时长自动锁定，明天重置',
    'add_limit_app': '添加限额应用',
    'choose_app': '选择应用',
    'choose_limit': '每日限额',
    'limit_per_day_fmt': '每日 {m} 分钟',
    'today_used_fmt': '今日已用 {m} 分钟',
    'no_apps_found': '未找到常见应用（微博/抖音/B站等）',
    'delete': '删除',
    'need_accessibility_for_limits': '应用限额需要无障碍服务',
    'app_limits_empty': '还没有限额应用',
    'remove_limit': '取消限额',
    'remove_limit_title': '取消限额？',
    'remove_limit_body_fmt': '取消后「{app}」将不再有每日使用时长限制。',
    'hold_confirm_remove': '长按 2 分钟确认取消',
    'search_apps': '搜索应用',
    // ---- 统计 ----
    'stats_title': '统计数据',
    'total_focus': '累计专注时长',
    'consecutive_days': '连续自律天数',
    'days_suffix': '天',
    'success_count': '成功次数',
    'fail_count': '失败次数',
    'walking_interventions': '行走锁定拦截',
    'times_suffix': '次',
    'stats_footer': '所有数据仅存储在本机，卸载应用后将被清除。',
    // ---- 格式化 ----
    'fmt_hours_minutes': '{h} 小时 {m} 分钟',
    'fmt_minutes': '{m} 分钟',
    // ---- 无障碍引导对话框 ----
    'acc_guide_title': '开启无障碍服务',
    'acc_guide_step1': '第一步：开启服务',
    'acc_guide_step1_body': '点击下方「去开启」，在系统无障碍设置中找到「自律助手」并开启。',
    'acc_guide_step2': '如果无法开启（提示"受限制的设置"等）',
    'acc_guide_step2_body': '非应用市场安装的应用会被系统限制，需先解除：\n\n'
        '· 通用/原生 Android 13+：设置 → 应用 → 自律助手 → 右上角菜单（⋮）→ 允许受限设置\n\n'
        '· 荣耀：设置 → 应用和服务 → 应用管理 → 自律助手 → 解除限制\n\n'
        '· 小米：设置 → 应用设置 → 应用管理 → 自律助手 → 允许受限设置\n\n'
        '解除后重新进入无障碍设置开启即可。\n\n'
        '注意：若开关显示已开启但 App 仍提示未开启，请关闭开关后重新打开（部分系统会杀掉服务但残留开关状态）。',
  };

  static const Map<String, String> _en = {
    'app_title': 'Self-Discipline',
    // ---- Home ----
    'stats': 'Stats',
    'settings': 'Settings',
    'acc_off_title': 'Accessibility service off',
    'acc_off_subtitle': 'Locking unavailable. Tap to enable',
    'walking_detection': 'Walking Detection',
    'walking_running': 'Running · locks screen while walking, calls allowed',
    'walking_off': 'Off · no protection while walking',
    'walking_dead': 'Service not running · possibly killed by system, tap to fix',
    'walking_close_hint': 'To turn off, go to Settings',
    'walking_detection_sub': 'Locks screen while walking; calls allowed',
    'walking_mode_step': 'Gait mode',
    'walking_mode_variance': 'Motion mode',
    'prepare_focus': 'Ready to Focus',
    'focusing': 'Focusing',
    'walking_locked': 'Walking Lock Active',
    'lock_overlay_hint': 'Lock screen is showing in fullscreen; this page is status only',
    'minutes_suffix': 'min',
    'start_focus': 'Start Focus',
    'emergency_hint': 'Emergency exit: long-press bottom center of lock screen for 20s',
    'service_restarted': 'Service Restarted',
    'service_restart_body': 'If walking detection keeps being stopped by the system:\n\n'
        '1. Enable "Ignore battery optimizations" (Settings)\n'
        '2. Allow background auto-start (button below)\n\n'
        'Samsung: Settings → Battery → Background usage limits → '
        'Never sleeping apps → add this app',
    'got_it': 'Got it',
    'go_settings': 'Open Settings',
    'need_notification': 'Walking detection needs notification permission for its foreground service',
    // ---- Onboarding ----
    'perm_setup': 'Permissions',
    'onboarding_intro': 'Self-Discipline needs these permissions to work.\n'
        'This app is open source and auditable; it has NO network permission, so your data cannot leave this device.\n'
        'Walking detection will be ON by default after setup (you can turn it off anytime).',
    'perm_accessibility': 'Accessibility Service',
    'perm_accessibility_sub': 'Required · blocks switching to other apps during lock (never reads screen content)',
    'required_tag': 'Required',
    'perm_notification': 'Notifications',
    'perm_notification_sub': 'Needed by walking detection · shows foreground service status',
    'perm_phone': 'Phone',
    'perm_phone_sub': 'Recommended · incoming calls auto-release walking lock',
    'perm_admin': 'Device Admin',
    'perm_admin_sub': 'Recommended · prevents bypassing the lock by uninstalling (revocable anytime)',
    'perm_battery': 'Ignore Battery Optimizations',
    'perm_battery_sub': 'Recommended · prevents the system from killing walking detection',
    'grant': 'Grant',
    'activate': 'Activate',
    'enable': 'Enable',
    'setup_action': 'Set up',
    'start_using': 'Get Started',
    'enable_accessibility_first': 'Enable accessibility service first',
    // ---- Settings ----
    'sensitivity': 'Detection Sensitivity',
    'sens_high': 'High',
    'sens_high_desc': 'High (locks on slight movement)',
    'sens_mid': 'Medium',
    'sens_mid_desc': 'Medium',
    'sens_low': 'Low',
    'sens_low_desc': 'Low (default, locks only on clear walking)',
    'perm_management': 'Permission Management',
    'core_perm_sub': 'Core permission for locking',
    'phone_perm_sub': 'Call exemption',
    'admin_perm_sub': 'Uninstall protection',
    'battery_perm_sub': 'Keeps walking detection alive',
    'background_protection': 'Background Protection (Auto-start)',
    'background_protection_sub': 'If walking detection stops often: open OEM auto-start settings',
    'others': 'Others',
    'language': 'Language / 语言',
    'lang_system': 'System default',
    'uninstall': 'Uninstall App',
    'uninstall_sub': 'Device admin must be deactivated first if enabled',
    'uninstall_title': 'Uninstall App',
    'uninstall_body_admin': 'Device admin is active and must be deactivated before uninstalling.\n\n'
        'Tap "Deactivate device admin" below, then uninstall from system settings.',
    'uninstall_body_plain': 'Device admin is not active. You can uninstall directly from system settings.',
    'cancel': 'Cancel',
    'deactivate_admin': 'Deactivate device admin',
    'admin_deactivated': 'Device admin deactivated. You can now uninstall normally',
    'about': 'About',
    'about_sub': 'Self-Discipline · Free · No ads · Local data only',
    'select_language': 'Select Language',
    'select_sensitivity': 'Select Sensitivity',
    'about_open_source': 'Open source · code fully auditable',
    'about_no_network': 'No network permission — data cannot leave this device',
    'about_repo_label': 'Source code',
    // ---- App limits ----
    'app_limits': 'App Limits',
    'app_limits_sub': 'Auto-lock after daily limit; resets tomorrow',
    'add_limit_app': 'Add an app',
    'choose_app': 'Choose App',
    'choose_limit': 'Daily limit',
    'limit_per_day_fmt': '{m} min per day',
    'today_used_fmt': '{m} min used today',
    'no_apps_found': 'No supported apps found (Weibo, Douyin, Bilibili…)',
    'delete': 'Delete',
    'need_accessibility_for_limits': 'App limits require the accessibility service',
    'app_limits_empty': 'No limited apps yet',
    'remove_limit': 'Remove limit',
    'remove_limit_title': 'Remove limit?',
    'remove_limit_body_fmt': '"{app}" will no longer have a daily usage limit.',
    'hold_confirm_remove': 'Hold 2 minutes to confirm removal',
    'search_apps': 'Search apps',
    // ---- Stats ----
    'stats_title': 'Statistics',
    'total_focus': 'Total Focus Time',
    'consecutive_days': 'Consecutive Days',
    'days_suffix': 'days',
    'success_count': 'Completed',
    'fail_count': 'Failed',
    'walking_interventions': 'Walking Lock Interventions',
    'times_suffix': 'times',
    'stats_footer': 'All data is stored locally and will be erased on uninstall.',
    // ---- Format ----
    'fmt_hours_minutes': '{h}h {m}m',
    'fmt_minutes': '{m} min',
    // ---- Accessibility guide dialog ----
    'acc_guide_title': 'Enable Accessibility Service',
    'acc_guide_step1': 'Step 1: Enable the service',
    'acc_guide_step1_body': 'Tap "Enable" below, find "Self-Discipline" in system accessibility settings and turn it on.',
    'acc_guide_step2': 'If it cannot be enabled ("Restricted setting")',
    'acc_guide_step2_body': 'Apps installed outside an app store are restricted by the system. Lift the restriction first:\n\n'
        '· Stock Android 13+ / Samsung: Settings → Apps → Self-Discipline → ⋮ menu (top right) → Allow restricted settings\n\n'
        '· Honor: Settings → Apps → App management → Self-Discipline → Remove restriction\n\n'
        '· Xiaomi: Settings → Apps → Manage apps → Self-Discipline → Allow restricted settings\n\n'
        'Then return to accessibility settings and enable the service.\n\n'
        'Note: if the toggle shows ON but the app still says it is off, turn the toggle off and on again (some systems kill the service but keep the toggle state).',
  };
}
