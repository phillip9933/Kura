#!/usr/bin/env python3
"""Run pinned native Semgrep rules without uploading source or findings."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import tempfile
import urllib.parse
import urllib.request
import zipfile


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def download(url, destination, expected, limit):
    with urllib.request.urlopen(url, timeout=120) as response:
        resolved = urllib.parse.urlparse(response.geturl())
        if resolved.scheme != "https" or resolved.hostname not in {
            "files.pythonhosted.org", "raw.githubusercontent.com"
        }:
            raise ValueError("Unexpected public scanner input destination")
        with destination.open("wb") as output:
            total = 0
            while chunk := response.read(64 * 1024):
                total += len(chunk)
                if total > limit:
                    raise ValueError("Public scanner input exceeds its limit")
                output.write(chunk)
    if expected and sha256(destination) != expected:
        raise ValueError("Public scanner input digest mismatch")


def targets(files, root):
    return ["Targets", [["CodeTarget", {
        "path": {"fpath": str(path), "ppath": "/" + path.relative_to(root).as_posix()},
        "analyzer": "kotlin", "products": ["sast"],
    }] for path in files]]


def scanner_result(command, environment, work, reports, name):
    run = subprocess.run(command, env=environment, cwd=work, capture_output=True,
                         timeout=180, check=False)
    (reports / f"{name}.json").write_bytes(run.stdout)
    (reports / f"{name}.log").write_bytes(run.stderr)
    if run.returncode != 0:
        raise RuntimeError("Native scanner failed; inspect private diagnostics")
    result = json.loads(run.stdout)
    if result.get("errors") or result.get("skipped_rules"):
        raise RuntimeError("Native scanner errors/skips require private review")
    return result


def prepare_native_tool(work, wheel, inputs):
    tool = work / "scanner"
    tool.mkdir()
    with zipfile.ZipFile(wheel) as bundle:
        core = [name for name in bundle.namelist() if name.endswith("/bin/semgrep-core")]
        if len(core) != 1:
            raise ValueError("Unexpected native scanner layout")
        members = [core[0]] + [name for name in bundle.namelist()
                               if "/bin/libs/" in name and not name.endswith("/")]
        for name in members:
            info = bundle.getinfo(name)
            if info.file_size > 300_000_000:
                raise ValueError("Unexpected native scanner member size")
            relative = name.split("/bin/", 1)[1]
            if Path(relative).is_absolute() or ".." in Path(relative).parts:
                raise ValueError("Unsafe native scanner member")
            destination = tool / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            with bundle.open(name) as source, destination.open("wb") as output:
                shutil.copyfileobj(source, output)
    binary = tool / "semgrep-core"
    binary.chmod(0o700)
    if sha256(binary) != inputs["core_sha256"]:
        raise ValueError("Native scanner digest mismatch")
    return binary


def prepare_rule_inputs(work, inputs, rules_dir):
    rules = work / "rules"
    rules.mkdir()
    upstream = f"https://raw.githubusercontent.com/semgrep/semgrep-rules/{inputs['rules_revision']}/"
    if rules_dir:
        shutil.copy2(rules_dir / "LICENSE", rules / "LICENSE")
    else:
        download(upstream + "LICENSE", rules / "LICENSE", inputs["rules_license_sha256"], 100_000)
    if sha256(rules / "LICENSE") != inputs["rules_license_sha256"]:
        raise ValueError("Pinned rule license digest mismatch")
    for item in inputs["rules"]:
        destination = rules / item["path"]
        if rules_dir:
            shutil.copy2(rules_dir / item["path"], destination)
        else:
            download(upstream + "kotlin/lang/security/" + item["path"], destination,
                     item["sha256"], 100_000)
        if sha256(destination) != item["sha256"]:
            raise ValueError("Pinned rule digest mismatch")
    return rules


def prepare_source(repository, snapshot, work):
    if snapshot:
        source = snapshot.resolve()
    else:
        source = work / "source"
        source.mkdir()
        archive = subprocess.Popen(["git", "archive", "HEAD"], cwd=repository, stdout=subprocess.PIPE)
        with tarfile.open(fileobj=archive.stdout, mode="r|") as bundle:
            bundle.extractall(source, filter="data")
        if archive.wait() != 0:
            raise RuntimeError("Tracked-tree archive failed")
    files = sorted(path for path in source.rglob("*.kt") if "src" in path.relative_to(source).parts
                   and not path.relative_to(source).parts[0] == "benchmark"
                   and not any(part in {"test", "androidTest"} or part.endswith("AndroidTest")
                               for part in path.relative_to(source).parts))
    return source, files


def scan_sources(binary, rules, inputs, source, files, work, reports):
    control = work / "control"
    control.mkdir()
    fixture = control / "Synthetic.kt"
    fixture.write_text('import javax.crypto.Cipher\nimport javax.crypto.NullCipher\n'
                       'import java.security.KeyPairGenerator\nfun forbiddenControl() {\n'
                       ' val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")\n'
                       ' val plain = NullCipher()\n'
                       ' val generator = KeyPairGenerator.getInstance("RSA")\n'
                       ' generator.initialize(1024)\n}\n')
    environment = {"PATH": "/usr/bin:/bin", "HOME": str(work / "home"), "LANG": "C.UTF-8",
                   "LD_LIBRARY_PATH": str(binary.parent / "libs")}
    found = {"control": [], "source": []}
    scanned = set()
    for label, paths, root in [("control", [fixture], control), ("source", files, source)]:
        target_file = work / f"{label}-targets.json"
        target_file.write_text(json.dumps(targets(paths, root)))
        for item in inputs["rules"]:
            result = scanner_result(
                [str(binary), "-rules", str(rules / item["path"]), "-targets", str(target_file),
                 "-json_nodots", "-j", "2", "-timeout", "15", "-max_memory", "1024"],
                environment, work, reports, f"{label}-{Path(item['path']).stem}",
            )
            found[label].extend(result["results"])
            if label == "source":
                scanned.update(result.get("paths", {}).get("scanned", []))
    return found, scanned


def review_audits(matches, source, audits):
    expected = {(item["rule"], item["file"], item["line"], item["source_sha256"])
                for item in audits["findings"]}
    observed = set()
    unexpected = 0
    for match in matches:
        path = Path(match["path"])
        finding = (match["check_id"], path.relative_to(source).as_posix(),
                   match["start"]["line"], sha256(path))
        if match["extra"]["severity"] != "INFO" or finding not in expected:
            unexpected += 1
        observed.add(finding)
    return unexpected, observed != expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path.cwd())
    parser.add_argument("--snapshot", type=Path, help="Tracked-tree snapshot for an isolated local run")
    parser.add_argument("--wheel", type=Path, help="Previously downloaded official pinned wheel")
    parser.add_argument("--rules-dir", type=Path, help="Previously downloaded pinned rules and LICENSE")
    parser.add_argument("--report-dir", type=Path, required=True)
    args = parser.parse_args()
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        parser.error("This pinned scanner bundle requires Linux x86_64")
    repository = args.repository.resolve()
    reports = args.report_dir.resolve()
    if reports == repository or repository in reports.parents:
        parser.error("Keep private reports outside the repository")
    os.umask(0o077)
    reports.mkdir(mode=0o700, parents=True, exist_ok=False)
    inputs = json.loads((Path(__file__).parent / "security/kotlin-scan-inputs.json").read_text())
    audits = json.loads((Path(__file__).parent / "security/kotlin-audit-findings.json").read_text())
    with tempfile.TemporaryDirectory(prefix="kura-kotlin-scan-") as temporary:
        work = Path(temporary)
        wheel = args.wheel.resolve() if args.wheel else work / "semgrep.whl"
        if not args.wheel:
            download(inputs["wheel_url"], wheel, inputs["wheel_sha256"], 80_000_000)
        if sha256(wheel) != inputs["wheel_sha256"]:
            raise ValueError("Scanner wheel digest mismatch")
        binary = prepare_native_tool(work, wheel, inputs)
        rules = prepare_rule_inputs(work, inputs, args.rules_dir)
        source, files = prepare_source(repository, args.snapshot, work)
        found, scanned = scan_sources(binary, rules, inputs, source, files, work, reports)
        if not {"ecb-cipher", "no-null-cipher", "use-of-weak-rsa-key"}.issubset(
            {match["check_id"] for match in found["control"]}
        ):
            raise RuntimeError("Controlled forbidden algorithms were not detected")
        if scanned != {str(path) for path in files}:
            raise RuntimeError("Source scan coverage differs from selected targets")
        unexpected, audit_set_changed = review_audits(found["source"], source, audits)
        summary = {"scanner": f"Semgrep native {inputs['version']}", "rules": len(inputs["rules"]),
                   "shipping_kotlin_files": len(files), "control_matches": len(found["control"]),
                   "retained_informational_audits": sum(match["extra"]["severity"] == "INFO" for match in found["source"]),
                   "unreviewed_findings": unexpected,
                   "audit_set_changed": audit_set_changed, "scan_errors": 0, "skipped_rules": 0,
                   "reports_uploaded": False, "source_uploaded": False}
        (reports / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        print(json.dumps(summary))
        return 1 if unexpected or audit_set_changed else 0


if __name__ == "__main__":
    raise SystemExit(main())
