<div align="center">

# 🪟 FloatInput (微浮)
### 专为 Android 15 深度调优 · 原生硬件毛玻璃 · 边框半隐贴边浮窗

[![Android 15](https://img.shields.io/badge/Android-15%20(API%2035)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/about/versions/15)
[![Version](https://img.shields.io/badge/Version-v1.3.0%20(Build%204)-38BDF8?style=for-the-badge&logo=semver&logoColor=white)](https://github.com/yanzhe-Xiao/FloatInput/releases/tag/v1.3.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Size](https://img.shields.io/badge/APK%20Size-2.0%20MB-10B981?style=for-the-badge&logo=googleplay&logoColor=white)](https://github.com/yanzhe-Xiao/FloatInput/releases/latest)
[![CI/CD](https://img.shields.io/badge/GitHub%20Actions-Auto--Build-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)](.github/workflows/build-apk.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-F59E0B?style=for-the-badge)](LICENSE)

<br/>

**FloatInput（微浮）** 是一款轻量到极致、追求纯粹效率的 Android 15 悬浮输入辅助工具。  
半隐形收纳于屏幕黑边内，微光呼吸灯提示停靠，向内轻拨秒级滑出，原生高斯毛玻璃视效，支持任意第三方输入法畅快混输，绝不弹安全键盘。

[📥 立即下载最新 APK (v1.3.0)](https://github.com/yanzhe-Xiao/FloatInput/releases/download/v1.3.0/app-release.apk) • [📦 版本发布页](https://github.com/yanzhe-Xiao/FloatInput/releases) • [🐞 报告问题](https://github.com/yanzhe-Xiao/FloatInput/issues)

</div>

---

### 📱 视觉交互与架构示意

```
 屏幕黑边 ───────────────┐
   │                     │
   │  ┌──┐               │
   ├──┤▍ │ 边框半隐把手   │   ──▶ 向内轻拨 (Inward Swipe) ──▶ 瞬时滑出输入面板
   │  └──┘ (48dp手势跑道) │
   │                     │
   │                     │   ┌──────────────────────────────────────────────────┐
   │                     │   │ [●] 微浮输入  v1.3.0        [=]   [—]折叠   [✕]关闭 │
   │                     │   │ ┌──────────────────────────────────────────────┐ │
   │                     │   │ │ 真实系统级 65px 高斯毛玻璃实时背景虚化透出...  │ │
   │                     │   │ │ 中英文自由混输 (支持微信/搜狗/Gboard/小鹤音形)│ │
   │                     │   │ └──────────────────────────────────────────────┘ │
   │                     │   │  48 字符               [ 🧹 清空 ]   [ 📋 复制 ] │
   │                     │   └──────────────────────────────────────────────────┘
   │                     │         │                        │
   │                     │         ▼ (推向边缘自动吸附)       ▼ (点击外部空白处)
   │                     │      平滑渐隐贴边 (微光呼吸灯闪烁)  无缝收纳回边框把手
   └─────────────────────┘
```

---

## 🌟 核心特性与功能矩阵

| 功能项 | 特性说明 | 技术实现与底层优化 |
| :--- | :--- | :--- |
| **🔤 自由文本输入** | 中英文畅快混输，**绝不调用系统安全键盘** | 严格配置 `TYPE_CLASS_TEXT \| MULTI_LINE \| CAP_SENTENCES`；杜绝任何 `FLAG_SECURE` 与密码变体属性；动态唤起 `InputMethodManager`，完美调用搜狗、微信键盘、百度、Gboard、小鹤音形等所有第三方输入法。 |
| **📋 一键极速复制** | 瞬间将内容提交至系统剪贴板 | 注入 `ClipboardManager`，融合 `HapticFeedbackConstants.CONFIRM` 微触感震动反馈，复制按钮带有微缩放弹性弹跳动效。 |
| **🧹 一键快速清空** | 一键清除全文，光标精准复位 | 毫秒级重置内容，伴随 `VibrationEffect.EFFECT_TICK` 清脆机械敲击反馈，字符统计徽章瞬时归零。 |
| **🪟 原生硬件毛玻璃** | 媲美 iOS / HyperOS 的奢华暗透质感 | 深度利用 Android 15 / 12+ 原生 `FLAG_BLUR_BEHIND`，配置 **65px 硬件 GPU 加速高斯模糊**，配合 `#B811141C` 半透深空琉璃底色与青蓝光晕描边。 |
| **🚪 边框半隐式把手** | 不遮挡视线、贴合屏幕边缘嵌入 | 把手仅宽 `22dp`，外缘齐平屏幕物理黑边，内侧圆润微凸；闲时进入低透明度（Alpha 0.40）静默休眠状态，**背景屏幕触控 100% 原生穿透**。 |
| **💡 微光脉冲呼吸灯** | 贴边瞬间告知停靠坐标 | 折叠入边框的瞬间，把手自动触发一段高亮霓虹微光脉冲闪烁（Cyan Beacon Glow）与轻微点按震动，告知停靠位置后柔和淡出。 |
| **💨 向内划动秒级呼出** | 原生智能侧边栏抽屉级抽拉手势 | 配备 **48dp 隐藏手势跑道**，将向内划动识别阈值精密优化至 **`8dp`（约 2mm 拇指微拂）**，向内一拨瞬时抽出面板；轻点同样支持展开。 |
| **🧲 三维自如隐藏体系** | 彻底摆脱必须精准点击关闭按钮的束缚 | **方式一**：拖曳顶栏将面板推至左右边缘或快速甩动，松手自动磁吸折叠。<br>**方式二**：在浮窗外任意空白处轻触，自动收起面板并收回键盘。<br>**方式三**：轻按顶栏右上角专属「折叠」图标收起。 |
| **🔄 永久签名与在线更新** | 彻底终结「签名冲突」，支持无缝覆盖 | 内置永久固化 Release 官方密钥库（有效期至 2054 年）；应用内内置轻量下载更新引擎，免卸载一键直接覆盖更新。 |

---

## ⚡ 极度优化硬核指标 (Extreme Optimization)

```
┌─────────────────┬───────────────────┬──────────────────────────────────────────┐
│ 指标维度         │ 实测数据           │ 优化实现机制                              │
├─────────────────┼───────────────────┼──────────────────────────────────────────┤
│ 运行时常驻内存   │ < 16.5 MB         │ 纯原生 View 结构，零内存泄漏，无臃肿依赖   │
│ 安装包精简体积   │ 2.06 MB (Release) │ R8 Full Mode 全混淆 + ProGuard 资源精简   │
│ 空闲待机 CPU     │ 0.00% (零电量消耗) │ 完全事件驱动（Event-Driven），无后台轮询   │
│ 动画切换帧率     │ 120 FPS 满帧丝滑   │ 双窗口表面交叉预热融合同步，彻底消除闪屏   │
│ 系统兼容性       │ Android 15 (API 35)│ 适配 DisplayCutout 刘海挖孔与沉浸边到边    │
└─────────────────┴───────────────────┴──────────────────────────────────────────┘
```

---

## 🔄 为什么之前会提示「已安装签名冲突的软件」？

很多用户在升级安装 `v1.0.0` 或 `v1.1.0` 时遇到了 Android 系统提示：**「已安装签名冲突的软件，无法安装」**。

### 🔍 根本原因揭秘
- Android 系统的核心安全机制要求：同一个应用（`com.yxiao.floatinput`）在覆盖升级时，**新 APK 的数字签名证书必须与手机上已安装的旧 APK 完全一致**。
- 在 `v1.0` 和 `v1.1` 中，使用的是云端 GitHub Actions 虚拟机的临时 Debug 密钥。因为 GitHub Actions 每次编译都在一台全新的临时 Linux 云主机中，**每次都会随机生成一个全新公钥指纹的临时证书**，导致两次编译的包在 Android 看来属于“不同开发者”，从而被系统拦截。

### ✅ v1.2.0 永久解决方案
1. **固化官方密钥库**：本项目现已永久配置并提交了专属 Release 签名密钥（`app/keystore/release.jks`），证书指纹（SHA-256: `23:35:9F:C2:A2:90:79:E2:...`）永久固化，有效期至 **2054 年**。
2. **操作说明（仅需一次）**：由于您手机上目前的旧版本使用的是临时的旧证书，**请在手机上卸载旧版本这一次**；
3. **后续升级体验**：安装本次 **v1.2.0** 正式版后，**后续所有版本均支持 100% 免卸载直接覆盖升级**！

---

## 📲 快速安装与使用

### 1. 下载安装
前往 [Releases 页面](https://github.com/yanzhe-Xiao/FloatInput/releases) 或直接点击：
- 🚀 **[FloatInput-v1.2.0-release.apk (2.06 MB 直链下载)](https://github.com/yanzhe-Xiao/FloatInput/releases/download/v1.2.0/app-release.apk)**

### 2. 首次启动与授权
1. 打开应用，点击 **「去授权」** 开启系统的 **「悬浮窗 / 在其他应用上层显示」** 权限。
2. 建议授予 **「通知权限」**（维持前台服务在后台平稳待命）。
3. 允许 **「安装未知应用」** 权限（用于后续在软件内一键免卸载自动更新）。

### 3. 日常手势指引
- **呼出**：停靠在屏幕边框时，用拇指在边框把手处**向屏幕内侧轻划（或轻点）**，毛玻璃输入面板顺滑抽出。
- **输入**：轻触输入框自由输入中英文，任意第三方输入法自动呼出。
- **复制与清空**：点击「复制」写入剪贴板；点击「清空」重置文本。
- **收起隐藏**：
  - 手指按住顶栏将卡片**推向屏幕边缘松手**；
  - 或者**直接轻触浮窗外的空白处**；
  - 或者点击右上角的**折叠图标**。
- **快捷开关**：支持在 Android 15 下拉控制中心（Quick Settings）添加「极简悬浮输入」快捷磁贴，单手下拉一键全局开关！

---

## 🛠️ 本地编译与构建

如果您希望自行从源码编译本应用：

```bash
# 1. 克隆代码仓库
git clone https://github.com/yanzhe-Xiao/FloatInput.git
cd FloatInput

# 2. 赋予 Gradle 脚本执行权限
chmod +x ./gradlew

# 3. 编译发布版本 (自动执行 R8 全混淆压缩与永久密钥库签名)
./gradlew assembleRelease

# 4. 生成的 APK 位于:
# app/build/outputs/apk/release/app-release.apk
```

---

## 🔒 权限与隐私承诺

本应用严格坚守隐私底线，**不设任何后台常驻联网收集隐私行为**：
- `SYSTEM_ALERT_WINDOW`：用于显示悬浮输入栏与侧边把手（必需）。
- `FOREGROUND_SERVICE` & `SPECIAL_USE`：Android 14/15 规范，维持后台悬浮服务稳定。
- `POST_NOTIFICATIONS`：Android 13+ 常驻轻量状态栏控制通知。
- `VIBRATE`：按键及边缘停靠微触感反馈。
- `INTERNET`：**仅用于在应用内检查 GitHub 最新 Release 版本及下载更新包**，绝不上报任何用户输入内容。
- `REQUEST_INSTALL_PACKAGES`：用于下载新版本后调起系统安装器执行免卸载直接覆盖升级。

---

## 📄 开源许可证

本项目基于 [Apache License 2.0](LICENSE) 协议完全开源。欢迎提交 Issue 或 Pull Request！
