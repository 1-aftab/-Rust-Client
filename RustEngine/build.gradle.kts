/*
 * Rust Client: the Rust Edition engine.
 *
 * The engine is Starling 0.8.0 (https://github.com/JuanLuci/starling, MIT), a fork of Horizon:
 * a Rust rewrite of Minecraft Java Edition 26.3 that runs natively on Android (Vulkan through
 * wgpu, no JVM while playing). See NOTICE.md.
 *
 * This module holds
 *   - the engine's Android activity code (src/main/java, adapted from upstream's
 *     packaging/android/java), and
 *   - the build recipe of the native library: engine.lock pins the upstream commit,
 *     patches/ holds Rust Client's changes to it, tools/build_engine.py fetches, patches and
 *     cross-compiles it.
 *
 * The native build is NOT part of a normal Gradle build: it compiles about forty crates and
 * takes a long time. It is run on its own (`./gradlew :RustEngine:buildRustEngine`, or the
 * "engine" job of .github/workflows/build.yml) and leaves its result in prebuilt/, which this
 * module packages when it is there. Without prebuilt/ the app still builds; the launcher then
 * reports that the Rust Edition is not included (EngineHost.isAvailable).
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.juanluci.starling"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            // libhorizon_android.so, libstarling_java.so, libstarling_javac.so per ABI
            jniLibs.srcDir(file("prebuilt/jniLibs"))
            // prepare-kit.zip, lowbudget.zip, StarlingVisuals.zip, diagnostic-fonts/
            assets.srcDir(file("prebuilt/assets"))
        }
    }
}

/**
 * Fetches the pinned engine source, applies patches/ and cross-compiles it into prebuilt/.
 * Needs: git, python3, a Rust toolchain with the Android target, cargo-ndk, the Android NDK
 * (ANDROID_NDK_HOME) and JDK 25 on the PATH (the engine compiles its data exporters with it).
 *
 *   ./gradlew :RustEngine:buildRustEngine                    # arm64-v8a, the "quick" profile
 *   ./gradlew :RustEngine:buildRustEngine -PrustEngineProfile=fast
 */
tasks.register<Exec>("buildRustEngine") {
    group = "build"
    description = "Fetches, patches and cross-compiles the Rust Edition engine into prebuilt/."
    workingDir = projectDir
    commandLine(
        "python3", "tools/build_engine.py",
        "--abi", (findProperty("rustEngineAbi") as String?) ?: "arm64-v8a",
        "--profile", (findProperty("rustEngineProfile") as String?) ?: "quick",
    )
}
