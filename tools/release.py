#!/usr/bin/env python3
"""Bump WazheBox's version, record the change and build Play-ready artifacts.

    python3 tools/release.py show
    python3 tools/release.py bump --type patch --note "رفع باگ کارت مرور"
    python3 tools/release.py bump --type minor --note "ویجت تازه" --build

The version lives in version.properties (Gradle reads it), so a release never
edits build files and `git log` shows exactly which version each commit was.
versionCode always increases by one; versionName follows semver unless
--version is given explicitly.
"""
from __future__ import annotations

import argparse
import datetime
import hashlib
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "version.properties"
CHANGELOG = ROOT / "CHANGELOG.md"
APP = ROOT / "app"
ARTIFACTS = ROOT / "artifacts"
CHANGELOG_MARKER = "<!-- releases -->"

AAB = APP / "build/outputs/bundle/release/app-release.aab"
APK = APP / "build/outputs/apk/release/app-release.apk"
MAPPING = APP / "build/outputs/mapping/release/mapping.txt"


def read_version() -> tuple[int, str]:
    if not VERSION_FILE.exists():
        sys.exit(f"error: {VERSION_FILE.name} not found")
    values: dict[str, str] = {}
    for line in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    try:
        code = int(values.get("versionCode", "1"))
    except ValueError:
        sys.exit("error: versionCode in version.properties is not a number")
    return code, values.get("versionName", "1.0.0")


def write_version(code: int, name: str) -> None:
    VERSION_FILE.write_text(
        "# Single source of truth for the version that goes to Google Play.\n"
        "# Gradle reads it (app/build.gradle.kts) and tools/release.py bumps it.\n"
        "# versionCode must increase with every upload; versionName is what users see.\n"
        f"versionCode={code}\n"
        f"versionName={name}\n",
        encoding="utf-8",
    )


def next_name(current: str, kind: str) -> str:
    numbers = [int(part) for part in re.findall(r"\d+", current)[:3]]
    while len(numbers) < 3:
        numbers.append(0)
    major, minor, patch = numbers
    if kind == "major":
        return f"{major + 1}.0.0"
    if kind == "minor":
        return f"{major}.{minor + 1}.0"
    return f"{major}.{minor}.{patch + 1}"


def validate_name(name: str) -> str:
    if not re.fullmatch(r"\d+\.\d+\.\d+", name):
        sys.exit(f"error: versionName '{name}' is not MAJOR.MINOR.PATCH")
    return name


def changelog_entry(name: str, notes: list[str]) -> str:
    day = datetime.date.today().isoformat()
    bullets = "\n".join(f"- {note.strip()}" for note in notes if note.strip())
    return f"## {name} — {day}\n\n{bullets}\n\n"


def prepend_changelog(entry: str) -> None:
    if not CHANGELOG.exists():
        CHANGELOG.write_text(
            "# تاریخچهٔ نسخه‌ها\n\n" + CHANGELOG_MARKER + "\n\n" + entry,
            encoding="utf-8",
        )
        return
    text = CHANGELOG.read_text(encoding="utf-8")
    # Anchor on a whole line: the marker is also mentioned in the intro prose,
    # and splitting on the first raw occurrence broke the heading paragraph.
    # [ \t] rather than \s: \s would swallow the newlines after the marker and
    # leave a double blank line above the new entry.
    marker_line = re.compile(rf"^{re.escape(CHANGELOG_MARKER)}[ \t]*$", re.MULTILINE)
    match = marker_line.search(text)
    if match:
        head = text[: match.end()]
        tail = text[match.end():].lstrip("\n")
        text = f"{head}\n\n{entry}{tail}"
    else:
        heading, _, rest = text.partition("\n")
        text = f"{heading}\n\n{CHANGELOG_MARKER}\n\n{entry}{rest.lstrip()}"
    CHANGELOG.write_text(text, encoding="utf-8")


def run(command: list[str]) -> None:
    print("$ " + " ".join(command))
    subprocess.run(command, cwd=ROOT, check=True)


def build(code: int, name: str) -> None:
    run([
        "./gradlew",
        ":app:testDebugUnitTest",
        ":app:lintRelease",
        ":app:bundleRelease",
        ":app:assembleRelease",
        "--no-daemon",
    ])

    if not AAB.exists() or not APK.exists():
        sys.exit("error: gradle finished but the bundle or the APK is missing")
    ARTIFACTS.mkdir(exist_ok=True)
    targets = [
        (AAB, ARTIFACTS / f"wazhebox-{name}-release.aab"),
        (APK, ARTIFACTS / f"wazhebox-{name}-release-signed.apk"),
    ]
    sums: list[str] = []
    for source, target in targets:
        shutil.copy2(source, target)
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        sums.append(f"{digest}  {target.name}")

    if MAPPING.exists():
        shutil.copy2(MAPPING, ARTIFACTS / f"mapping-{name}.txt")

    checksum_file = ARTIFACTS / f"wazhebox-{name}-release.sha256"
    checksum_file.write_text("\n".join(sums) + "\n", encoding="utf-8")

    print("\nversionCode {0} · versionName {1}".format(code, name))
    for line in sums:
        print(line)
    print(f"checksums: {checksum_file.relative_to(ROOT)}")
    if (ROOT / "keystore.properties").exists():
        print("signed with the key from keystore.properties")
    else:
        print("warning: keystore.properties is missing, so the release is UNSIGNED")
    print("\nnext:")
    print(f"  1. upload {targets[0][1].relative_to(ROOT)} to Play Console")
    print(f"  2. upload {MAPPING.relative_to(ROOT)} as the deobfuscation file")
    print(f"  3. git tag v{name} && git push --tags")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Bump the WazheBox version and build Play artifacts.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("show", help="print the current version")

    bump = sub.add_parser("bump", help="increase the version and write a changelog entry")
    bump.add_argument(
        "--type",
        choices=["patch", "minor", "major"],
        default="patch",
        help="semver part to increase (default: patch)",
    )
    bump.add_argument(
        "--version",
        help="use this exact MAJOR.MINOR.PATCH instead of a computed bump",
    )
    bump.add_argument(
        "--note",
        action="append",
        default=[],
        metavar="TEXT",
        help="changelog bullet; repeat for more bullets",
    )
    bump.add_argument(
        "--build",
        action="store_true",
        help="also run tests, lint, bundleRelease, assembleRelease and copy the outputs",
    )
    bump.add_argument(
        "--dry-run",
        action="store_true",
        help="print what would change without touching any file",
    )

    args = parser.parse_args()

    if args.command == "show":
        code, name = read_version()
        print(f"versionCode={code} versionName={name}")
        return

    notes = args.note or []
    if not notes:
        sys.exit("error: pass at least one --note so the changelog explains the release")

    code, current = read_version()
    name = validate_name(args.version) if args.version else next_name(current, args.type)
    if name == current:
        sys.exit(f"error: versionName would stay {current}; pick a higher version")
    new_code = code + 1

    print(f"versionCode {code} → {new_code}")
    print(f"versionName {current} → {name}")
    for note in notes:
        print(f"  • {note.strip()}")

    if args.dry_run:
        print("\ndry run: nothing written")
        return

    write_version(new_code, name)
    prepend_changelog(changelog_entry(name, notes))
    print(f"\nupdated {VERSION_FILE.name} and {CHANGELOG.name}")

    if args.build:
        build(new_code, name)
    else:
        print("\nnext: python3 tools/release.py bump --build (or run gradlew bundleRelease)")


if __name__ == "__main__":
    main()
