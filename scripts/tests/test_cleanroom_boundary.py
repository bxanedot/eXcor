from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class CleanRoomBoundaryTest(unittest.TestCase):
    def test_root_gradle_selects_each_source_set_from_cleanroom(self):
        build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        source_root = re.search(
            r'kotlin\.setSrcDirs\(listOf\("([^"]+)"\)\)', build
        )

        self.assertIsNotNone(source_root)
        self.assertEqual("modules/cleanroom/$name", source_root.group(1))
        self.assertNotIn("modules/legacy", build)

    def test_legacy_tree_is_not_an_included_gradle_project(self):
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        included_projects = re.findall(r'\binclude\s*\(\s*["\']([^"\']+)', settings)

        self.assertNotIn("modules/legacy", settings)
        self.assertNotIn(":legacy", included_projects)


if __name__ == "__main__":
    unittest.main()
