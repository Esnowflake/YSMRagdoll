import tempfile
import unittest
import json
from io import BytesIO
from pathlib import Path
import zipfile
import hashlib

import release


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "gradle.properties").write_text(
            "mod_version=1.2.3\nminecraft_version=1.20.1\nforge_version=47.4.10\n",
            encoding="utf-8",
        )
        (self.root / "CHANGELOG.md").write_text(
            "# Changelog\n\n## [Unreleased]\n- Future work\n\n"
            "## [1.2.3]\n### Fixed\n- Released fix\n\n## [1.2.2]\n- Old fix\n",
            encoding="utf-8",
        )

    def test_selects_only_released_section(self):
        text = release.release_notes(self.root, "v1.2.3")
        self.assertIn("Released fix", text)
        self.assertNotIn("Future work", text)
        self.assertNotIn("Old fix", text)

    def test_rejects_invalid_or_mismatched_tag(self):
        for tag in ("1.2.3", "v1.2.4", "v01.2.3", "v1.2.3-rc.0", "v../1.2.3"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.release_notes(self.root, tag)

    def test_rejects_missing_or_duplicate_changelog(self):
        for text in ("## [Unreleased]\n- Only future\n",
                     "## [1.2.3]\n- One\n## [1.2.3]\n- Two\n",
                     "## [1.2.3]\n\n"):
            (self.root / "CHANGELOG.md").write_text(text, encoding="utf-8")
            with self.assertRaises(ValueError):
                release.release_notes(self.root, "v1.2.3")

    def make_jar(self, version="1.2.3", include_libraries=True):
        path = self.root / "build/libs/ysmragdoll-1.2.3-all.jar"
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("META-INF/MANIFEST.MF", f"Implementation-Version: {version}\n")
            jar.writestr("META-INF/mods.toml", """
modLoader="javafml"
[[mods]]
modId="ysmragdoll"
version="${file.jarVersion}"
[[dependencies.ysmragdoll]]
modId="minecraft"
versionRange="[1.20.1]"
[[dependencies.ysmragdoll]]
modId="forge"
versionRange="[47.4.0,48)"
""")
            metadata = []
            if include_libraries:
                for group, name, version in (("cz.advel.jbullet", "jbullet", "20101010-1"),
                                             ("javax.vecmath", "vecmath", "1.5.2")):
                    nested_path = f"META-INF/jarjar/{name}.jar"
                    buffer = BytesIO()
                    with zipfile.ZipFile(buffer, "w") as nested:
                        nested.writestr(
                            "com/bulletphysics/BulletGlobals.class" if name == "jbullet"
                            else "javax/vecmath/Vector3f.class", b"fixture"
                        )
                    jar.writestr(nested_path, buffer.getvalue())
                    metadata.append({
                        "identifier": {"group": group, "artifact": name},
                        "version": {"artifactVersion": version},
                        "path": nested_path,
                    })
            jar.writestr("META-INF/jarjar/metadata.json", json.dumps({"jars": metadata}))
            for required in (
                "META-INF/licenses/YSMRAGDOLL-LICENSE.txt",
                "META-INF/licenses/OpenYSM-LICENSE.txt",
                "com/ysmragdoll/OpenYsmRagdollMod.class",
                "com/elfmcys/yesstevemodel/resource/YSMBinaryDeserializer.class",
                "com/ysmragdoll/mixin/Ysm265MeshCaptureMixin.class",
            ):
                jar.writestr(required, "fixture")
            jar.writestr("ysmragdoll.mixins.json", '{"client":["Ysm265MeshCaptureMixin"]}')
        return path

    def test_prepares_flat_checksum_and_matching_bytes(self):
        jar = self.make_jar()
        release.prepare(self.root, "v1.2.3")
        output = self.root / "build/release"
        name = release.asset_name("1.2.3")
        self.assertEqual(jar.read_bytes(), (output / name).read_bytes())
        checksum = (output / "ysmragdoll-1.2.3-SHA256SUMS.txt").read_text()
        self.assertEqual(checksum.split()[1], name)

    def test_rejects_wrong_manifest_or_missing_libraries(self):
        for version, libraries in (("0.0.0", True), ("1.2.3", False)):
            self.make_jar(version, libraries)
            with self.assertRaises(ValueError):
                release.prepare(self.root, "v1.2.3")

    def test_accepts_matching_prerelease(self):
        props = self.root / "gradle.properties"
        props.write_text(props.read_text().replace("1.2.3", "1.2.3-rc.1"))
        log = self.root / "CHANGELOG.md"
        log.write_text(log.read_text().replace("[1.2.3]", "[1.2.3-rc.1]"))
        self.assertIn("1.2.3-rc.1", release.release_notes(self.root, "v1.2.3-rc.1"))

    def test_rejects_unsupported_game_target(self):
        props = self.root / "gradle.properties"
        props.write_text(props.read_text().replace("1.20.1", "1.21.1"))
        with self.assertRaises(ValueError):
            release.release_notes(self.root, "v1.2.3")

    def test_rejects_corrupt_nested_jar(self):
        path = self.make_jar()
        with zipfile.ZipFile(path) as jar:
            entries = {n: jar.read(n) for n in jar.namelist()}
        entries["META-INF/jarjar/jbullet.jar"] = b"PKgarbage"
        with zipfile.ZipFile(path, "w") as jar:
            for name, data in entries.items():
                jar.writestr(name, data)
        with self.assertRaises(zipfile.BadZipFile):
            release.prepare(self.root, "v1.2.3")

    def make_fabric_jar(self, minecraft="1.21.1", metadata_version="1.2.3", libraries=True):
        path = self.root / f"fabric/build/{minecraft}/libs/ysmragdoll-{minecraft}-fabric-1.2.3-all.jar"
        path.parent.mkdir(parents=True, exist_ok=True)
        metadata = {
            "id": "ysmragdoll", "version": metadata_version,
            "depends": {
                "minecraft": minecraft, "java": ">=21" if minecraft == "1.21.1" else ">=17",
                "fabricloader": ">=0.16.14", "fabric-api": "*", "forgeconfigapiport": "*",
            },
            "entrypoints": {
                "main": ["com.ysmragdoll.OpenYsmRagdollMod"],
                "client": ["com.ysmragdoll.client.ClientBootstrap"],
            },
            "jars": [],
        }
        with zipfile.ZipFile(path, "w") as jar:
            if libraries:
                for name, required in (
                    ("jbullet", "com/bulletphysics/BulletGlobals.class"),
                    ("vecmath", "javax/vecmath/Vector3f.class"),
                    ("config", "net/minecraftforge/common/ForgeConfigSpec.class"),
                ):
                    nested_path = f"META-INF/jars/{name}.jar"
                    buffer = BytesIO()
                    with zipfile.ZipFile(buffer, "w") as nested:
                        nested.writestr(required, b"fixture")
                    jar.writestr(nested_path, buffer.getvalue())
                    metadata["jars"].append({"file": nested_path})
            jar.writestr("fabric.mod.json", json.dumps(metadata))
            jar.writestr("ysmragdoll.fabric.mixins.json", '{"client":["Ysm265MeshCaptureMixin"]}')
            for name in (
                "META-INF/licenses/YSMRAGDOLL-LICENSE.txt", "META-INF/licenses/OpenYSM-LICENSE.txt",
                "com/ysmragdoll/OpenYsmRagdollMod.class",
                "com/ysmragdoll/client/ClientBootstrap.class",
                "com/elfmcys/yesstevemodel/resource/YSMBinaryDeserializer.class",
                "com/ysmragdoll/mixin/Ysm265MeshCaptureMixin.class",
                "ysmragdoll-platform.properties", "ysmragdoll.refmap.json",
            ):
                jar.writestr(name, b"fixture")
        return path

    def test_prepares_both_fabric_targets(self):
        for minecraft in ("1.20.1", "1.21.1"):
            source = self.make_fabric_jar(minecraft)
            target = "fabric-" + minecraft
            release.prepare(self.root, "v1.2.3", target)
            self.assertEqual(source.read_bytes(),
                             (self.root / "build/release" / release.asset_name("1.2.3", target)).read_bytes())

    def test_rejects_fabric_wrong_version_or_missing_dependencies(self):
        for version, libraries in (("0.0.0", True), ("1.2.3", False)):
            self.make_fabric_jar(metadata_version=version, libraries=libraries)
            with self.assertRaises(ValueError):
                release.prepare(self.root, "v1.2.3", "fabric-1.21.1")

    def test_rejects_unrequested_targets(self):
        for target in ("forge-1.21.1", "neoforge-1.21.1", "../forge-1.20.1"):
            with self.assertRaises(ValueError):
                release.asset_name("1.2.3", target)

    def make_bundles(self):
        inputs = self.root / "bundles"
        for target in release.TARGETS:
            directory = inputs / target
            directory.mkdir(parents=True)
            name = release.asset_name("1.2.3", target)
            data = target.encode()
            (directory / name).write_bytes(data)
            (directory / "ysmragdoll-1.2.3-SHA256SUMS.txt").write_text(
                f"{hashlib.sha256(data).hexdigest()}  {name}\n", encoding="utf-8")
            (directory / "release-notes.md").write_text(
                release.release_notes(self.root, "v1.2.3"), encoding="utf-8")
        return inputs

    def test_assembles_exactly_three_targets(self):
        release.assemble(self.root, "v1.2.3", self.make_bundles())
        output = self.root / "build/release"
        self.assertEqual(len(list(output.glob("*.jar"))), 3)
        self.assertEqual(len((output / "ysmragdoll-1.2.3-SHA256SUMS.txt").read_text().splitlines()), 3)

    def test_rejects_incomplete_or_tampered_matrix(self):
        inputs = self.make_bundles()
        jar = inputs / release.TARGETS[0] / release.asset_name("1.2.3")
        jar.write_bytes(b"changed")
        with self.assertRaises(ValueError):
            release.assemble(self.root, "v1.2.3", inputs)
        jar.unlink()
        with self.assertRaises(FileNotFoundError):
            release.assemble(self.root, "v1.2.3", inputs)


if __name__ == "__main__":
    unittest.main()
