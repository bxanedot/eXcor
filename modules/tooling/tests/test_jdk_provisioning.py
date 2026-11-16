"""Opt-in real Gradle settings checks; no SDK code or external fixture is executed."""
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


@unittest.skipUnless(os.environ.get("EXCOR_SETTINGS_SMOKE") == "1", "real Gradle settings check is opt-in")
class JdkProvisioningTests(unittest.TestCase):
    def run_settings(self, installed):
        properties = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
        distribution = re.search(r"gradle-[0-9.]+-bin(?=\.zip)", properties).group()
        home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
        binaries = list((home / "wrapper/dists" / distribution).glob("*/gradle-*/bin/gradle"))
        self.assertEqual(1, len(binaries), "Run the repository wrapper once before this check")
        with tempfile.TemporaryDirectory(prefix="excor-settings-") as temporary:
            fixture = Path(temporary)
            (fixture / "settings.gradle.kts").write_text((ROOT / "settings.gradle.kts").read_text())
            (fixture / "build.gradle.kts").write_text("")
            for project in ("android", "desktop"):
                (fixture / "modules/platforms/preview" / project).mkdir(parents=True)
            probe = fixture / "probe.gradle"
            probe.write_text("""settingsEvaluated { settings ->
                def installed = gradle.startParameter.projectProperties['excor.autoProvisionJdk'] == 'false'
                assert settings.pluginManager.hasPlugin('org.gradle.toolchains.foojay-resolver-convention') == !installed
                if (installed) assert settings.buildscript.configurations.classpath.allDependencies.empty
                println('EXCOR_SETTINGS_VERIFIED')
            }
            """)
            command = [str(binaries[0]), "--no-daemon", "--console=plain", "-p", str(fixture), "-I", str(probe), "help"]
            if installed:
                command += ["--offline", "--gradle-user-home", str(fixture / "empty-gradle-home"), "-Pexcor.autoProvisionJdk=false"]
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            try:
                output, _ = process.communicate(timeout=180)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.communicate(timeout=10)
                self.fail("Settings fixture timed out")
            # Only fixed diagnostics leave this fixture; dependency errors may contain local details.
            self.assertEqual(0, process.returncode, "Settings fixture failed; no build output is echoed")
            self.assertIn(b"EXCOR_SETTINGS_VERIFIED", output)

    def test_installed_mode_needs_no_plugin_artifacts_or_network(self):
        self.run_settings(installed=True)

    def test_default_mode_still_applies_the_pinned_provisioner(self):
        self.run_settings(installed=False)
