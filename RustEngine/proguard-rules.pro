# Rust Client: the engine's native library calls these classes and methods by name through JNI
# (crates/horizon-client/src/android.rs, `with_activity`), and Android's NativeActivity loads the
# library named in the manifest. Nothing here may be renamed or removed.
-keep class io.github.juanluci.starling.** { *; }
