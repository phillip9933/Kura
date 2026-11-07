#!/usr/bin/env python3
"""Scan the tracked tree with pinned Gitleaks; keep findings out of public logs."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import urllib.parse
import urllib.request


VERSION = "8.30.1"
ARCHIVE = f"gitleaks_{VERSION}_linux_x64.tar.gz"
ARCHIVE_SHA256 = "551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb"
CHECKSUMS = f"gitleaks_{VERSION}_checksums.txt"
CHECKSUMS_SHA256 = "061476c21adaf5441516f96f185c1a4706a83cd6329b9b38762271b3d4a52fae"
RELEASE = f"https://github.com/gitleaks/gitleaks/releases/download/v{VERSION}/"


def public_asset(name, digest):
    with urllib.request.urlopen(RELEASE + name, timeout=60) as response:
        destination = urllib.parse.urlparse(response.geturl())
        if destination.scheme != "https" or destination.hostname not in {
            "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"
        }:
            raise ValueError("Unexpected public release download destination")
        content = response.read(20_000_001)
    if len(content) > 20_000_000 or hashlib.sha256(content).hexdigest() != digest:
        raise ValueError("Public scanner asset integrity check failed")
    return content


def scanner_binary(work):
    archive = public_asset(ARCHIVE, ARCHIVE_SHA256)
    checksums = public_asset(CHECKSUMS, CHECKSUMS_SHA256)
    if f"{ARCHIVE_SHA256}  {ARCHIVE}".encode() not in checksums:
        raise ValueError("Public scanner checksum manifest disagrees")
    with tarfile.open(fileobj=io.BytesIO(archive)) as bundle:
        member = bundle.getmember("gitleaks")
        if not member.isfile() or member.size > 100_000_000:
            raise ValueError("Unexpected scanner archive member")
        binary = work / "gitleaks"
        binary.write_bytes(bundle.extractfile(member).read())
        binary.chmod(0o700)
    return binary


def run_scan(scanner, source, label, reports, work, environment):
    result = subprocess.run(
        [str(scanner), "dir", str(source), "--redact=100", "--no-banner", "--no-color",
         "--report-format", "json", "--report-path", str(reports / f"{label}.json")],
        cwd=work, env=environment, capture_output=True, timeout=180, check=False,
    )
    (reports / f"{label}.log").write_bytes(result.stdout + result.stderr)
    report = reports / f"{label}.json"
    findings = json.loads(report.read_text()) if report.exists() else None
    if result.returncode not in (0, 1) or not isinstance(findings, list):
        raise RuntimeError("Scanner execution failed; inspect the private report")
    if (result.returncode == 0) != (len(findings) == 0):
        raise RuntimeError("Scanner status and private report disagree")
    return {"scope": label, "exit_code": result.returncode, "finding_count": len(findings)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path.cwd())
    parser.add_argument("--scanner", type=Path, help="Previously checksum-verified scanner executable")
    parser.add_argument("--snapshot", type=Path, help="Existing tracked-tree archive for isolated local scans")
    parser.add_argument("--report-dir", type=Path, required=True, help="Private directory outside the repository")
    args = parser.parse_args()
    os.umask(0o077)
    repository = args.repository.resolve()
    reports = args.report_dir.resolve()
    if reports == repository or repository in reports.parents:
        parser.error("Reports must stay outside the repository")
    reports.mkdir(mode=0o700, parents=True, exist_ok=False)
    with tempfile.TemporaryDirectory(prefix="kura-secret-scan-") as temporary:
        work = Path(temporary)
        environment = {"PATH": "/usr/bin:/bin", "HOME": str(work / "home"), "LANG": "C.UTF-8"}
        scanner = args.scanner.resolve() if args.scanner else scanner_binary(work)
        version = subprocess.run([str(scanner), "version"], env=environment, cwd=work,
                                 capture_output=True, text=True, timeout=30, check=True)
        if version.stdout.strip() != VERSION:
            raise ValueError("Unexpected scanner version")
        if args.snapshot:
            source = args.snapshot.resolve()
        else:
            source = work / "source"
            source.mkdir()
            # Archive tracked bytes, excluding .git, ignored data and generated outputs.
            archive = subprocess.Popen(["git", "archive", "HEAD"], cwd=repository, stdout=subprocess.PIPE)
            with tarfile.open(fileobj=archive.stdout, mode="r|") as bundle:
                bundle.extractall(source, filter="data")
            if archive.wait() != 0:
                raise RuntimeError("Tracked-tree archive failed")
        fixture = work / "control"
        fixture.mkdir()
        # Fabricated base32 credential-pattern control; never a usable credential.
        (fixture / "control.txt").write_text("aws_access_key_id = " + "AKIA" + "QZMYPNRSWDXHJKUV" + "\n")
        control = run_scan(scanner, fixture, "control", reports, work, environment)
        if control["exit_code"] != 1 or control["finding_count"] < 1:
            raise RuntimeError("Controlled failing example was not detected")
        current = run_scan(scanner, source, "tracked-tree", reports, work, environment)
        summary = {"scanner": f"Gitleaks {VERSION}", "scope": "Current tracked tree; not Git history or binary/archive internals",
                   "redaction_percent": 100, "reports_uploaded": False, "runs": [control, current]}
        (reports / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        print(json.dumps(summary))
        return current["exit_code"]


if __name__ == "__main__":
    raise SystemExit(main())
