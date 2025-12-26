# Droid2Run

[![Build & Release APK](https://github.com/user/Droid2Run/actions/workflows/build-release.yml/badge.svg)](https://github.com/user/Droid2Run/actions/workflows/build-release.yml)

Run virtual machines on your Android device using QEMU.

## Features

- Run Windows XP, Windows 7, Windows 10, Linux, and other x86 OSes
- Built-in VNC viewer with touch controls
- Virtual keyboard with special keys (Ctrl, Alt, Esc)
- Virtual mouse with left/right/middle click
- Modern Material 3 UI with dark theme
- ISO file picker for boot media
- Per-OS optimized configurations

## Requirements

- Android 11+ (API 30)
- ARM64 or x86_64 device
- At least 4GB RAM recommended

## Download

Download the latest APK from [Releases](../../releases):

| APK | Description |
|-----|-------------|
| `arm64-v8a` | Modern 64-bit ARM phones (most devices) |
| `armeabi-v7a` | Older 32-bit ARM phones |
| `x86_64` | x86_64 devices/emulators |
| `x86` | x86 devices/emulators |
| `universal` | All architectures (larger file) |

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

APKs will be in `app/build/outputs/apk/`

## How It Works

1. QEMU binaries are extracted from Termux packages on first launch
2. VMs run using TCG (software emulation) - no KVM required
3. Display is served via VNC to the built-in viewer
4. Uses Android's linker to bypass noexec restrictions on /data

## Performance Tips

- Use lightweight Linux distros (Alpine, TinyCore) for best experience
- Windows XP runs better than newer Windows versions
- Allocate only the memory you need
- Single CPU mode is often faster than multi-core on TCG

## License

MIT
