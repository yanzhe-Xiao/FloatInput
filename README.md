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
## 🔄 在线自动更新与统一签名机制 (v1.2.0 升级)

### 🚨 为什么之前会提示「已安装签名冲突的软件」？
- **根本原因**：在之前的自动编译流程中，使用的是云端 CI 临时生成的 Debug 签名密钥。由于每次 GitHub Actions 运行都在一台全新的临时虚拟机中，系统每次都会动态生成一个**不同公钥指纹的临时证书**。
- 当手机上安装了证书 A 的包，再尝试安装证书 B 的新包时，Android 系统的 PackageInstaller 安全机制就会强制拦截并提示：**「已安装签名冲突的软件」/「签名不一致，无法直接更新」**。
- **v1.2.0 永久修复方案**：
  1. **配置固定永久 Release 密钥库**（`app/keystore/release.jks`），所有云端构建与本地构建统一使用固定证书签名。
  2. **重要操作提示**：由于之前安装的版本使用的是旧的临时签名，**请仅需卸载旧版这一次**；安装本次 `v1.2.0` 正式签名版后，**后续所有版本均支持 100% 免卸载直接覆盖安装**！

### 🚀 应用内一键检查更新与免卸载覆盖安装
- **自动检测**：应用启动时在后台静默请求 GitHub 最新发布版本，发现新版本时主界面自动展开新版提示与更新日志。
- **一键极速下载**：内置原生轻量多线程下载器，实时显示下载百分比进度条。
- **直接调起系统安装**：下载完成后自动通过 `FileProvider` 调起 Android 原生安装器，直接提示「您要更新此应用吗？」，轻点一下即可完成无缝覆盖更新，保留全部配置与悬浮窗位置。

---


## 🎯 贴边隐藏、微光呼吸灯与向内划动手势 (v1.1.0 升级)

- **侧边栏隐形把手（Collapsed Mode）**：
  - **半隐形嵌入边框**：折叠后把手紧密吸附在屏幕左侧或右侧边框内，外缘贴合屏幕黑边，内侧圆润微凸，仅留一条精致的霓虹指示细条。
  - **微光脉冲呼吸灯（Luminous Beacon）**：浮窗收缩贴边瞬间，把手会发出一段清脆的高亮微光脉冲（Cyan Glow）与触感点按反馈，明确告知停靠位置，随后柔和淡入低透明度静默待命状态（Alpha 0.40）。
  - **向内划动秒级展开（Inward Swipe Gesture）**：需要输入时，直接从屏幕边缘把手处**向屏幕内侧划动**（或轻点），面板如同原生智能侧边栏抽屉一般瞬时滑出！
  - **彻底修复误回弹 Bug**：展开后在全屏任意位置畅快编辑、输入、拖动，绝无打开瞬间误判回弹。
  - **极致无感触控穿透**：隐藏状态下完全开启 `FLAG_NOT_FOCUSABLE`，除把手极小区域外，屏幕其他触控 100% 穿透至背景，打游戏、刷短视频毫无干扰。

- **卡片展开（Expanded Mode）**：
  - 采用全新设计的深色暗透玻璃拟物风格（Dark Glassmorphism），搭配青蓝霓虹微光边缘与字符计数胶囊。
  - 顶部微型拖曳把手，点击右上角「折叠」图标或拖动均可平滑收回侧边。
  - 打开与关闭均具备 Overshoot 弹性物理滑动动画。
  - **持久化记忆**：实时保存坐标、停靠侧边与输入草稿，横竖屏旋转或重启后依然精准复位。
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
