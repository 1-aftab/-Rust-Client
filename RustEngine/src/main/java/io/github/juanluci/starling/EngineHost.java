package io.github.juanluci.starling;

import android.content.Context;
import android.content.Intent;

import java.io.File;

/**
 * Rust Client: what ties the engine's activity code to the launcher that hosts it. Upstream
 * Starling is an app of its own; here it is one of two editions inside another app, so the
 * places where it assumed to own the whole app (its files folder, the activity that plays, its
 * shared folder, its version) are answered here instead.
 */
public final class EngineHost {
    /** The Starling release the engine library is built from (RustEngine/engine.lock). */
    public static final String ENGINE_VERSION = "0.8.0";

    /** The game library's file name, as System.loadLibrary and NativeActivity find it. */
    public static final String LIBRARY = "libhorizon_android.so";

    /** The folder of the engine's own files inside the app's: android.rs joins the same name. */
    private static final String PRIVATE_FOLDER = "rust-engine";

    /** The game folder's name inside the app's external files. */
    private static final String GAME_FOLDER = "rust";

    private EngineHost() {
    }

    /**
     * The engine's own files (prepared resources, caches, import inbox), apart from the host
     * launcher's: the same folder android.rs `main` uses as HORIZON_PRIVATE.
     */
    public static File filesDir(Context context) {
        File folder = new File(context.getFilesDir(), PRIVATE_FOLDER);
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        return folder;
    }

    /**
     * The game folder (worlds, resource packs, shader packs, options): the app's own external
     * files, where a file manager reaches it. Null when the external storage is not there; the
     * engine then keeps the game folder in its own files.
     */
    public static File gameFolder(Context context) {
        File external = context.getExternalFilesDir(null);
        return external == null ? null : new File(external, GAME_FOLDER);
    }

    /** Whether this APK carries the engine library for the device's architecture. */
    public static boolean isAvailable(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, LIBRARY).isFile();
    }

    /**
     * The activity that plays. The host names its own subclass of GameActivity in the string
     * resource rust_engine_game_activity, which overrides the default of this module.
     */
    public static Intent gameIntent(Context context) {
        return new Intent().setClassName(context,
                context.getString(R.string.rust_engine_game_activity));
    }
}
