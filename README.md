# FloatInput (微浮 - 侧边极简输入浮窗)

> 🚀 **专为 Android 15 (API 35) 极度调优** 的极简屏幕悬浮输入条。可吸附隐藏至屏幕边缘为微型胶囊把手，轻触即开，支持任意第三方输入法畅快混输，绝不触发系统安全键盘。

[![Android 15](https://img.shields.io/badge/Android-15%20(API%2035)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/15)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub Actions CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)](.github/workflows/build-apk.yml)

---

## ✨ 核心特性与三大功能

本应用遵循 **极简（Minimalist）与极致性能（Extreme Optimization）** 设计理念，没有一丝冗余逻辑，只专注于三个核心功能和优雅的侧边收纳体验：

### 1. 🔤 中英文无限制畅快输入（绝不调用安全键盘）
- **痛点解决**：很多悬浮窗由于配置了错误密码类 InputType、设置了窗口 `FLAG_SECURE` 或未合理移除 `FLAG_NOT_FOCUSABLE`，会导致部分国产系统（小米 HyperOS、华为 HarmonyOS、vivo OriginOS、OPPO ColorOS 等）误判为密码敏感输入，强制弹起**系统纯英文/纯数字安全键盘**，无法切换第三方输入法，无法输入中文。
- **优化方案**：
  - 严格采用 `InputType.TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE or TYPE_TEXT_FLAG_CAP_SENTENCES` 标准自由输入模式。
  - 动态聚焦管理：展开并轻触输入框时动态启用 IME 交互通道并主动调度 `InputMethodManager`，完美调用搜狗输入法、微信键盘、百度输入法、Gboard、小鹤音形等所有中英文输入法。
  - 支持多行自动伸缩排版与实时字数统计。

### 2. 📋 一键极速复制
- 点击「复制」按钮立即将输入框内的文本打包提交至系统剪贴板（`ClipboardManager`）。
- **优化细节**：
  - 融入清脆触感反馈（`HapticFeedbackConstants.CONFIRM` + `VibrationEffect` 毫秒级微震动）。
  - 按钮附带平滑弹性微缩放动效（Overshoot Animation）。
  - 针对 Android 13/14/15 原生剪贴板预览气泡进行了优雅规避，杜绝屏幕重复弹窗打扰。

### 3. 🧹 一键快速清空
- 点击「清除」按钮瞬间清空输入框内容，重置光标至起始位置。
- 配备清脆敲击微震动（Tick Haptic Feedback），字数统计即刻归零。

---

## 🎯 贴边隐藏与侧边栏胶囊交互

- **侧边胶囊（Collapsed Mode）**：
  - 折叠后自动贴合在屏幕左边缘或右边缘，仅显示一条宽度仅 `32dp` 的精致半透明小把手。
  - **极致无感穿透**：胶囊状态下完全开启 `FLAG_NOT_FOCUSABLE`，除胶囊自身面积外的任何屏幕触控均 **100% 穿透到背景应用**，完全不影响打游戏、看小说、刷短视频。
  - 支持沿屏幕边缘上下拖曳微调位置；支持拖动中根据中线自动平滑磁吸回弹至最近侧边（Decelerate Interpolator 边缘吸附动效）。
- **卡片展开（Expanded Mode）**：
  - 轻触胶囊瞬间弹性展开为 Material 3 暗色晶透输入面板。
  - 顶部配有拖曳把手，可在全屏任意位置自由摆放。
  - 拖至屏幕边缘（距离边缘 < 30dp）或点击顶栏「折叠」图标，将自动平滑收纳回侧边胶囊。
  - **位置持久化**：使用轻量级首选项（`PreferencesHelper`）实时记录悬浮坐标与停靠侧边，横竖屏旋转或重启后依然精准记忆位置。

---

## ⚡ 极度优化 (Extreme Optimization)

| 优化维度 | 优化措施与指标 |
| :--- | :--- |
| **内存占用 (RAM)** | 平时驻留内存 **< 15MB**，采用高效轻量 View 结构，杜绝冗余内存泄漏。 |
| **包体积 (APK Size)** | 开启 R8 全模式混淆优化与资源强力缩减（`isShrinkResources = true`, `isMinifyEnabled = true`），生成的 Release 包极其小巧（< 1.8MB）。 |
| **功耗与续航** | 采用**完全事件驱动架构（Event-Driven）**，空闲时 CPU 占用率为 **0%**，无后台轮询、无闹钟唤醒，零电量消耗。 |
| **Android 15 原生适配** | - 适配 Android 15 (Target SDK 35)。<br>- 前台服务严格遵循 Android 14/15 规范，声明 `android:foregroundServiceType="specialUse"` 与对应 subtype 属性。<br>- 窗口坐标精准兼容全面屏异形屏（`WindowInsets.Type.displayCutout()` 与刘海/打孔挖孔避让）。<br>- 启动页与主页采用 Edge-to-Edge 沉浸式边到边设计。 |
| **控制中心快捷开关** | 内置 Android 原生 `TileService`，可在下拉通知栏控制中心添加「极简悬浮输入」磁贴，无需打开主应用即可单手瞬间呼出/收起浮窗。 |

---

## 📱 运行与编译

### GitHub Actions 自动构建 (推荐)
本项目已完整配置 `.github/workflows/build-apk.yml` 持续集成：
1. 仓库每次推送提交或发布 Tag 时，GitHub Actions 会自动编译生成 Debug 与 Release 版本的 APK。
2. 可以在仓库页面的 **Actions -> Build Android APK -> Artifacts** 中直接下载 `FloatInput-APKs.zip` 安装到安卓 15 手机上。

### 本地编译
在拥有 Android SDK 与 JDK 17 环境的机器上运行：
```bash
# 赋予执行权限
chmod +x ./gradlew

# 编译 Debug 版
./gradlew assembleDebug

# 编译 Release 版 (已配置优化混淆与自动签名)
./gradlew assembleRelease
```
编译产物位于 `app/build/outputs/apk/` 目录下。

---

## 🔒 权限说明

- `SYSTEM_ALERT_WINDOW`：用于在其他应用之上显示悬浮输入栏与侧边胶囊把手（必需）。
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`：Android 14/15 系统规范，保障悬浮窗在后台平稳待命不被系统误杀。
- `POST_NOTIFICATIONS`：Android 13+ 显示常驻轻量通知（提供快捷展开与退出按钮）。
- `VIBRATE`：用于复制成功与清空文本时的清脆微触感反馈。

---

## 📄 开源许可

本项目遵循 [Apache License 2.0](LICENSE) 开源协议。
