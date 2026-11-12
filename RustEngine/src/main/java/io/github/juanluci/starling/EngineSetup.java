package io.github.juanluci.starling;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Rust Client: the engine's own download and preparation of the game files, run without the
 * game so that the host launcher shows it in a screen of its own (android.rs, host_setup).
 *
 * It is the same work the engine's set-up screen does: Minecraft's official files fetched from
 * Mojang, a temporary Java runtime, then the preparer over them. Use it in a process the game
 * never runs in: the engine keeps what init gives it for the life of the process.
 */
public final class EngineSetup {
    /** The assets the preparer reads through the engine's platform hooks. */
    private static final String[] ASSETS = {"prepare-kit.zip"};

    private static boolean initialized;

    private EngineSetup() {
    }

    /** The folder the game reads its resources from; it is there once they are prepared. */
    public static File resources(Context context) {
        return new File(EngineHost.filesDir(context), "resources");
    }

    /** Whether the game files are prepared: the game then starts straight into its title. */
    public static boolean prepared(Context context) {
        String[] files = resources(context).list();
        return files != null && files.length > 0;
    }

    /**
     * Loads the engine and tells it where its files are. Not on the UI thread: it copies the
     * preparer's kit out of the APK.
     */
    public static synchronized void init(Context context) throws IOException {
        if (initialized) {
            return;
        }
        File assets = new File(EngineHost.filesDir(context), "host-assets");
        if (!assets.isDirectory() && !assets.mkdirs()) {
            throw new IOException("Cannot create " + assets);
        }
        for (String name : ASSETS) {
            File part = new File(assets, name + ".part");
            try (InputStream in = context.getAssets().open(name);
                 OutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            File file = new File(assets, name);
            if (!part.renameTo(file)) {
                throw new IOException("Cannot write " + file);
            }
        }
        try {
            System.loadLibrary("horizon_android");
            nativeInit(EngineHost.filesDir(context).getPath(),
                    context.getApplicationInfo().nativeLibraryDir,
                    assets.getPath());
        } catch (UnsatisfiedLinkError e) {
            throw new IOException("The engine library could not be loaded: " + e.getMessage(), e);
        }
        initialized = true;
    }

    /** Starts, or takes up again, the download and the preparation. */
    public static void start() {
        nativeStart();
    }

    /** Stops the download in progress; what it fetched stays. */
    public static void cancel() {
        nativeCancel();
    }

    /**
     * Where the work is: "stage \t done \t total \t text". Stages: idle, checking, download and
     * java (done and total in bytes), unpacking, preparing (text: the preparer's last line),
     * done, failed (text: the reason).
     */
    public static String status() {
        return nativeStatus();
    }

    private static native void nativeInit(String privateFolder, String libraryFolder,
            String assetsFolder);

    private static native void nativeStart();

    private static native void nativeCancel();

    private static native String nativeStatus();
}
