from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_android_publication import ROOT, check_publication


class AndroidPublicationTest(unittest.TestCase):
    def test_requires_exact_notice_bytes_and_pom_license(self):
        with tempfile.TemporaryDirectory() as directory:
            group = Path(directory)
            pom = group / "eXcor-android/0.2.0/eXcor-android-0.2.0.pom"
            pom.parent.mkdir(parents=True)
            pom_text = """<project xmlns="http://maven.apache.org/POM/4.0.0">
              <groupId>com.excor</groupId><artifactId>eXcor-android</artifactId>
              <version>0.2.0</version><packaging>aar</packaging>
              <licenses><license><name>GNU General Public License v3.0 only</name>
              <url>https://www.gnu.org/licenses/gpl-3.0.html</url></license></licenses>
            </project>"""
            pom.write_text(pom_text, encoding="utf-8")
            aar = pom.with_suffix(".aar")
            notice = (ROOT / "NOTICE").read_bytes()
            for content, error in (
                (None, "expected one META-INF/NOTICE"),
                (bytes(len(notice)), "META-INF/NOTICE differs"),
                (notice, None),
            ):
                with self.subTest(error=error):
                    with ZipFile(aar, "w") as archive:
                        archive.writestr("AndroidManifest.xml", "<manifest/>")
                        archive.writestr("classes.jar", b"fixture")
                        archive.write(ROOT / "LICENSE", "META-INF/LICENSE")
                        archive.write(ROOT / "docs/licenses/Apache-2.0.txt", "META-INF/Apache-2.0.txt")
                        if content is not None:
                            archive.writestr("META-INF/NOTICE", content)
                    if error is None:
                        self.assertEqual(aar, check_publication(group))
                    else:
                        with self.assertRaisesRegex(ValueError, error):
                            check_publication(group)

            pom.write_text(pom_text.replace("GNU General Public License", "GNU Lesser General Public License"), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "GPL-3.0-only license"):
                check_publication(group)
