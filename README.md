# Rust Client

**Rust Client is an unofficial modified version of [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2).**
It is not affiliated with or endorsed by the Zalith Launcher developers. Please do not report Rust Client problems to them.

Rust Client is an Android launcher that aims to offer two independent Minecraft environments in one app:

| Edition | Engine | Status |
|---|---|---|
| **Java Edition** | The Zalith Launcher 2 launch pipeline (JVM + LWJGL + renderer plugins), unchanged | Working (inherited from upstream) |
| **Rust Edition** | A separate Rust-based client, loaded as a native library | **Not implemented.** The engine source is not in this repository yet; see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |

Nothing in the app is presented as a Rust Edition feature until the engine is actually integrated.

## Building

Same as upstream: JDK 21, Android SDK/NDK, a Rust toolchain with the four Android targets and `cargo-ndk`
(needed by the existing `PetLib` module).

```
./gradlew ZalithLauncher:assembleDebug -Darch=arm64
```

The GitHub Actions workflow in `.github/workflows` builds debug APKs on every push.

Things you must supply yourself before publishing (they are upstream's and are not in this repository):

- **Release signing key.** `releaseBuild` still points at upstream's keystore, whose passwords are private. Debug builds use the bundled debug keystore.
- **Microsoft OAuth client id** (`oauth_client_id`), required for Microsoft account login.
- **CurseForge API key** (`curseforge_api_key`), required for CurseForge downloads.

Branding and identity are configured in [`ZalithLauncher/gradle.properties`](ZalithLauncher/gradle.properties).

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): architecture of the code base, how Java Edition is launched, how the controls work, and the Rust Edition integration plan.
- Upstream READMEs, kept unmodified: [简体中文](README_ZH_CN.md) · [English](README_EN_US.md) · [繁體中文](README_ZH_TW.md). They include the full table of third-party libraries and licenses.

## License and attribution

Rust Client is licensed under the **[GPL-3.0 license](LICENSE)**, the same as Zalith Launcher 2.

Zalith Launcher 2 is Copyright (C) 2025-2026 MovTery and contributors. Upstream adds these terms under GPLv3 section 7, and they apply to Rust Client and to anything derived from it:

1. A modified version must not use the name "ZalithLauncher" or the abbreviation "ZL", or a confusingly similar name, and must clearly state on its startup screen or main interface that it is an unofficial modified version. Rust Client shows this notice on the home screen and in *Settings → About*; do not remove it.
2. The copyright notices displayed by the program must not be removed.

Source file headers, the in-app acknowledgements and the third-party library list are preserved from upstream.
