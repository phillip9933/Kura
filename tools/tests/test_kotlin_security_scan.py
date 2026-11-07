"""Keep review decisions bound to canonical bytes and exact finding tuples."""

import copy
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location(
    "kotlin_scan", Path(__file__).resolve().parents[1] / "run-kotlin-security-scan.py"
)
SCAN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SCAN)


class KotlinSecurityScanTests(unittest.TestCase):
    def test_archive_uses_git_blob_bytes_despite_autocrlf(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository = root / "repository"
            repository.mkdir()

            def git(*arguments):
                return subprocess.run(
                    ["git", *arguments], cwd=repository, check=True,
                    capture_output=True,
                ).stdout

            git("init", "--quiet")
            git("config", "core.autocrlf", "true")
            file = repository / "module/src/main/Example.kt"
            file.parent.mkdir(parents=True)
            file.write_bytes(b"class Example {\n    val value = 12\n}\n")
            git("add", "module/src/main/Example.kt")
            git("-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "Fixture")
            expected = git("cat-file", "blob", "HEAD:module/src/main/Example.kt")
            work = root / "work"
            work.mkdir()
            source, files = SCAN.prepare_source(repository, None, work)
            self.assertEqual([source / "module/src/main/Example.kt"], files)
            self.assertEqual(expected, files[0].read_bytes())
            self.assertNotIn(b"\r", files[0].read_bytes())

    def test_review_rejects_changed_source_and_finding_set(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / "Example.kt"
            original = b"val nonce = ByteArray(12)\n"
            file.write_bytes(original)
            match = {"check_id": "gcm-detection", "path": str(file),
                     "start": {"line": 1}, "extra": {"severity": "INFO"}}
            audits = {"findings": [{"rule": "gcm-detection", "file": "Example.kt",
                                    "line": 1, "source_sha256": hashlib.sha256(original).hexdigest()}]}
            self.assertEqual((0, False), SCAN.review_audits([match], root, audits))
            # A one-byte nonce change must invalidate review even at the same line.
            file.write_bytes(original.replace(b"12", b"11"))
            self.assertEqual((1, True), SCAN.review_audits([match], root, audits))
            file.write_bytes(original)
            other = root / "Other.kt"
            other.write_bytes(original)
            for name, mutate in [
                ("rule", lambda value: value.update(check_id="other-rule")),
                ("path", lambda value: value.update(path=str(other))),
                ("line", lambda value: value["start"].update(line=2)),
                ("severity", lambda value: value["extra"].update(severity="WARNING")),
            ]:
                with self.subTest(change=name):
                    changed = copy.deepcopy(match)
                    mutate(changed)
                    unexpected, changed_set = SCAN.review_audits([changed], root, audits)
                    self.assertGreater(unexpected, 0)
                    self.assertTrue(changed_set or name == "severity")
            self.assertEqual((0, True), SCAN.review_audits([], root, audits))
            self.assertEqual((0, True), SCAN.review_audits([match, match], root, audits))


if __name__ == "__main__":
    unittest.main()
