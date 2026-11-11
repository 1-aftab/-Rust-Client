package io.github.juanluci.starling;

/**
 * Rust Client: the engine's mod catalog (mods, resource packs, shader packs) for a screen of the
 * host launcher's own. The game asks for that screen with StarlingActivity.openCatalog; the
 * screen then searches, installs and removes through these calls, which block: call them off
 * the UI thread. They answer in JSON (android.rs, host_catalog).
 */
public final class EngineCatalog {
    public static final int SHELF_MODS = 0;
    public static final int SHELF_RESOURCE_PACKS = 1;
    public static final int SHELF_SHADER_PACKS = 2;

    private EngineCatalog() {
    }

    /** One page: {"rows":[{handle,id,title,summary,author,downloads,icon,page,installed,native,caution}],"more":bool} or {"error":text}. */
    public static String search(int shelf, String text, boolean everything, int offset) {
        return nativeSearch(shelf, text, everything, offset);
    }

    /** The ids of the installed projects, as a JSON list. */
    public static String installed() {
        return nativeInstalled();
    }

    /** Installs the project a search named handle: {"done":"native-mod"|"content-mod"|"resource-pack"|"shader-pack"} or {"error":text}. */
    public static String install(int handle, boolean everything) {
        return nativeInstall(handle, everything);
    }

    /** The running install: its fraction done, a tab, its line. */
    public static String progress() {
        return nativeProgress();
    }

    /** Removes what was installed for the project a search named handle: {} or {"error":text}. */
    public static String remove(int handle) {
        return nativeRemove(handle);
    }

    private static native String nativeSearch(int shelf, String text, boolean everything, int offset);

    private static native String nativeInstalled();

    private static native String nativeInstall(int handle, boolean everything);

    private static native String nativeProgress();

    private static native String nativeRemove(int handle);
}
