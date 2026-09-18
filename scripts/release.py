"""Validate release metadata and prepare only verified, freshly built packages."""

import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import re
import tomllib
import zipfile


ROOT = Path(__file__).resolve().parents[1]
VERSION = re.compile(
    r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(?:alpha|beta|rc)\.[1-9]\d*)?",
    re.ASCII,
)
TARGETS = ("forge-1.20.1", "fabric-1.20.1", "fabric-1.21.1")


def asset_name(version, target="forge-1.20.1"):
    if target not in TARGETS:
        raise ValueError("Unsupported release target: " + target)
    loader, minecraft = target.split("-", 1)
    return f"ysmragdoll-{version}+mc{minecraft}-{loader}-all.jar"


def properties(root):
    return dict(
        line.split("=", 1)
        for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines()
        if "=" in line and not line.lstrip().startswith("#")
    )


def release_notes(root, tag):
    props = properties(root)
    if props["minecraft_version"] != "1.20.1" or props["forge_version"] != "47.4.10":
        raise ValueError("Release tooling currently supports only Minecraft 1.20.1 / Forge 47.4.10")
    version = tag.removeprefix("v")
    if not tag.startswith("v") or not VERSION.fullmatch(version):
        raise ValueError("Expected vMAJOR.MINOR.PATCH, optionally -alpha.N, -beta.N or -rc.N")
    if version != props["mod_version"]:
        raise ValueError("Tag version differs from gradle.properties")
    changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")
    matches = list(re.finditer(r"^## \[([^\]]+)\][^\n]*$", changelog, re.MULTILINE))
    selected = [i for i, match in enumerate(matches) if match[1] == version]
    if len(selected) != 1:
        raise ValueError("Expected exactly one changelog section for " + version)
    index = selected[0]
    end = matches[index + 1].start() if index + 1 < len(matches) else len(changelog)
    body = changelog[matches[index].end():end].strip()
    if not re.search(r"^- \S", body, re.MULTILINE):
        raise ValueError("Release changelog must contain at least one change")
    return (
        f"# YSM Ragdoll {version}\n\n"
        "Forge 1.20.1 / Java 17 | Fabric 1.20.1 / Java 17 | Fabric 1.21.1 / Java 21\n\n"
        f"{body}\n\n"
        "## Installation / 安装\n\n"
        "Install the `-all.jar`, not the plain JAR or source archive. "
        "安装 `-all.jar`，不要安装普通 JAR 或源码压缩包。\n\n"
        "Choose the JAR matching your Minecraft version and loader. Fabric requires Fabric API. "
        "YSM compatibility target: 2.6.5. "
        "Build checks do not replace in-game testing.\n\n"
        "To verify the complete download, place all three JARs and the checksum file together:\n\n"
        f"```sh\nsha256sum --check ysmragdoll-{version}-SHA256SUMS.txt\n```\n"
    )


def prepare(root, tag, target="forge-1.20.1"):
    notes = release_notes(root, tag)
    version = tag[1:]
    filename = asset_name(version, target)
    if target.startswith("fabric-"):
        minecraft = target.split("-", 1)[1]
        jar = root / "fabric" / "build" / minecraft / "libs" / f"ysmragdoll-{minecraft}-fabric-{version}-all.jar"
        verify_fabric(jar, version, minecraft)
        write_bundle(root, jar, filename, version, notes)
        return
    jar = root / "build" / "libs" / f"ysmragdoll-{version}-all.jar"
    with zipfile.ZipFile(jar) as archive:
        if archive.testzip():
            raise ValueError("Corrupt JAR")
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
        if f"Implementation-Version: {version}" not in manifest.splitlines():
            raise ValueError("JAR manifest version mismatch")
        metadata = tomllib.loads(archive.read("META-INF/mods.toml").decode("utf-8"))
        mods = metadata.get("mods", [])
        if len(mods) != 1 or mods[0].get("modId") != "ysmragdoll":
            raise ValueError("Missing or incorrect mod ID")
        if mods[0].get("version") != "${file.jarVersion}":
            raise ValueError("Mod metadata must use the manifest version")
        if metadata.get("modLoader") != "javafml":
            raise ValueError("Expected Forge metadata")
        dependencies = {d["modId"]: d for d in metadata.get("dependencies", {}).get("ysmragdoll", [])}
        if dependencies.get("minecraft", {}).get("versionRange") != "[1.20.1]":
            raise ValueError("Minecraft compatibility must be restricted to the tested version")
        if dependencies.get("forge", {}).get("versionRange") != "[47.4.0,48)":
            raise ValueError("Unexpected Forge compatibility range")
        libraries = json.loads(archive.read("META-INF/jarjar/metadata.json"))["jars"]
        expected = {
            ("cz.advel.jbullet", "jbullet"): "20101010-1",
            ("javax.vecmath", "vecmath"): "1.5.2",
        }
        found = {}
        for library in libraries:
            identifier = library["identifier"]
            key = (identifier["group"], identifier["artifact"])
            if key in found:
                raise ValueError("Duplicate bundled dependency")
            found[key] = library["version"]["artifactVersion"]
            path = library["path"]
            if not path.startswith("META-INF/jarjar/") or ".." in Path(path).parts:
                raise ValueError("Unsafe nested dependency path")
            with zipfile.ZipFile(BytesIO(archive.read(path))) as nested:
                if nested.testzip():
                    raise ValueError("Corrupt nested dependency")
                required_class = {
                    ("cz.advel.jbullet", "jbullet"): "com/bulletphysics/BulletGlobals.class",
                    ("javax.vecmath", "vecmath"): "javax/vecmath/Vector3f.class",
                }.get(key)
                if required_class is None or not nested.read(required_class):
                    raise ValueError("Missing expected nested dependency class")
        if found != expected:
            raise ValueError("Bundled dependency versions differ from the supported set")
        for required in (
            "META-INF/licenses/YSMRAGDOLL-LICENSE.txt",
            "META-INF/licenses/OpenYSM-LICENSE.txt",
            "com/ysmragdoll/OpenYsmRagdollMod.class",
            "com/elfmcys/yesstevemodel/resource/YSMBinaryDeserializer.class",
            "com/ysmragdoll/mixin/Ysm265MeshCaptureMixin.class",
        ):
            if not archive.read(required):
                raise ValueError("Empty required entry: " + required)
        mixin = json.loads(archive.read("ysmragdoll.mixins.json"))
        if "Ysm265MeshCaptureMixin" not in mixin.get("client", []):
            raise ValueError("Missing YSM capture mixin")
    write_bundle(root, jar, filename, version, notes)


def verify_fabric(jar, version, minecraft):
    with zipfile.ZipFile(jar) as archive:
        if archive.testzip():
            raise ValueError("Corrupt Fabric JAR")
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata.get("id") != "ysmragdoll" or metadata.get("version") != version:
            raise ValueError("Fabric mod ID or version mismatch")
        expected_java = ">=21" if minecraft == "1.21.1" else ">=17"
        depends = metadata.get("depends", {})
        if depends.get("minecraft") != minecraft or depends.get("java") != expected_java:
            raise ValueError("Fabric game or Java version mismatch")
        if not {"fabricloader", "fabric-api", "forgeconfigapiport"} <= depends.keys():
            raise ValueError("Missing Fabric runtime dependency")
        if metadata.get("entrypoints") != {
            "main": ["com.ysmragdoll.OpenYsmRagdollMod"],
            "client": ["com.ysmragdoll.client.ClientBootstrap"],
        }:
            raise ValueError("Unexpected Fabric entrypoints")
        for required in (
            "META-INF/licenses/YSMRAGDOLL-LICENSE.txt",
            "META-INF/licenses/OpenYSM-LICENSE.txt",
            "com/ysmragdoll/OpenYsmRagdollMod.class",
            "com/ysmragdoll/client/ClientBootstrap.class",
            "com/elfmcys/yesstevemodel/resource/YSMBinaryDeserializer.class",
            "com/ysmragdoll/mixin/Ysm265MeshCaptureMixin.class",
            "ysmragdoll-platform.properties",
            "ysmragdoll.refmap.json",
        ):
            if not archive.read(required):
                raise ValueError("Missing Fabric runtime entry: " + required)
        mixin = json.loads(archive.read("ysmragdoll.fabric.mixins.json"))
        if "Ysm265MeshCaptureMixin" not in mixin.get("client", []):
            raise ValueError("Missing Fabric YSM capture mixin")
        expected = {
            "com/bulletphysics/BulletGlobals.class",
            "javax/vecmath/Vector3f.class",
            "net/minecraftforge/common/ForgeConfigSpec.class",
        }
        found = set()
        paths = set()
        for entry in metadata.get("jars", []):
            path = entry["file"]
            if path in paths or not path.startswith("META-INF/jars/") or ".." in Path(path).parts:
                raise ValueError("Unsafe or duplicate Fabric nested dependency")
            paths.add(path)
            with zipfile.ZipFile(BytesIO(archive.read(path))) as nested:
                if nested.testzip():
                    raise ValueError("Corrupt Fabric nested dependency")
                found.update(expected.intersection(nested.namelist()))
        if found != expected:
            raise ValueError("Missing Fabric bundled dependency")


def write_bundle(root, jar, filename, version, notes):
    output = root / "build" / "release"
    output.mkdir(parents=True, exist_ok=True)
    data = jar.read_bytes()
    (output / filename).write_bytes(data)
    digest = hashlib.sha256(data).hexdigest()
    (output / f"ysmragdoll-{version}-SHA256SUMS.txt").write_text(
        f"{digest}  {filename}\n", encoding="utf-8"
    )
    (output / "release-notes.md").write_text(notes, encoding="utf-8")
    print(f"Verified {filename}: {digest}")


def assemble(root, tag, inputs):
    notes = release_notes(root, tag)
    version = tag[1:]
    output = root / "build" / "release"
    output.mkdir(parents=True, exist_ok=True)
    checksums = []
    for target in TARGETS:
        name = asset_name(version, target)
        directory = inputs / target
        data = (directory / name).read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        expected = f"{digest}  {name}\n"
        if (directory / f"ysmragdoll-{version}-SHA256SUMS.txt").read_text(encoding="utf-8") != expected:
            raise ValueError("Release bundle checksum mismatch: " + target)
        if (directory / "release-notes.md").read_text(encoding="utf-8") != notes:
            raise ValueError("Release bundle notes mismatch: " + target)
        (output / name).write_bytes(data)
        checksums.append(expected)
    (output / f"ysmragdoll-{version}-SHA256SUMS.txt").write_text("".join(checksums), encoding="utf-8")
    (output / "release-notes.md").write_text(notes, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("validate", "prepare", "assemble"))
    parser.add_argument("tag")
    parser.add_argument("--target", choices=TARGETS, default=TARGETS[0])
    parser.add_argument("--inputs", type=Path, default=ROOT / "build" / "bundles")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(ROOT, args.tag, args.target)
    elif args.command == "assemble":
        assemble(ROOT, args.tag, args.inputs)
    else:
        release_notes(ROOT, args.tag)
        print("Release metadata valid:", args.tag)


if __name__ == "__main__":
    main()
