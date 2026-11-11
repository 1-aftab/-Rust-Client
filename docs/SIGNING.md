# Signing Rust Client by hand (Termux)

Rust Client does not ship a release keystore and the CI does not sign release
builds. A `Release` build therefore produces
`RustClient-<version>-<abi>-unsigned.apk`. Android refuses to install an
unsigned APK, so it has to be aligned and signed once before installing.

> `Debug` builds (the ones made on every push) are signed with the public debug
> key in `ZalithLauncher/zalith_launcher_debug.jks` and install directly. They
> use the application id `…rustclient.debug`, so they can live next to a
> release build.

## 1. Get the unsigned APK

GitHub → Actions → **Build APK** → *Run workflow* → variant `Release`.
When it finishes, download the artifact `release apk (arm64)` (a zip) and
extract the `.apk` to your phone, for example into `Download/`.

## 2. Install the tools in Termux

```sh
pkg update
pkg install apksigner openjdk-17 aapt
termux-setup-storage        # lets Termux read ~/storage/downloads
```

`apksigner` provides `apksigner`; `openjdk-17` provides `keytool`;
`aapt` provides `zipalign`.

## 3. Create your own key (only once)

```sh
mkdir -p ~/keys
keytool -genkeypair -v \
  -keystore ~/keys/rustclient.jks \
  -alias rust_client \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep `~/keys/rustclient.jks` and its password safe. Every later update has to
be signed with the **same** key, otherwise Android will refuse to install it
over the previous version.

## 4. Align, sign, verify

```sh
cd ~/storage/downloads
IN=RustClient-2.8.2-arm64-v8a-unsigned.apk
OUT=RustClient-2.8.2-arm64-v8a.apk

zipalign -p -f 4 "$IN" aligned.apk
apksigner sign --ks ~/keys/rustclient.jks --ks-key-alias rust_client \
  --out "$OUT" aligned.apk
apksigner verify --verbose "$OUT"
rm aligned.apk
```

Always run `zipalign` **before** `apksigner`; aligning afterwards breaks the
signature. The APK is built with uncompressed, page-aligned native libraries
disabled (`useLegacyPackaging = true`), so `-p` is harmless and kept for
safety.

Then open `$OUT` with a file manager to install it.

## Optional: let Gradle sign instead

When building on a computer you can let Gradle sign the release build by
exporting these variables before running `./gradlew ZalithLauncher:assembleRelease`:

| Variable | Meaning |
| --- | --- |
| `RELEASE_STORE_FILE` | absolute path of your `.jks` |
| `STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias (default `rust_client`) |
| `KEY_PASSWORD` | key password |

If `RELEASE_STORE_FILE` is not set, the build stays unsigned.
