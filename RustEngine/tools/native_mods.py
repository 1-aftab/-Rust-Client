#!/usr/bin/env python3
"""The native mods of a build request, fetched from the mod registry as bundles.

    native_mods.py REQUEST OUT

REQUEST is a list of mod ids, each optionally with a version
(`zoom@1.2.0 minimap`), separated by spaces, commas or new lines. Each must be a native mod
(`kind = "native-mod"`) of the registry that is not taken down; its `.hnmod` is downloaded from
the registry's release, checked against the SHA-256 the registry records, and unpacked. OUT gets
one folder per mod (its horizon-mixins bundle) and `bundles.txt`, their paths in order, which
build_engine.py hands to the engine's native_build.sh. Rust Client's own changes to a mod
(mod_tweaks.py) are made after its download was checked.

Nothing a requester writes is executed or used as a path: an id must match ID and be an entry
of the registry.
"""
import hashlib
import io
import pathlib
import re
import subprocess
import sys
import tempfile
import tomllib
import urllib.request
import zipfile

import mod_tweaks

REGISTRY = "https://github.com/horizon-rust/horizon-mods"
ID = re.compile(r"[a-z0-9][a-z0-9_-]{0,40}")
VERSION = re.compile(r"[0-9A-Za-z][0-9A-Za-z.+_-]{0,40}")
MOST = 40
LARGEST = 64 * 1024 * 1024


def fail(text):
    print(f"::error::native_mods: {text}")
    sys.exit(f"native_mods: {text}")


def parse(request):
    """The request's mods: {id: version or None}."""
    wanted = {}
    for word in re.split(r"[\s,]+", request.strip()):
        if not word:
            continue
        name, _, version = word.partition("@")
        if not ID.fullmatch(name):
            fail(f"'{word[:60]}' is not a mod id")
        if version and not VERSION.fullmatch(version):
            fail(f"'{word[:60]}' has no good version")
        wanted[name] = version or None
    if not wanted:
        fail("the request names no mod")
    if len(wanted) > MOST:
        fail(f"at most {MOST} mods in one build")
    return wanted


def unpack(data, name, out):
    """The bundle of `.hnmod` bytes (its mixins/<id>/) into out/<id>."""
    bundle = out / name
    prefix = f"mixins/{name}/"
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        size = sum(i.file_size for i in archive.infolist())
        if size > 4 * LARGEST:
            fail(f"{name} unpacks to too much")
        for info in archive.infolist():
            path = info.filename
            if info.is_dir() or not path.startswith(prefix):
                continue
            rest = pathlib.PurePosixPath(path[len(prefix):])
            if rest.is_absolute() or ".." in rest.parts or (info.external_attr >> 16) & 0o170000 == 0o120000:
                fail(f"{name} has a bad path: {path}")
            target = bundle.joinpath(*rest.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(info))
    if not (bundle / "horizon-mixin.toml").is_file():
        fail(f"{name}'s file has no {prefix}horizon-mixin.toml")
    return bundle


def main():
    request, out = sys.argv[1], pathlib.Path(sys.argv[2])
    wanted = parse(request)
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["git", "clone", "-q", "--depth", "1", REGISTRY, tmp], check=True)
        registry = pathlib.Path(tmp)
        revoked = tomllib.loads((registry / "revoked.toml").read_text()).get("revoked", [])
        bundles = []
        for name in sorted(wanted):
            entry = registry / "mods" / f"{name}.toml"
            if not entry.is_file():
                fail(f"the registry has no mod '{name}'")
            mod = tomllib.loads(entry.read_text())
            if mod.get("kind") != "native-mod":
                fail(f"'{name}' is not a native mod (it is a {mod.get('kind')})")
            versions = mod.get("versions", [])
            if wanted[name]:
                versions = [v for v in versions if v.get("version") == wanted[name]]
            if not versions:
                fail(f"the registry has no version {wanted[name]} of '{name}'")
            version = versions[0]
            for gone in revoked:
                if gone.get("id") == name and gone.get("sha256") in (None, version["sha256"]):
                    fail(f"'{name}' was taken down: {gone.get('reason', '')}")
            url = f"{REGISTRY}/releases/download/{name}-{version['version']}/{version['file']}"
            print(f"native_mods: {name} {version['version']} from {url}", file=sys.stderr)
            with urllib.request.urlopen(url, timeout=120) as response:
                data = response.read(LARGEST + 1)
            if len(data) > LARGEST:
                fail(f"{name} is too big")
            if hashlib.sha256(data).hexdigest() != version["sha256"]:
                fail(f"{name}'s download does not match the registry's SHA-256")
            bundle = unpack(data, name, out)
            mod_tweaks.apply(name, bundle)
            bundles.append(bundle)
    (out / "bundles.txt").write_text("".join(f"{b.resolve()}\n" for b in bundles))


if __name__ == "__main__":
    main()
