# AdMask（广告遮罩）

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-brightgreen.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

[English](README_EN.md) | 简体中文

AdMask 是一款使用 **无障碍悬浮层** 在屏幕顶部 / 底部绘制纯色遮挡条的 Android 工具，用来屏蔽应用内的横幅广告位，让阅读区域更干净。

> 本应用只做「画一块不透明的色块」，**不拦截请求、不修改其他应用、不读取屏幕内容**，也不申请任何网络权限。

---

## 功能特性

- **顶部 / 底部独立开关**：可只遮底部、只遮顶部，或同时开启
- **尺寸精细可调**：高度（0–400dp）、距屏幕边缘偏移（0–300dp）、宽度（20%–100%）
- **外观自定义**：颜色（预设色 + **HSV 调色盘** + **RGB / HEX 手动输入**）、不透明度（10%–100%）
- **屏幕内实时调整**：点「调整位置」后屏幕右侧出现浮动面板，▲▼ 调整偏移、＋－ 调整高度
- **生效范围可控**：可只在你选定的应用中显示遮罩
- **开机自动恢复**：配合前台服务与开机广播，重启后自动恢复遮罩
- **多种快捷入口**：通知栏常驻（调整 / 关闭）、快捷设置磁贴一键开关
- **状态自愈**：主进程被回收后，重新打开界面会自动拉起服务；权限失效时开关自动复位为关闭，避免出现「显示已开启但其实没生效」

## 截图

> 建议在仓库中新建 `docs/screenshots/` 放入截图后，在此处引用：
>
> ```markdown
> ![主界面](docs/screenshots/main.png)
> ```

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| JDK | 17 |
| `minSdk` | 26（Android 8.0） |
| `targetSdk` | 36 |
| 依赖 | AndroidX Core / AppCompat / Activity、Material Components |

## 编译与安装

```bash
# 调试包
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug

# 安装到已连接设备
./gradlew installDebug

# 正式包（当前未开启混淆）
./gradlew assembleRelease
```

产物路径：`app/build/outputs/apk/<debug|release>/`

> 若 Gradle 下载缓慢，可保留 `settings.gradle.kts` 中的阿里云镜像，或替换为其他可用镜像。

## 使用步骤

1. 安装并打开 AdMask
2. 在「运行所需权限」中点击**去开启**，在系统「无障碍」里打开 **广告遮罩悬浮层**
3. 回到应用，打开「启用遮罩」开关
4. 点「调整位置」，用屏幕右侧面板把遮罩对齐到广告条位置
5. （可选）在「外观」中挑选颜色，或用调色盘 / RGB / HEX 精确配色

### 注意事项

- **不要用系统的「强制停止」**：强制停止会连带关闭本应用的无障碍服务，需要重新授权才能继续使用；请用本页的「停止」或通知栏的「关闭」
- 想让遮罩长期稳定，建议同时：授予通知权限、将应用加入电池优化白名单、允许自启动并锁定后台任务
- 遮罩窗口设置了 `FLAG_NOT_TOUCHABLE`，不接收触摸事件，不影响正常阅读与翻页
- 进程被杀或首次打开应用时，「启用遮罩」会默认处于**关闭**状态，这是刻意设计：此时无障碍授权通常已失效，需要你重新授权后手动打开

## 实现说明

| 模块 | 职责 |
| --- | --- |
| `MainActivity` | 设置界面：开关、尺寸、外观、生效范围、权限引导 |
| `OverlayService` | 前台服务：保活、心跳（2 秒）、转发停止 / 更新 / 调整指令 |
| `MaskAccessibilityService` | 无障碍服务：持有 `TYPE_ACCESSIBILITY_OVERLAY` 窗口并真正绘制遮罩；监听窗口切换得到前台包名 |
| `MaskController` | 绘制逻辑：按配置创建 / 更新 / 移除顶部与底部窗口，应用白名单过滤 |
| `ColorPickerView` | 自绘 HSV 调色盘（左侧饱和度 / 明度面板 + 右侧色相条） |
| `OverlayPrefs` | 全部用户配置的读写封装 |
| `MaskTileService` | 快捷设置磁贴 |
| `BootReceiver` | 开机后按用户设置恢复遮罩 |

**为什么需要无障碍服务？** 普通悬浮窗（`TYPE_APPLICATION_OVERLAY`）层级较低，在部分 ROM 上会被应用盖住。无障碍悬浮层层級更高，可以稳定地盖在广告条之上。本服务仅通过窗口事件获取**当前前台应用的包名**（用于应用白名单），`flagRetrieveInteractiveWindows` 未开启，**不会读取任何屏幕内容**。

心跳机制：前台服务每 2 秒写入一次时间戳，无障碍服务发现心跳超时（15 秒）会自动撤掉遮罩，避免主进程被杀后残留色块。

## 隐私说明

- 应用**不联网**，未声明 `INTERNET` 权限
- 不收集、不上传任何数据
- 无障碍服务仅用于绘制遮罩与读取前台应用包名，不做任何其他用途

## 参与贡献

欢迎提交 Issue 与 Pull Request，详见 [CONTRIBUTING.md](CONTRIBUTING.md)（[English](CONTRIBUTING_EN.md)）。

## 许可证

[MIT License](LICENSE) © 2026 AdMask contributors
