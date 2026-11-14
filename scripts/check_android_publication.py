#!/usr/bin/env python3
"""Check the eXcor Android AAR and POM in a fresh local Maven publication."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[1]
NOTICE_FILES = ("LICENSE", "NOTICE", "docs/licenses/Apache-2.0.txt")
MAVEN_NS = {"": "http://maven.apache.org/POM/4.0.0"}


def check_publication(group_directory: Path) -> Path:
    poms = sorted((group_directory / "eXcor-android").glob("*/eXcor-android-*.pom"))
    if len(poms) != 1:
        raise ValueError(f"Expected one eXcor-android POM under {group_directory}, found {len(poms)}")
    pom = poms[0]
    project = ET.parse(pom).getroot()
    expected = {
        "groupId": "com.excor",
        "artifactId": "eXcor-android",
        "version": pom.parent.name,
        "packaging": "aar",
    }
    for field, value in expected.items():
        if project.findtext(field, namespaces=MAVEN_NS) != value:
            raise ValueError(f"{pom.name}: expected {field}={value}")
    licenses = project.findall("licenses/license", MAVEN_NS)
    if len(licenses) != 1 or (
        licenses[0].findtext("name", namespaces=MAVEN_NS),
        licenses[0].findtext("url", namespaces=MAVEN_NS),
    ) != ("GNU General Public License v3.0 only", "https://www.gnu.org/licenses/gpl-3.0.html"):
        raise ValueError(f"{pom.name}: expected the GPL-3.0-only license declaration")

    aar = pom.with_suffix(".aar")
    with ZipFile(aar) as archive:
        names = archive.namelist()
        for name in ("AndroidManifest.xml", "classes.jar"):
            if names.count(name) != 1 or archive.getinfo(name).file_size == 0:
                raise ValueError(f"{aar.name}: expected one nonempty {name}")
        for source in map(Path, NOTICE_FILES):
            name = f"META-INF/{source.name}"
            expected_bytes = (ROOT / source).read_bytes()
            if names.count(name) != 1:
                raise ValueError(f"{aar.name}: expected one {name}")
            if archive.getinfo(name).file_size != len(expected_bytes) or archive.read(name) != expected_bytes:
                raise ValueError(f"{aar.name}: {name} differs from {source}")
    return aar


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("group_directory", type=Path, help="fresh local Maven repository's com/excor directory")
    args = parser.parse_args()
    try:
        aar = check_publication(args.group_directory)
    except (OSError, ValueError, BadZipFile, ET.ParseError) as error:
        print(error, file=sys.stderr)
        return 1
    print(f"Verified {aar.name}: Android manifest, classes, three matching notices, and GPL-3.0-only POM.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
