#!/usr/bin/env python3
"""Build the Rust Edition engine for Rust Client.

Fetches the upstream Starling source pinned in ../engine.lock, applies ../patches/*.patch and
cross-compiles it with upstream's own recipe (packaging/android/build_apk.py), stopping before
upstream would package an APK of its own. What the launcher needs is left in ../prebuilt/:

    prebuilt/jniLibs/<abi>/libhorizon_android.so   the game (a NativeActivity library)
    prebuilt/jniLibs/<abi>/libstarling_java.so     launchers of the Java runtime the engine
    prebuilt/jniLibs/<abi>/libstarling_javac.so    fetches to prepare the game files on the phone
    prebuilt/assets/prepare-kit.zip                what that preparation runs
    prebuilt/assets/lowbudget.zip                  the skin of the engine's set-up screens
    prebuilt/assets/StarlingVisuals.zip            the engine's built-in shader pack
    prebuilt/assets/diagnostic-fonts/              fonts of the diagnostics window
    prebuilt/engine.json                           what was built

Nothing of Minecraft is packaged: the engine downloads the game files from Mojang on the phone.
(To compile its data exporters, upstream's recipe downloads the pinned Minecraft jars on the
build machine into an ignored cache; they are only compiled against.)

Needs: git, a Rust toolchain with the Android target of the ABI, cargo-ndk, the Android NDK
(ANDROID_NDK_HOME; upstream pins 28.2.13676358) and JDK 25 on the PATH.

upstream's libc++_shared.so is deliberately not taken: the launcher already ships the one of its
own NDK (APP_STL := c++_shared), and an APK holds only one.
"""

import argparse
import importlib.util
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

MODULE = Path(__file__).resolve().parents[1]
SOURCE = MODULE / ".engine-src"
PREBUILT = MODULE / "prebuilt"
PATCHES = MODULE / "patches"


def run(*args, cwd=None):
    print("+", " ".join(str(a) for a in args), flush=True)
    subprocess.run([str(a) for a in args], check=True, cwd=cwd)


def lock():
    """engine.lock as a dict."""
    pins = {}
    for line in (MODULE / "engine.lock").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            name, _, value = line.partition("=")
            pins[name.strip()] = value.strip()
    return pins


def fetch(pins):
    """The pinned commit in SOURCE, as upstream committed it, with patches/ applied."""
    commit = pins["commit"]
    if not (SOURCE / ".git").is_dir():
        SOURCE.mkdir(parents=True, exist_ok=True)
        run("git", "init", "-q", cwd=SOURCE)
        run("git", "remote", "add", "origin", pins["repository"], cwd=SOURCE)
    have = subprocess.run(
        ["git", "cat-file", "-e", commit + "^{commit}"], cwd=SOURCE,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode == 0
    if not have:
        run("git", "fetch", "-q", "--depth", "1", "origin", commit, cwd=SOURCE)
    # Back to the commit as it is (a patch applied by an earlier run goes); what Git ignores
    # (target/, the exporters' cache) stays, so a rebuild is incremental.
    run("git", "checkout", "-q", "-f", commit, cwd=SOURCE)
    run("git", "clean", "-q", "-fd", cwd=SOURCE)
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=SOURCE, text=True).strip()
    if head != commit:
        sys.exit(f"build_engine: fetched {head}, engine.lock pins {commit}")
    patches = sorted(PATCHES.glob("*.patch"))
    for patch in patches:
        run("git", "apply", "--index", "--whitespace=nowarn", patch, cwd=SOURCE)
    rebrand()
    return [p.name for p in patches]


def rebrand():
    """Every text the engine shows names the host launcher, which is what the player sees and
    installed: the translations, and the texts written in the Rust sources. Only string
    literals are touched (names of types, modules, files and environment variables stay), so
    the sources compile as before."""
    import json
    import re
    # Not inside a header, path or identifier-like word ("X-Horizon", "Starling/1.0").
    brand = re.compile(r"(?<![-_/\w])(?:Starling|Horizon)(?: Android)?(?![-_/\w])")

    def named(text):
        return brand.sub("Rust Client", text).replace("Starling/{", "RustClient/{")

    for path in sorted((SOURCE / "crates/horizon-client/assets/horizon/lang").glob("*.json")):
        texts = json.loads(path.read_text(encoding="utf-8"))
        for key, text in texts.items():
            if isinstance(text, str):
                texts[key] = named(text)
        path.write_text(json.dumps(texts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    changed = 0
    # The catalog is the desktop launcher's mod browser, compiled into the game from there.
    sources = sorted((SOURCE / "crates").rglob("*.rs")) + sorted(
        (SOURCE / "launcher/src").rglob("browser*/**/*.rs")
    ) + sorted((SOURCE / "launcher/src").glob("browser*.rs"))
    for path in sources:
        source = path.read_text(encoding="utf-8")
        if not brand.search(source):
            continue
        swept = sweep_rust(source, named)
        # Not a text: what the mod registry's own entries say their author is.
        swept = swept.replace('== ["Rust Client"]', '== ["Horizon"]')
        if swept != source:
            path.write_text(swept, encoding="utf-8")
            changed += 1
    print(f"build_engine: texts renamed in {changed} source files", flush=True)


def sweep_rust(source, named):
    """`source` with `named` applied to the contents of its string literals. A small lexer:
    comments, string and raw string literals, character literals and lifetimes."""
    import re
    raw = re.compile(r'b?r(#*)"')
    char = re.compile(r"'(?:\\(?:u\{[0-9a-fA-F_]+\}|x[0-9a-fA-F]{2}|.)|[^\\'\n])'")
    out = []
    i, n = 0, len(source)
    while i < n:
        c = source[i]
        if source.startswith("//", i):
            j = source.find("\n", i)
            j = n if j < 0 else j
            out.append(source[i:j])
            i = j
        elif source.startswith("/*", i):
            j = source.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append(source[i:j])
            i = j
        elif c in "br" and (i == 0 or not (source[i - 1].isalnum() or source[i - 1] == "_")) \
                and raw.match(source, i):
            m = raw.match(source, i)
            end = '"' + m.group(1)
            j = source.find(end, m.end())
            j = n if j < 0 else j
            out.append(source[i:m.end()] + named(source[m.end():j]))
            i = j
        elif c == '"':
            j = i + 1
            while j < n and source[j] != '"':
                j += 2 if source[j] == "\\" else 1
            out.append('"' + named(source[i + 1:j]))
            i = j
            if i < n:
                out.append('"')
                i += 1
        elif c == "'":
            m = char.match(source, i)
            j = m.end() if m else i + 1
            out.append(source[i:j])
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def upstream():
    """upstream's packaging/android/build_apk.py as a module."""
    spec = importlib.util.spec_from_file_location(
        "build_apk", SOURCE / "packaging" / "android" / "build_apk.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--abi", default="arm64-v8a",
                        choices=("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
    parser.add_argument("--profile", default="quick",
                        help="Cargo profile of upstream: quick (default), fast or release")
    parser.add_argument("--fetch-only", action="store_true",
                        help="fetch and patch the source, build nothing")
    args = parser.parse_args()

    pins = lock()
    patches = fetch(pins)
    if args.fetch_only:
        return

    build_apk = upstream()
    ndk = os.environ.get("ANDROID_NDK_HOME")
    if not ndk or not Path(ndk).is_dir():
        sys.exit("build_engine: set ANDROID_NDK_HOME to the Android NDK (upstream pins "
                 + build_apk.NDK + ")")
    ndk = Path(ndk)
    sdk = build_apk.sdk_dir()
    triple = build_apk.TARGETS[args.abi][0]

    # 1. The game library.
    build_apk.build_library(args.profile, ndk, sdk, args.abi)
    profile_dir = "debug" if args.profile == "dev" else args.profile
    built = SOURCE / "target" / triple / profile_dir / f"lib{build_apk.LIBRARY}.so"
    if not built.is_file():
        sys.exit(f"build_engine: {built} not built")

    out = SOURCE / "target" / "android"
    out.mkdir(parents=True, exist_ok=True)

    # 2. The assets (compiles the data exporters on this machine) and the Java launchers.
    assets = build_apk.assets(out)
    launchers = build_apk.build_launchers(ndk, out, args.abi)

    # 3. What the launcher packages.
    libs = PREBUILT / "jniLibs" / args.abi
    shutil.rmtree(libs, ignore_errors=True)
    libs.mkdir(parents=True)
    run(build_apk.ndk_tool(ndk, "llvm-strip"), "--strip-unneeded",
        "-o", libs / f"lib{build_apk.LIBRARY}.so", built)
    for name, path in sorted(launchers.items()):
        if name == "libc++_shared.so":
            continue  # the launcher's own (see the top of this file)
        shutil.copyfile(path, libs / name)
    shutil.rmtree(PREBUILT / "assets", ignore_errors=True)
    shutil.copytree(assets, PREBUILT / "assets")

    (PREBUILT / "engine.json").write_text(json.dumps({
        "engine": "Starling",
        "version": build_apk.VERSION_NAME,
        "repository": pins["repository"],
        "commit": pins["commit"],
        "patches": patches,
        "profile": args.profile,
        "abi": args.abi,
    }, indent=2) + "\n", encoding="utf-8")
    for path in sorted(p for p in PREBUILT.rglob("*") if p.is_file()):
        print(f"{path.stat().st_size:>12}  {path.relative_to(MODULE)}")


if __name__ == "__main__":
    main()
