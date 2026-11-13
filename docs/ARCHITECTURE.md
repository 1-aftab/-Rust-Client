# Rust Client architecture

This document describes the code base Rust Client is built on (Zalith Launcher 2, version 2.8.2),
and the plan for adding the Rust Edition. It is a description of what is in the repository;
sections about the Rust Edition are a plan, not a description of working code.

## 1. Gradle modules

| Module | Type | Purpose |
|---|---|---|
| `ZalithLauncher` | application | The app: UI (Jetpack Compose), accounts, downloads, version management, launch pipeline, native bridge (`src/main/jni`, ndk-build) |
| `LayerController` | library | Control-layout data model, renderer and editor: layers, buttons, joystick, styles, JSON format |
| `InputMap` | library | Key-name ↔ keycode tables (GLFW/LWJGL, Android) |
| `LWJGL`, `LWJGL:patches`, `LWJGL:lwjgl-3.3.3`, `LWJGL:lwjgl-3.4.1` | Java | Patched LWJGL/GLFW classes packed into app assets and put on the game's classpath |
| `Terracotta` | library | LAN multiplayer over EasyTier (VPN service) |
| `PetLib` | library + Rust | Home-screen player-model animation. **Already a Rust `cdylib` built with `cargo-ndk` and called over JNI**; it is the in-repo precedent for the Rust Edition build |
| `ColorPicker`, `CardGrid`, `Guide` | library | UI components |

Build: AGP + Kotlin, `compileSdk 37`, `minSdk 26`, `targetSdk 34`, NDK via ndk-build, JDK 21 in CI.
`-Darch=arm|arm64|x86|x86_64|all` selects the ABI split. CI (`.github/workflows/build.yml`) installs
Rust, the Android targets and `cargo-ndk`, then runs `ZalithLauncher:assemble<Variant>`.

## 2. Android entry points and processes

| Component | Process | Role |
|---|---|---|
| `ZLApplication` | all | Path setup (`PathManager.refreshPaths`), settings (MMKV), crash handling |
| `SplashActivity` | main | Launcher activity; unpacks bundled components (JREs, LWJGL, auth libs), first-run flow |
| `MainActivity` | main | Whole launcher UI: single activity, Navigation 3 back stack (`ui/screens`) |
| `ControlEditorActivity` | main | Standalone control-layout editor |
| `VMActivity` + `GameService` | `:game` | Runs the game. Separate process so the JVM can be killed without taking the launcher down |
| `JvmService` | `:jvm` | Headless JVM for installers (Forge/NeoForge processors) |
| `FileManagerActivity` | `:filemanager` | Built-in file manager |

## 3. How Java Edition is launched

1. `GameLaunchFlow.launch` (main process) runs phases: account check/refresh, file integrity and
   download of missing libraries/assets, mod checks. It ends in `runGame(context, version, account)`.
2. `runGame` starts `GameService` and `VMActivity` in the `:game` process with a `LaunchConfig`.
3. `VMActivity` creates a `TextureView`, and builds a handler: `GameHandler` (Minecraft) or
   `JVMHandler` (plain jar, AWT). Both extend `AbstractHandler`, which owns lifecycle callbacks
   (`onPause`, `onResume`, `onDestroy`, `onGraphicOutput`) and the Compose overlay (`ComposableLayout`).
4. When the surface is available, `GameHandler.execute` hands it to native code
   (`ZLBridge.setupBridgeWindow`), writes `options.txt` overrides (`MCOptions`), then calls
   `GameLauncher.launch`.
5. `GameLauncher` / `Launcher` select the Java runtime (`multirt`), the renderer
   (`game/renderer`: GL4ES, MobileGlues, ANGLE, Zink, ...; plus renderer/driver plugins), set
   environment variables, build the JVM and game arguments (`LaunchArgs`), `dlopen` the JRE and
   bridge libraries, and call `ZLBridge` → `jre_launcher.c` → `JLI_Launch` in-process.
6. Inside the JVM, the patched LWJGL/GLFW talks back to `libpojavexec.so`: `egl_bridge.c` and
   `ctxbridges/` provide the GL/Vulkan surface, `input_bridge_v3.c` provides the input queue.

Game files live in a "game path" (`game/path`), `<externalFilesDir>/.minecraft` by default, with the
standard `versions/`, `libraries/`, `assets/` layout. Per-version isolation puts `mods/`,
`resourcepacks/`, `shaderpacks/`, `saves/` inside the version folder.

## 4. How the touch controls work

- **Data**: a control layout is a JSON file in `<externalFilesDir>/control_layouts`, modelled by
  `LayerController` (`ControlLayout` → `ControlLayer` → `NormalData` buttons / `TextData`,
  plus `JoystickStyle`). Each button carries a list of `ClickEvent(type, key)` where type is
  `Key`, `LauncherEvent`, `SwitchLayer`/`ShowLayer`/`HideLayer` or `SendText`. Positions and sizes
  are stored per widget, so layout, size and visibility are all user-editable (`ControlEditor`).
- **Rendering and hit-testing**: `ControlBoxLayout` (in `LayerController`) draws the layers over the
  game surface and reports presses through an `EventHandler { event, pressed -> }` callback. This
  part knows nothing about Minecraft or LWJGL.
- **Dispatch**: `GameScreen` receives the callback. `Key` events go to `lwjglEvent`, which resolves
  the key name to a GLFW keycode and calls `CallbackBridge.sendKeyPress` / `sendMouseButton`.
  `LauncherEvent`s (IME, menu, scroll) are handled in Kotlin.
- **Joystick**: the joystick widget reports a direction; `ui/control/joystick/_Movement.kt` turns it
  into W/A/S/D key presses.
- **Camera and pointer**: `SwitchableMouseLayout` / `TouchInput` turn drags into cursor deltas
  (`CallbackBridge.sendCursorPos`) while the game has grabbed the pointer, or drive a virtual cursor
  in menus. Optional gyroscope (`GyroscopeReader`).
- **Gamepad**: `ui/control/gamepad` remaps physical controllers to keys/mouse, or passes them to SDL.
- **Sink**: every path ends in `org.lwjgl.glfw.CallbackBridge` natives implemented in
  `input_bridge_v3.c`, which feed GLFW callbacks inside the JVM.

The useful property for Rust Edition: everything above the last bullet is engine-agnostic. Only the
sink (`lwjglEvent`, `CallbackBridge`) is tied to the JVM.

## 5. Storage layout today

| Path | Content |
|---|---|
| `<externalFilesDir>/.minecraft/` | Default game path (users can add others) |
| `<filesDir>/games/` | Java runtimes (`runtimes/`), account skins/capes, `minecraft_versions.json` |
| `<filesDir>/components/` | Unpacked LWJGL, auth libs, Caciocavallo |
| `<filesDir>/settings.json`, MMKV | Launcher settings |
| `<dataDir>/databases/` | Room databases: accounts, game paths |
| `<externalFilesDir>/control_layouts/`, `logs/` | Control layouts, logs |

## 6. Rust Edition

The Rust Edition is [Starling](https://github.com/JuanLuci/starling) (MIT), a Rust rewrite of
Minecraft Java Edition 26.3 that runs natively on Android: no JVM while playing, wgpu on Vulkan.
It is packaged by the `:RustEngine` module. See [`RustEngine/NOTICE.md`](../RustEngine/NOTICE.md).

### What "integrated" means here, precisely

Starling is not a library that draws into a surface handed to it. Its `libhorizon_android.so` is a
complete Android `NativeActivity` application that owns its window, its event loop, its menus and
its account screens. Rust Client therefore hosts it rather than embeds it:

| Part | Who provides it |
|---|---|
| Edition selector, launch button, folder shortcuts | Rust Client (Compose) |
| Game window, rendering, title screen, menus, inventory, settings | Starling, unchanged |
| On-screen controls, mouse pointer, game menu | Rust Client: the launcher's control layout and menu |
| Player name and skin | the launcher's current account, handed over at every start |

### Pieces

- `RustEngine/engine.lock` pins the upstream commit; `tools/build_engine.py` fetches it, applies
  `patches/`, builds `libhorizon_android.so` and the engine's asset kits into `prebuilt/`
  (git-ignored). The source is not vendored.
- `RustEngine/src/main/java/io/github/juanluci/starling/` is Starling's Java glue, adapted
  (`Rust Client:` comments). The package cannot be renamed: the native code calls it by name.
  `EngineHost` is the launcher's entry point (`isAvailable`, folders, game intent).
- `game/rust/RustGameActivity` subclasses the engine's activity and runs in the `:rust` process.
  The Java Edition's `:game` process, JVM, LWJGL and renderer plugins are never loaded with it.
- `game/rust/RustEdition` checks that the engine is in the APK and that the device has
  Vulkan 1.3, and starts the game.
- `setting/AllSettings.gameEdition` persists the choice; `GameEditionCard` (home screen) changes
  it; `LauncherScreen.VersionsContent` starts the chosen edition.

### Game resources (first start)

Starling ships no Minecraft files; they are fetched from Mojang and prepared on the device. In
Rust Client this is shown by the launcher, not by the engine: when `<filesDir>/rust-engine/resources`
is missing, the launch button opens `RustSetupActivity` (Compose, process `:rustsetup`). It loads
the engine library without its game and runs the engine's own downloader and preparer through
`EngineSetup` (patch `0002-rust-client-host-setup.patch`), polling it for progress. Once done, the
game starts and loads straight into its title screen. The engine's built-in set-up screen remains
as a fallback if the prepared resources fail to load.

### Accounts and skin

The launcher is the only place accounts and skins are managed. At every start `RustEdition.launch`
writes the current account's name and skin into the engine's files (`horizon-args.txt`,
`<game folder>/horizon-player.txt`, `skins/launcher.png`) and sets `RUST_CLIENT_HOST_ACCOUNTS=1`.
Patch `0003` makes the engine honour that: no account button on its title screen, and its Skin
Customization page keeps only vanilla's rows. The same patch stops the engine drawing its own
on-screen controls; the launcher's control layout is the only one.

### Controls

A `NativeActivity` owns its window's surface, so no view can be drawn over the game in the same
window. `RustControlsOverlay` is a separate, never-focused window above it that renders the
LayerController layout chosen in *Settings → Controls* (the same file the Java Edition uses).

```
LayerController widget ─ ClickEvent ─▶ RustInputAdapter ─▶ StarlingActivity.nativeExternal*
                                         (GLFW name → Android key code, RustKeyMap)
                                                         ─▶ engine patch: queued, replayed as
                                                            keyboard / mouse window events
```

The overlay is shown while the engine reads the host's input (`nativeExternalReady`, polled by
`RustGameActivity`; false while the engine starts and while it is paused) and has two states,
switched by the engine's `capturePointer`:

| State | What the overlay does |
|---|---|
| A world is played (`capturePointer(true)`) | The layout's controls; outside them a drag turns the view, a tap is a right click, a long press holds the left button. |
| A screen of the game (`capturePointer(false)`) | The layout's menu-state controls and the launcher's mouse pointer. Mouse mode *slide*: a touchpad (drag moves the pointer, tap clicks, long press drags, two fingers scroll), sent as mouse events. Mouse mode *click*: every finger is handed to the engine as a touch of its own (`nativeExternalTouch`), so its screens are used directly. |

Over both sit the floating menu button and the game menu (`GameMenu` in `RustControlsOverlay.kt`):
input method (`nativeExternalKeyboard`), control layout picker, the layout editor
(`ControlEditorActivity`; the layout is reloaded on return), opacity and mouse settings, quit.
The layout events "switch IME" and "open game menu" do the same.

Engine side (patches 0004 and 0005): the screens that read touches only (mod catalog, the cards
over the title) take the pointer's left button as a finger; the on-screen keyboard also opens
without the engine's touch mode; the engine's update offer is off; the engine's own screens use
the launcher's dark colour scheme (`horizon-theme` `DARK`).

How the game's screens are touched is one setting (`AllSettings.rustTouchScreen`, on by default),
switched by the round button at the top right of those screens and in the game menu: on, fingers
go to the game as touches where they are and no pointer shows; off, the screen is a touchpad for
the mouse pointer.

The mod catalog (patch 0006) is the launcher's screen, not the engine's: where the game would
open its catalog it calls `StarlingActivity.openCatalog(shelf)`, and `RustGameActivity` shows
`RustCatalogDialog` (Compose, laid out as the launcher's download results). The dialog searches,
installs and removes through `EngineCatalog` (JNI into `android.rs` `host_catalog`, which runs
the engine's own `starling-catalog`), and the game refreshes its pages from the recorded changes
in `poll_catalog`.

`tools/build_engine.py` finally renames the engine in every text it shows (translations and the
string literals of its Rust sources), so the player only ever reads the launcher's name.

Not mapped: the layout event "send text"; gamepad and gyroscope settings of the launcher;
per-version control layouts (the global one is used).

### Storage

| Path | Owner |
|---|---|
| `<externalFilesDir>/.minecraft/` (or custom game paths) | Java Edition |
| `<externalFilesDir>/rust/` — `saves`, `resourcepacks`, `shaderpacks`, `datapacks`, options | Rust Edition game folder |
| `<filesDir>/rust-engine/` — prepared resources, caches, sign-ins | Rust Edition engine |
| control layouts | shared on purpose |

No storage permission is requested for the Rust Edition.

### CI

`.github/workflows/build.yml`: job `engine` builds the arm64 engine once (cached on
`engine.lock`, the patches and the build tool); `build-rust` packages it into the arm64 and
universal APKs; `build` produces arm, x86 and x86_64 APKs without it. Those report "not included"
in the selector.

### Verified and not verified

Verified by GitHub Actions: the patched engine compiles for arm64, the Java glue and the Kotlin
host compile, and all five APKs assemble.

**Not verified: anything at run time.** No build has been started on a device. In particular:
the engine starting inside the host package, the overlay window appearing and receiving touches,
key delivery, and `libc++_shared.so` (the APK carries the NDK 25 copy from the Java Edition's
build, the engine was built with NDK 28).

### Known limitations

- ARM64 only, Vulkan 1.3 only, Minecraft 26.3 only.
- No Java mods; Starling has its own mod formats.
- The engine downloads Minecraft's assets from Mojang at first start and needs network for that.
- CI builds the engine with upstream's `fast` profile, the one its releases use (the whole game
  at opt-level 3). The build needs the swap the engine job adds.

## Look

The launcher has one look in two modes, white and dark (`ui/theme/RustClientColors.kt`); the
colour themes, the dynamic and the custom colour are no longer offered, only the dark mode
setting. Type is Nunito (`res/font`, SIL OFL 1.1, `docs/NUNITO_LICENSE.txt`) and corners are
rounder (`ui/theme/Type.kt`: `AppTypography`, `AppShapes`). The engine's own screens use the dark
palette (patch 0007, `RustEngine/src/main/res/values/rust_engine.xml`). The same patch puts the
built-in shader pack in `shaderpacks/` as `RustClientVisuals.zip`.

The launcher's own arrangement: no title bar; a floating rail at the left side carries the
navigation (`MainScreen.kt`), and the launch card starts beside it (`launcherActionMenuSide`).
Items enter by growing into place while they fade in (`ui/components/_Animated.kt`).

Engine patch 0008: the engine's own touch controls are not offered in its Controls page (the
launcher's are the only ones), and the offer to import another app's worlds is off.

In the Rust Edition's process the settings are read on every start and resume
(`RustGameActivity`: `loadAllSettings`), and the layout editor opens as
`RustControlEditorActivity`: the launcher's editor, translucent, in the game's process and task.

## Rust Edition mods

The Rust engine has no mod loader: a Rust Edition mod is Rust source compiled into the game.
Rust Client compiles nothing for a player. An earlier mod compiler (a GitHub Actions workflow
answering requests from the game) was removed; the mods of the Rust Edition are the built-in
ones below. Patch `0009` turns the engine's Compile card, its build service and the catalog's
native mods off (`SERVICE_SUPPORTED`, `NATIVE_MODS_BUILD`).

## Built-in Rust Edition mods, gyroscope, CI storage

- `RustEngine/tools/builtin-mods.txt` lists registry mods that are compiled into the engine of
  every APK (`build_engine.py` → `build_with_mods`, which runs upstream's `native_build.sh`).
  They are switched on and off in the game under Options > Mods.
- Free placement of mod HUDs: the FPS Counter, Keystrokes and the Minimap are dragged anywhere
  with "Move HUD" in the Rust Edition's game menu (`game/rust/RustHudEditor.kt`), over the running
  game. `RustEngine/tools/mod_tweaks.py` edits the fetched sources of these mods so that the box,
  drawn in its corner, is then moved by engine patch `0010` (`extras_hud::FreeHud`, `MINIMAP_AT`).
  Launcher and engine talk through two files in the mod's settings folder
  (`horizon-mods/.horizon-mod-data/<mod>/`): the engine writes `rc_box` (where the box is drawn,
  as fractions of the screen), the launcher writes `rc_place` ("1 across down" in percent), which
  the engine reads again every few frames. The mods' settings pages also get the buttons
  "Position: Corner/Free", "Across" and "Down".
  HUD+ has its own drag-and-drop editor; the other built-in mods draw no free-standing box.
- LOD Distance has a 32 chunk step before 64 (engine patch `0011`).
- About screen: the credits are folded under the entry "Open-source licences"
  (`AboutInfoScreen.kt`); they stay in the app because the licences require them.
- Gyroscope aiming in the Rust Edition is the launcher's own (`GyroscopeReader`), fed to the
  engine as relative mouse movement in `RustControlsOverlay.kt` while the game holds the
  pointer. Its switch and sensitivity are also in the Rust game menu.
- CI stores nothing as artifacts: APKs are uploaded to the release `latest-build`, and the
  engine passes between jobs through `actions/cache`.

## Rust Client Lite

A second app built from the same source with `-Dvariant=lite` (`RustClientLite-arm64-*.apk`,
application id `io.github.aftab1.rustclient.lite`, so it installs next to Rust Client).

- Only the Rust Edition: the Java runtimes, LWJGL, the Java components and the renderer
  libraries are not packaged (`ZalithLauncher/build.gradle.kts`), and the launcher hides what
  belongs to the Java Edition (`rustOnly()` in `Internal.kt`).
- Its engine is built by `build_engine.py --variant lite`: the mods of
  `RustEngine/tools/builtin-mods-lite.txt`, and `RUST_CLIENT_LITE` set, which makes the engine
  ignore X-Ray and Trigger Bot (`rc_qol.rs`). CI builds it with the `release` profile (thin
  LTO) and falls back to `fast` when that does not fit the runner; `engine.json` in the APK's
  build log says which one was used.
- The home card has Customise HUD (the launcher's `RustHudEditor`) and the shader pack and
  resource pack downloads (the launcher's `RustCatalogDialog`, opened over the started game).
