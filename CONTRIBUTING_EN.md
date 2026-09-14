# Contributing

Thanks for helping improve AdMask. This document explains how to report issues and open pull requests.

English | [简体中文](CONTRIBUTING.md)

## Reporting issues

- Search [existing issues](../../issues) first to avoid duplicates
- Use the issue template and include at least: device model, Android version, ROM (e.g. MIUI 14), app version, reproduction steps, expected vs. actual behavior
- If the mask does not show up, also state: whether the accessibility service is enabled, the notification permission and battery-optimization status, and whether you ever used the system *Force stop*
- Attach `adb logcat` output or screenshots when relevant (strip any private information)

## Pull requests

1. Fork the repository and create a branch, e.g. `feat/color-wheel`, `fix/boot-restore`
2. Make sure it compiles locally:

   ```bash
   ./gradlew assembleDebug
   ```

3. Follow [Conventional Commits](https://www.conventionalcommits.org/) for commit messages:

   ```text
   feat: add HSV color wheel
   fix: reset the enable switch on cold start
   docs: add English README
   refactor: extract color input handling
   ```

4. In the PR description cover: why the change is needed, how it is implemented, and which devices / Android versions you tested

## Code style

- Kotlin official style (already set via `kotlin.code.style=official` in `gradle.properties`), 4-space indentation
- Do not add third-party dependencies unless the benefit is clear and justified in the PR
- Do not request permissions unrelated to the feature; **never add the network permission or collect any data**
- Any change involving permissions or the accessibility service must be documented in both Chinese and English
- User-visible strings belong in `strings.xml`; the project currently ships Chinese resources only, so keep them in sync
- When touching drawing logic, verify on a real device: screen lock/unlock, orientation change, app switching, and restart after the process is killed

## Code of conduct

Be kind and stay on topic. Personal attacks and off-topic arguments will be closed or removed.

## License

By contributing you agree that your code is licensed under the repository's [MIT License](LICENSE).
