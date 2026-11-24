import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("secret_scan", Path(__file__).resolve().parents[1] / "run-secret-scan.py")
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


class SecretScanTests(unittest.TestCase):
    def test_scan_materializes_committed_blobs_including_export_ignored_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repo = root / "repo"
            repo.mkdir()

            def git(*args):
                subprocess.run(["git", *args], cwd=repo, check=True, capture_output=True)

            git("init", "-q")
            (repo / ".gitattributes").write_text("private.txt export-ignore\n")
            (repo / "private.txt").write_bytes(b"synthetic committed content")
            (repo / "link").symlink_to("private.txt")
            git("add", ".")
            git("-c", "user.name=Test", "-c", "user.email=test@example.invalid", "-c", "commit.gpgsign=false",
                "commit", "-qm", "Synthetic input")
            (repo / "private.txt").write_text("working copy")
            (repo / "untracked.txt").touch()
            output = root / "snapshot"
            output.mkdir()
            scanner.write_tracked_tree(repo, output)
            self.assertEqual((output / "private.txt").read_bytes(), b"synthetic committed content")
            self.assertFalse((output / "link").is_symlink())
            self.assertEqual((output / "link").read_text(), "private.txt")
            self.assertFalse((output / "untracked.txt").exists())
