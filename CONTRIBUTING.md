# 贡献指南

感谢你愿意为自律助手贡献力量！

## 行为准则

保持友善与尊重。本项目服务于「帮助用户自律」这一纯粹目标。

## 可以贡献什么

- **Bug 修复**：尤其是各品牌 ROM 兼容性问题（如通话界面包名、无障碍服务行为差异）
- **行走检测算法改进**：降低误判（如坐车颠簸）、降低功耗
- **文档与翻译**
- **新功能讨论**：请**先开 Issue 讨论**再动手，避免方向冲突

## 贡献流程

1. Fork 并创建分支：`git checkout -b feature/xxx`
2. 提交代码，commit message 简明描述改动
3. Dart 代码通过 `flutter analyze` 与 `flutter test`
4. 提 PR，说明改动动机与测试情况

## 代码约定

- Dart：遵循 `flutter_lints`，UI 文案使用中文
- Kotlin：保持现有分层（LockManager 管状态，Service 管执行，Activity 管展示）
- **不引入任何网络请求、统计 SDK、广告 SDK** —— 这是项目红线
- 新增权限需要充分理由，并在 README 权限清单中说明

## 许可

贡献的代码将按项目现有许可证（PolyForm Noncommercial 1.0.0 + 附加许可）发布。
