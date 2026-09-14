# AdMask

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-brightgreen.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

English | [简体中文](README.md)

AdMask is an Android utility that draws **solid color bars** on the top and/or bottom of the screen through an **accessibility overlay**, hiding in-app banner ad slots and keeping the reading area clean.

> It only draws an opaque rectangle. It does **not** block network requests, modify other apps, or read on-screen content, and it declares **no network permission** at all.

---

## Features

- **Independent top / bottom switches** – cover the bottom only, the top only, or both
- **Fine-grained geometry** – height (0–400dp), offset from the screen edge (0–300dp), width (20%–100%)
- **Appearance** – preset swatches plus a **HSV color wheel** and **RGB / HEX manual input**, opacity from 10% to 100%
- **On-screen adjustment** – tap *Adjust* and a floating panel appears on the right edge: ▲▼ moves the bar, ＋－ resizes it
- **Scoped to selected apps** – optionally show the mask only inside apps you pick
- **Auto restore after reboot** – a foreground service plus the boot receiver bring the mask back
- **Quick entries** – ongoing notification (*Adjust* / *Stop*) and a Quick Settings tile
- **Self-healing state** – if the process was killed, reopening the app restarts the service; if the permission was revoked, the switch resets to *off* so it never claims to be enabled while doing nothing

## Screenshots

> Create `docs/screenshots/` and drop the images there, then reference them:
>
> ```markdown
> ![Main screen](docs/screenshots/main.png)
> ```

## Requirements

| Item | Version |
| --- | --- |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| JDK | 17 |
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` | 36 |
| Dependencies | AndroidX Core / AppCompat / Activity, Material Components |

## Build & install

```bash
# Debug APK
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug

# Install on a connected device
./gradlew installDebug

# Release APK (minification is currently disabled)
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/<debug|release>/`

> If dependency resolution is slow, keep the Aliyun mirror declared in `settings.gradle.kts` or replace it with another mirror reachable from your network.

## Usage

1. Install and open AdMask
2. Tap **Enable** under *Required permission* and turn on **AdMask Overlay** in the system Accessibility screen
3. Return to the app and switch on **Enable mask**
4. Tap **Adjust** and align the bar with the ad slot using the floating panel
5. (Optional) Pick a color in *Appearance*, or dial in an exact value with the color wheel / RGB / HEX fields

### Notes

- **Never use the system "Force stop"** – it also disables this app's accessibility service and you have to grant it again. Use *Stop* in the app or *Turn off* in the notification instead
- For long-term stability: grant the notification permission, add the app to the battery-optimization whitelist, allow auto-start and lock it in the recent-apps list
- The mask window uses `FLAG_NOT_TOUCHABLE`, so it never intercepts touches – reading and page turning are unaffected
- On a cold start (first launch, or after the process was killed) the **Enable mask** switch is intentionally **off**: the accessibility grant is usually gone at that point, so you re-authorize and turn it on yourself

## How it works

| Module | Responsibility |
| --- | --- |
| `MainActivity` | Settings screen: switches, geometry, appearance, scope, permission guidance |
| `OverlayService` | Foreground service: keep-alive, heartbeat (2s), dispatches stop / update / adjust commands |
| `MaskAccessibilityService` | Accessibility service: owns the `TYPE_ACCESSIBILITY_OVERLAY` window and actually draws the mask; reads the foreground package from window events |
| `MaskController` | Drawing logic: creates / updates / removes the top and bottom windows, applies the app allowlist |
| `ColorPickerView` | Self-drawn HSV wheel (saturation / value panel + hue bar) |
| `OverlayPrefs` | Single entry point for reading and writing every user setting |
| `MaskTileService` | Quick Settings tile |
| `BootReceiver` | Restores the mask after boot when the user enabled it |

**Why an accessibility service?** A regular overlay window (`TYPE_APPLICATION_OVERLAY`) sits on a lower z-order and gets covered by apps on several ROMs. The accessibility overlay is layered above app windows, so the bar stays on top of the ad slot. The service only receives **the package name of the foreground app** (used by the allowlist); `flagRetrieveInteractiveWindows` is off and **no screen content is ever read**.

Heartbeat: the foreground service writes a timestamp every 2 seconds. If the accessibility service sees the heartbeat go stale (15s), it removes the mask so no orphan bar is left behind after the process dies.

## Privacy

- No network access – the `INTERNET` permission is not declared
- Nothing is collected and nothing is uploaded
- The accessibility service is used solely for drawing the mask and reading the foreground package name

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING_EN.md](CONTRIBUTING_EN.md) ([简体中文](CONTRIBUTING.md)).

## License

[MIT License](LICENSE) © 2026 AdMask contributors
