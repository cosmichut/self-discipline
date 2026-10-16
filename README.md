# 自律助手 (Self-Discipline)

> [English](README_EN.md)

一款帮助你专注学习和工作、走路时放下手机的 Android 应用。

**免费 · 无广告 · 数据完全本地存储 · 不收集任何用户信息**

## 截图

| 主页 | 专注锁定 | 行走保护 | 应用限额 |
|---|---|---|---|
| ![主页](docs/screenshots/home.jpg) | ![专注锁定](docs/screenshots/focus_lock.jpg) | ![行走保护](docs/screenshots/walking_overlay.jpg) | ![应用限额](docs/screenshots/limit_overlay.jpg) |

> 许可说明：本项目采用 [PolyForm Noncommercial 1.0.0](LICENSE) + 附加许可 ——
> 可自由使用、修改、再分发；允许厂商**免费预置**；但**禁止收费、禁止加广告**。
> 严格来说这属于「源码可见（source available）」而非 OSI 定义的开源软件，
> 这是为了守住「免费、无广告」的初衷。

## 功能

### 1. 自律锁定模式
- 选择专注时长（15/25/45/60/90/120 分钟），一键开始
- 锁定期间全屏锁定界面覆盖，切换到其他应用会被立即拉回（无障碍服务实现）
- 倒计时归零自动成功并记录
- 紧急退出：长按锁定界面**底部中间区域** 5 秒 + 确认（记为失败）。界面无可见退出按钮，退出方式在「开始专注」按钮下方前置告知
- 锁定期间**允许按电源键息屏省电**，亮屏后自动回到锁定界面，倒计时继续
- **来电可正常接听**：锁定期间通话界面在白名单中，不受限制
- 锁定状态持久化：杀进程、重启手机后锁定继续

### 2. 行走检测模式（默认开启）
- 完成首次权限引导后默认开启，可随时关闭
- 前台服务持续监测加速度传感器，方差算法判断是否行走（App 在后台/熄屏也工作）
- **迟滞缓冲防呆**：持续行走 3 秒才锁定（拿起/放下手机不误触发），停止 6 秒才解除（等红灯不闪烁）
- **来电豁免**：来电/通话中不触发行走锁定，已触发的自动解除
- 静止时开启的音乐在行走锁定期间继续播放
- 灵敏度三档可调（高/中/低），适配不同机型传感器
- 服务心跳监控：首页卡片显示真实运行状态，被系统杀掉时点击卡片一键重启
- 开机自动恢复检测；打开 App 时自动拉起被杀的服务

### 3. 统计
- 累计专注时长、连续自律天数、成功/失败次数、行走拦截次数
- 全部存储在本机，卸载即清除

## 工作原理与能力边界（如实说明）

| 能力 | 实现方式 | 边界 |
|------|---------|------|
| 阻止切换应用 | 无障碍服务监测前台窗口，非白名单则拉回锁定界面；onPause 延迟 500ms 抢回前台 | 用户可在系统设置中关闭无障碍服务（自我约束工具，非强制管控） |
| 紧急退出 | 锁定界面底部中间不可见区域长按 5 秒 + 确认 | 会记录为失败 |
| 息屏省电 | 锁定期间可按电源键息屏，亮屏自动恢复锁定界面 | — |
| 防卸载 | 设备管理员（可选激活） | 应用内「设置 → 卸载应用」可随时停用 |
| 行走检测 | 加速度计方差 + 3 秒进入/6 秒退出迟滞 | 坐车颠簸可能误判（可调低灵敏度） |
| 来电豁免 | 监听通话状态 + 白名单电话应用包名 | 部分深度定制 ROM 的通话界面包名可能不同，欢迎提 issue 补充 |

## 安装

1. 从 [Releases](https://github.com/cosmichut/self-discipline/releases) 下载 APK
2. 允许「未知来源」安装
3. 首次启动按引导开启**无障碍服务**（必需），其余权限按需授权

### 常见问题：无障碍服务无法开启？

侧载（非应用市场）安装的应用在荣耀 MagicOS、小米 MIUI、原生 Android 13+ 上会被系统标记为「受限应用」，开启无障碍时会提示"受限制的设置"或按钮灰色不可点。解决方法：

- **通用**：设置 → 应用 → 应用管理 → 自律助手 → 右上角菜单（⋮）→ **允许受限设置**
- **荣耀**：设置 → 应用和服务 → 应用管理 → 自律助手 → **解除限制**
- **小米**：设置 → 应用设置 → 应用管理 → 自律助手 → **允许受限设置**

解除后重新进入无障碍设置开启即可。App 内的权限引导页也有同样的图文提示。

### 常见问题：行走检测经常被系统停止？

国产 ROM（荣耀/小米/OPPO/vivo 等）对后台服务有激进的省电限制。请在 App「设置」页：

1. 开启**忽略电池优化**
2. 点击**后台运行保护（自启动）**跳转厂商设置页，允许自律助手自启动和后台活动

荣耀手动路径：手机管家 → 应用启动管理 → 自律助手 → 关闭"自动管理"，手动允许自启动、关联启动、后台活动。

首页行走检测卡片会显示服务真实运行状态（心跳监控），服务被杀时卡片变橙色，点击即可重启。

## 可选：最强锁定（设备所有者模式）

默认的「无障碍拉回 + 延迟抢回前台」对绝大多数用户已足够，且无任何弹窗。如果你希望达到 kiosk 级别的锁定强度（屏幕固定，上滑手势系统级失效），且手机可连电脑执行 ADB：

```bash
# 注意：设置设备所有者要求手机未添加任何账号（或先移除），操作前请备份
adb shell dpm set-device-owner io.github.cosmichut/.DeviceAdminReceiver
```

设置后：手动锁定将静默进入屏幕固定，上滑手势完全失效，无任何系统弹窗。
解除：`adb shell dpm remove-active-admin io.github.cosmichut/.DeviceAdminReceiver`

## 自行构建

### Docker 构建（推荐，无需配置本地环境）

首次使用先构建编译镜像（含 Flutter SDK + Android SDK + JDK 17，约 10-20 分钟）：

```bash
bash docker_image_build.sh   # 构建镜像 flutter-env:ok（只需一次）
bash docker_build.sh         # 构建 APK
```

产物：
- `flutter_app/build/app/outputs/flutter-apk/app-release.apk`
- `self-discipline.apk`（复制到根目录）

缓存策略：Gradle 缓存 `~/.gradle`；Pub 缓存项目内 `./pub_cache`；debug 签名密钥 `~/.android`（持久化保证每次构建签名一致）。
镜像构建完成后，首次编译约 10-15 分钟，增量约 3-5 分钟。

### 本地构建

```bash
cd flutter_app
flutter pub get
flutter build apk --release
```

### 运行测试

```bash
cd flutter_app && flutter test
```

## 签名说明

`release` 构建默认使用 **debug 签名**（`android/app/build.gradle`），方便任何人直接构建出可安装的 APK。
Releases 中官方发布的 APK 同样以 debug key 签名——这意味着**请勿从不可信渠道下载 APK**（他人可伪造同签名应用）。
如需自己管理签名：

```bash
keytool -genkey -v -keystore release.keystore -alias mykey -keyalg RSA -keysize 2048 -validity 10000
```

然后在 `buildTypes.release` 中配置 `signingConfig`，并创建 `android/key.properties`。

## 权限清单（最小化）

| 权限 | 用途 | 必需性 |
|------|------|--------|
| 无障碍服务 | 锁定期间阻止切换应用（**不读取屏幕内容**，配置中 `canRetrieveWindowContent=false`） | 核心功能必需 |
| 前台服务 + 通知 | 行走检测常驻 | 行走检测必需 |
| READ_PHONE_STATE | 来电豁免（仅读通话状态，不读号码） | 推荐 |
| WAKE_LOCK | 熄屏后持续传感器采样 | 行走检测必需 |
| RECEIVE_BOOT_COMPLETED | 开机恢复检测/未完成的锁定 | 推荐 |
| 忽略电池优化 | 防止系统杀掉检测服务 | 推荐 |
| 设备管理员 | 仅用于卸载保护 | 可选 |

**应用列表**：应用限额的选择列表通过 `<queries>` 声明 launcher intent 查询实现（官方豁免通道），**未申请** `QUERY_ALL_PACKAGES` 敏感权限。

**未申请**：网络、定位、存储、通讯录等一切与功能无关的权限。

**未申请**：网络、定位、存储、通讯录等一切与功能无关的权限。

## 技术栈

- Flutter 3.x（Dart）+ Kotlin（原生层）
- 锁定/统计状态持久化在原生侧 `SharedPreferences`，Flutter 引擎被杀不丢失
- MethodChannel/EventChannel 桥接
- minSdk 24 / targetSdk 35

## 项目结构

```
flutter_app/
├── lib/
│   ├── main.dart                    # 入口 + 引导路由
│   ├── screens/                     # 主页/统计/设置/权限引导
│   ├── services/native_service.dart # 原生通道封装
│   └── utils/format.dart            # 纯函数工具（有单测）
└── android/app/src/main/kotlin/io/github/cosmichut/
    ├── LockManager.kt               # 锁定状态与统计中心（原生持久化）
    ├── LockActivity.kt              # 全屏锁定界面（倒计时 + 紧急退出）
    ├── LockAccessibilityService.kt  # 锁定执行器（无障碍）
    ├── WalkingService.kt            # 行走检测前台服务
    ├── BootReceiver.kt              # 开机恢复
    ├── DeviceAdminReceiver.kt       # 卸载保护
    └── MainActivity.kt              # 通道桥接 + 权限请求
```

## 发布清单（维护者）

- [ ] 更新 `pubspec.yaml` version 与 CHANGELOG.md
- [ ] `flutter test` 通过
- [ ] `bash docker_build.sh` 构建 APK
- [ ] **将 APK 上传到 GitHub Releases**（附版本说明）
- [ ] 打 git tag

## 开发说明

本项目由 Claude Code 协作开发。

## 贡献

欢迎 Issue 和 PR，请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

[PolyForm Noncommercial License 1.0.0](LICENSE) + 附加许可（允许免费预置，禁止收费与广告）。
Copyright (c) 2026 自律助手项目贡献者。
