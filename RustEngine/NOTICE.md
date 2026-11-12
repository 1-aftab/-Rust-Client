# RustEngine — the Rust Edition's engine

This module packages **Starling** as the "Rust Edition" of Rust Client.

| | |
|---|---|
| Project | Starling — <https://github.com/JuanLuci/starling> |
| Version | see [`engine.lock`](engine.lock) (tag and commit) |
| Licence | MIT — Copyright (c) 2026 Ludovic Marie and the Horizon contributors ([`LICENSE-STARLING`](LICENSE-STARLING)) |
| Third parties | [`THIRD_PARTY_NOTICES-STARLING.md`](THIRD_PARTY_NOTICES-STARLING.md), [`licenses-starling/`](licenses-starling) |

Starling is not affiliated with Rust Client, with Zalith Launcher, or with Mojang / Microsoft.
It ships no Minecraft assets: the game downloads them from Mojang on the device at first start.

## What is here

- `engine.lock` — the exact upstream commit the engine is built from. The source is **not**
  vendored; `tools/build_engine.py` fetches it.
- `patches/` — Rust Client's changes to the engine source, applied on top of that commit
  (host on-screen controls, private folder name). Same licence as Starling (MIT).
- `tools/build_engine.py` — fetches, patches and builds the engine into `prebuilt/`
  (git-ignored). Needs Rust, cargo-ndk, the Android NDK and a JDK; run by the `engine` job of
  `.github/workflows/build.yml`.
- `src/main/java/io/github/juanluci/starling/` — Starling's Android Java glue
  (`packaging/android/java`), adapted to live inside a host app. Changed places are marked
  `Rust Client:`. The package name must stay: the native library calls into it by name.
- `EngineHost.java` — new; the host launcher's entry point.

Without `prebuilt/` the module still builds; the app then reports that the Rust Edition is not
included.

## Licence compatibility

MIT code may be combined into a GPL-3.0 work. The combined APK is distributed under GPL-3.0;
Starling's files keep their MIT notice, reproduced in the files listed above.

## Built-in mods

The engine of the APK is compiled with the mods listed in `tools/builtin-mods.txt`, fetched at
build time from the mod registry https://github.com/horizon-rust/horizon-mods, where each
mod's page names its authors, source and licence: Zoom, Minimap, Recipes, FPS Counter and
Keystrokes (Horizon's official mods, MIT), AppleSkin (MIT), Eating Animation (MIT), Fullbright
(MIT), The Basic HUD (MIT) and MouseTweaks (BSD-3-Clause). They are unmodified.
