#!/usr/bin/env python3
"""Rebuild a broken AGP-on-ARM64 release APK into an installable signed APK.

Background: on this ARM64 builder, AGP's packageRelease emits an APK that is
missing AndroidManifest.xml, resources.arsc, res/ and signatures even though
all packaging inputs are correct (verified in intermediates/). This matches a
known AGP 9.x-on-ARM64 packaging failure. Instead of fighting the toolchain,
this script finishes the job deterministically:

1. Takes dex/assets/libs/java-res from packageRelease's APK.
2. Takes AndroidManifest.xml + resources.arsc + res/ from the linked .ap_.
3. Re-zips with install-required alignment
   (resources.arsc stored+4-byte aligned, native libs stored+page aligned).
4. Signs with apksigner.jar (pure Java, ARM-safe) using keystore.properties.
5. Verifies the result.

Usage (from repo root):
    python3 tools/fix-release-apk.py

Requires: keystore.properties next to the wrapper gradle files (see
keystore.properties.example — never commit the real one).
"""
import os
import subprocess
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_BUILD = os.path.join(REPO, "app", "build")
PACKAGE_APK = os.path.join(APP_BUILD, "outputs", "apk", "release", "app-release.apk")
LINKED_AP = os.path.join(
    APP_BUILD, "intermediates", "linked_resources_binary_format",
    "release", "processReleaseResources",
    "linked-resources-binary-format-release.ap_",
)
FINAL_APK = os.path.join(APP_BUILD, "outputs", "apk", "release", "app-release-fixed.apk")
SDK = os.environ.get(
    "ANDROID_SDK_ROOT",
    os.path.expanduser("~/android-sdk"),
)
if "ANDROID_HOME" in os.environ:
    SDK = os.environ["ANDROID_HOME"]
APKSIGNER_JAR = os.path.join(SDK, "build-tools", "36.0.0", "lib", "apksigner.jar")


def load_keystore_props():
    props = {}
    path = os.path.join(REPO, "keystore.properties")
    with open(path) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    for key in ("storeFile", "storePassword", "keyAlias", "keyPassword"):
        if not props.get(key):
            sys.exit(f"keystore.properties: missing {key} (see keystore.properties.example)")
    store = props["storeFile"]
    if not os.path.isabs(store):
        store = os.path.normpath(os.path.join(REPO, "app", store))
    props["storeFile"] = store
    if not os.path.exists(store):
        sys.exit(f"keystore not found: {store}")
    return props


def alignment_of(name):
    if name == "resources.arsc":
        return 4
    if name.endswith(".so"):
        return 4096
    return 1


def build_aligned_package(sources):
    """sources: list of (name, data, compress_type, date_time, external_attr).

    Returns the full zip bytes with alignment padding applied.
    """
    out = bytearray()
    offset = 0
    central = []

    def write_entry(name, data, compress_type, date_time, external_attr):
        nonlocal offset
        nonlocal out
        import struct
        import binascii
        nonlocal central
        # Local file header size BEFORE extra field.
        name_b = name.encode("utf-8")
        base_header = 30 + len(name_b)
        align = alignment_of(name)
        pad = (-(offset + base_header)) % align
        extra = b"\x00" * pad if pad else b""
        header_len = base_header + len(extra)
        if compress_type == zipfile.ZIP_STORED:
            payload = data
            crc = binascii.crc32(data) & 0xFFFFFFFF
        else:
            comp = __import__("zlib").compressobj(6, __import__("zlib").DEFLATED, -15)
            payload = comp.compress(data) + comp.flush()
            crc = binascii.crc32(data) & 0xFFFFFFFF
        # Local header
        out += struct.pack(
            "<IHHHHHIIIHH",
            0x04034B50, 20, 0,
            compress_type, 0, 0,
            crc, len(payload), len(data),
            len(name_b), len(extra),
        )
        out += name_b + extra
        data_offset = offset + header_len
        assert data_offset % align == 0, (name, align, data_offset)
        out += payload
        central.append((name, crc, len(payload), len(data), compress_type,
                        date_time, external_attr, offset, len(extra)))
        offset += header_len + len(payload)

    for (name, data, compress_type, date_time, external_attr) in sources:
        write_entry(name, data, compress_type, date_time, external_attr)

    central_start = offset
    central_blob = bytearray()
    import struct
    for (name, crc, csize, usize, ctype, dt, attr, local_off, _) in central:
        name_b = name.encode("utf-8")
        central_blob += struct.pack(
            "<IHHHHHHIIIHHHHHII",
            0x02014B50, 20, 20, 0, ctype, 0, 0,
            crc, csize, usize,
            len(name_b), 0, 0, 0, 0, attr,
            local_off,
        )
        central_blob += name_b
    out += central_blob
    out += struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(central),
                       len(central), len(central_blob), central_start, 0)
    return bytes(out)


def main():
    if not os.path.exists(PACKAGE_APK):
        sys.exit(f"missing {PACKAGE_APK}: run ./gradlew :app:assembleRelease first")
    if not os.path.exists(LINKED_AP):
        sys.exit(f"missing {LINKED_AP}")
    if not os.path.exists(APKSIGNER_JAR):
        sys.exit(f"missing apksigner.jar: {APKSIGNER_JAR}")
    props = load_keystore_props()

    with zipfile.ZipFile(PACKAGE_APK) as pkg, zipfile.ZipFile(LINKED_AP) as lap:
        sources = []
        # 1. Manifest, resource table and resources first (classic APK order).
        for name in ["AndroidManifest.xml", "resources.arsc"] + sorted(
            n for n in lap.namelist() if n.startswith("res/")
        ):
            info = lap.getinfo(name)
            sources.append((
                name, lap.read(name),
                zipfile.ZIP_STORED if name == "resources.arsc" else info.compress_type,
                info.date_time, info.external_attr,
            ))
        # 2. Everything the packager produced (dex, libs, assets).
        #    Skip anything we already took from the linked package.
        provided = {name for name, _, _, _, _ in sources}
        for name in pkg.namelist():
            if name in provided:
                continue
            info = pkg.getinfo(name)
            ctype = info.compress_type
            if name.endswith(".so"):
                ctype = zipfile.ZIP_STORED
            sources.append((name, pkg.read(name), ctype, info.date_time, info.external_attr))

    tmp_unsigned = FINAL_APK + ".unsigned"
    with open(tmp_unsigned, "wb") as f:
        f.write(build_aligned_package(sources))

    env = dict(os.environ)
    cmd = [
        "java", "-jar", APKSIGNER_JAR, "sign",
        "--ks", props["storeFile"],
        "--ks-key-alias", props["keyAlias"],
        "--ks-pass", "pass:" + props["storePassword"],
        "--key-pass", "pass:" + props["keyPassword"],
        "--out", FINAL_APK,
        tmp_unsigned,
    ]
    subprocess.run(cmd, check=True, env=env)
    os.remove(tmp_unsigned)

    verify = subprocess.run(
        ["java", "-jar", APKSIGNER_JAR, "verify", "--print-certs", FINAL_APK],
        capture_output=True, text=True, env=env,
    )
    print(verify.stdout.strip().splitlines()[0] if verify.stdout.strip() else verify.stderr.strip())
    if verify.returncode != 0:
        sys.exit(f"apksigner verify FAILED:\n{verify.stdout}\n{verify.stderr}")
    print(f"OK: {FINAL_APK}")


if __name__ == "__main__":
    main()
