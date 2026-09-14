# 贡献指南

感谢你愿意为 AdMask 出一份力。本文说明提交 Issue 与 Pull Request 的基本要求。

[English](CONTRIBUTING_EN.md) | 简体中文

## 提交 Issue

- 提 Bug 前请先搜索[已有 Issue](../../issues)，避免重复
- 使用模板填写，至少包含：设备型号、Android 版本、ROM（如 MIUI 14）、应用版本、复现步骤、预期与实际表现
- 涉及遮罩不显示时，请说明：无障碍服务是否已开启、通知权限与电池优化白名单状态、是否使用过系统「强制停止」
- 如有日志（`adb logcat`）或截图，请一并提供（注意隐去隐私信息）

## 提交 Pull Request

1. Fork 本仓库并新建分支，例如 `feat/color-wheel`、`fix/boot-restore`
2. 本地确认可编译：

   ```bash
   ./gradlew assembleDebug
   ```

3. 提交信息建议遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：

   ```text
   feat: 新增 HSV 调色盘
   fix: 修正冷启动时开关状态显示错误
   docs: 补充英文 README
   refactor: 抽离颜色输入逻辑
   ```

4. PR 描述中说明：改动原因、实现方式、在哪些设备 / 系统版本上验证过

## 代码规范

- Kotlin 官方代码风格（`gradle.properties` 中已设置 `kotlin.code.style=official`），缩进 4 空格
- 不引入新的三方依赖，除非有明显收益并在 PR 中说明理由
- 不申请与功能无关的权限；**绝不申请网络权限、不收集任何数据**
- 所有新增加密 / 权限 / 无障碍相关行为必须在 PR 与文档中用中文和英文同时说明
- 新增用户可见文案请同时补充 `strings.xml`；本项目暂未提供英文资源，请勿遗漏
- 修改绘制相关逻辑时，务必在真机上验证以下场景：锁屏解锁、切换横竖屏、切换应用、主进程被杀后重启

## 行为准则

请保持友善、就事论事。任何人身攻击、与项目无关的争论都会被直接关闭或删除。

## 许可证

你提交的代码默认以本仓库的 [MIT License](LICENSE) 授权。
