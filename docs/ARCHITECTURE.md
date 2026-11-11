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

## 6. Rust Edition: current state

**The Rust Minecraft engine is not in this repository.** The only Rust code is `PetLib/rust`
(about 4,900 lines: a skinned player-model renderer for the home screen). There is no Minecraft
client, no world/chunk/network/render engine, no submodule and no prebuilt engine library.

Until the engine source is added, no Rust Edition code can be written honestly: the JNI surface,
the surface hand-off, the input events, the asset format and the on-disk layout are all defined by
the engine. Nothing in the app claims Rust Edition support yet.

### What is needed

1. The engine source as a Cargo workspace (a Git URL to add as a submodule, or the files themselves).
   An APK or prebuilt `.so` is not a substitute: without source the entry points and input ABI are unknown.
2. Its license, and the licenses of its dependencies, to confirm it can be combined with GPL-3.0 code.
3. Answers to the questions below, or source clear enough to answer them:
   - Graphics backend (wgpu / Vulkan / GLES) and how it expects a window: `ANativeWindow*` from a
     `Surface`, or does it assume `winit` + `NativeActivity`/`GameActivity`?
   - Entry point: a library with init/resize/frame/shutdown functions, or a `main` with its own event loop?
   - Input model: raw key/mouse events, or abstract actions (move vector, jump, attack)?
   - Where it gets game assets for 26.3 (reads a vanilla client jar? own format?) and how it authenticates.
   - Whether it supports resource packs, mods/plugins, shaders. These tabs will only be built for
     features the engine really has.

### Planned integration (subject to the answers above)

- **Build**: new Gradle module `RustEngine`, mirroring `PetLib`: `cargo ndk` builds a `cdylib`
  into the module's `jniLibs`. arm64-v8a first.
- **Process**: run in the existing `:game` process model. A new `RustGameActivity` (or a third
  `HandlerType` in `VMActivity`, if the engine fits `AbstractHandler`) so a native crash cannot take
  down the launcher, and so the JVM-specific setup in `Launcher` is never touched.
- **JNI surface** (`RustEngineBridge.kt` ↔ `jni.rs`): `create(config)`, `surfaceCreated(Surface)`,
  `surfaceChanged(w, h)`, `surfaceDestroyed()`, `pause()`, `resume()`, `destroy()`, plus input calls.
  Rendering runs on an engine-owned thread; surface loss on backgrounding must drop the swapchain
  and recreate it on resume.
- **Controls**: reuse `LayerController` (`ControlBoxLayout`, the editor, the JSON format) unchanged.
  Add a `RustInputAdapter` as a second sink beside `lwjglEvent`: it receives the same
  `ClickEvent`s, joystick directions, cursor deltas and gamepad events and translates them to the
  engine's input calls. Java Edition's path is not modified.
- **Storage**: a separate root `<filesDir>/rust/` with `engine/`, `worlds/`, `resourcepacks/`,
  `config/` (and `mods/`, `shaders/` only if supported). Accounts and launcher settings stay shared.
  Java Edition paths do not move, so existing installs keep working.
- **UI**: an edition selector on the home screen, backed by a persisted `GameEdition` setting.
  It is added together with the engine, not before, so the app never shows an edition that cannot start.

### Known limitations and risks

- If the engine is built around `winit`'s `android-activity`, it expects to own the activity.
  Embedding it under a Compose overlay (needed for Zalith-style controls) then requires either
  engine changes or hosting it in a `GameActivity` subclass with the overlay added on top.
- Vulkan-only engines will not run on devices without a usable Vulkan driver; a GLES fallback is the engine's job.
- Java mods, shader packs and Java-format resource packs cannot be assumed to work.
- A second engine adds APK size per ABI; shared files (accounts, settings) are not duplicated.
- 32-bit ABIs may not be supportable by the engine; Rust Edition may be arm64-only while Java Edition keeps all four.
