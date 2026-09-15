# stand up for Mac — preview

## Choose the correct ZIP

- `stand-up-macos-arm64.zip`: Apple Silicon (M1/M2/M3/M4 and later).
- `stand-up-macos-x86_64.zip`: Intel Mac.

Open Apple menu → About This Mac to see your chip and macOS version.
The Qt runtime requires macOS 13 or newer. Builds are smoke-tested on macOS 15;
older supported macOS versions have not been tested on a physical device.

## Open the app

1. Extract the ZIP using Finder (do not separate files inside the .app bundle).
2. Drag `stand up.app` into Applications, then open it.
3. This personal preview has ad-hoc signing only, not an Apple Developer ID signature
   or Apple notarization. If macOS blocks it, verify that you downloaded it from this
   repository, attempt to open it, then use System Settings → Privacy & Security →
   Open Anyway if offered. Do not disable Gatekeeper globally.
4. If macOS reports malware, damage, or offers no override, stop and share the exact
   message so it can be investigated; do not bypass it blindly.

Apple instructions: https://support.apple.com/en-us/102445

The application does not require Python to be installed. Background music must be
added from local files. The timer alert repeats until paused. Mini-window pinning is
optional; behavior over full-screen apps/Spaces may differ from Windows.

This is a Mac preview. Audio-device behavior and Finder/Gatekeeper opening on the
recipient's Mac still need to be checked. See THIRD_PARTY_NOTICES.md for icon notices;
this is not a complete audit of the bundled runtime's licenses.

The Linux package uses the PySide6/Qt runtime and includes the built-in music, alert sound, Fluent icon notices, and third-party notices. It is an x86_64 Ubuntu 24.04 preview; other distributions are not yet physically tested.
