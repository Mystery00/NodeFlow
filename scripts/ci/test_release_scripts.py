"""使用本地替身验证发布门禁，不访问网络、设备或正式签名。"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPTS = Path(__file__).resolve().parent
PACKAGE = "app.mystery0.nodeflow"
VERSION = "0.1.0.r123.abcdef01"


class ReleaseScriptsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.sdk = self.root / "sdk"
        self.env = dict(os.environ, PATH=f"{self.bin}{os.pathsep}{os.environ['PATH']}")
        self.env.update(
            ANDROID_HOME=str(self.sdk),
            SIGN_KEY_STORE_FILE=str(self.root / "test.jks"),
            SIGN_KEY_STORE_PASSWORD="test-only-password",
            SIGN_KEY_ALIAS="test-only-alias",
            GITHUB_OUTPUT=str(self.root / "github-output"),
            FAKE_CERT_DIGEST=hashlib.sha256(b"test-public-certificate").hexdigest(),
            FAKE_ADB_LOG=str(self.root / "adb.log"),
        )
        self.metadata = self.root / "app/build/outputs/apk/release/output-metadata.json"
        self.metadata.parent.mkdir(parents=True)
        self.data = {
            "variantName": "release",
            "applicationId": PACKAGE,
            "elements": [{"versionName": VERSION, "outputFile": "app-release.apk"}],
        }
        self.write_metadata()
        self.apk = self.metadata.parent / "app-release.apk"
        self.apk.write_bytes(b"fake-apk")
        self.mapping = self.root / "app/build/outputs/mapping/release/mapping.txt"
        self.mapping.parent.mkdir(parents=True)
        self.mapping.write_text("example.Class -> a:\n")
        self.write_tool(self.bin / "keytool", '''#!/usr/bin/env bash
[[ "${FAKE_KEYTOOL_FAIL:-0}" == 0 ]] || exit 1
printf test-public-certificate
''')
        self.write_tool(self.sdk / "build-tools/37.0.0/apksigner", '''#!/usr/bin/env bash
[[ "${FAKE_VERIFY_FAIL:-0}" == 0 ]] || exit 1
printf 'Signer #1 certificate SHA-256 digest: %s\n' "$FAKE_CERT_DIGEST"
if [[ "${FAKE_MULTI_SIGNER:-0}" == 1 ]]; then
  printf 'Signer #2 certificate SHA-256 digest: %s\n' "$FAKE_CERT_DIGEST"
fi
''')
        self.write_tool(self.sdk / "cmdline-tools/latest/bin/apkanalyzer", '''#!/usr/bin/env bash
case "$2" in
  debuggable) echo "${FAKE_DEBUGGABLE:-false}" ;;
  application-id) echo "${FAKE_PACKAGE:-app.mystery0.nodeflow}" ;;
  *) exit 1 ;;
esac
''')
        self.write_tool(self.bin / "sleep", "#!/usr/bin/env bash\nexit 0\n")
        self.write_tool(self.bin / "adb", '''#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_ADB_LOG"
case "$*" in
  wait-for-device|"shell svc wifi disable"|"shell svc data disable"|"shell am force-stop "*) ;;
  install*) [[ "${FAKE_INSTALL_FAIL:-0}" == 0 ]] || exit 1 ;;
  "shell am start "*) echo "Status: ${FAKE_LAUNCH_STATUS:-ok}" ;;
  "shell pidof app.mystery0.nodeflow:crash")
    [[ "${FAKE_CRASH:-0}" == 1 ]] || exit 1
    echo 999 ;;
  "shell pidof app.mystery0.nodeflow")
    [[ "${FAKE_NO_PROCESS:-0}" == 0 ]] || exit 1
    if [[ "${FAKE_RESTART:-0}" == 1 ]]; then
      count_file="${FAKE_ADB_LOG}.count"
      count=0; [[ ! -f "$count_file" ]] || read -r count < "$count_file"
      echo $((count + 1)) > "$count_file"
      echo $((1234 + count))
    else
      echo 1234
    fi ;;
  "shell dumpsys activity activities")
    echo "mResumedActivity: ActivityRecord{0 u0 ${FAKE_ACTIVITY:-app.mystery0.nodeflow/.MainActivity} t1}" ;;
  *) exit 2 ;;
esac
''')

    @staticmethod
    def write_tool(path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        path.chmod(0o755)

    def write_metadata(self):
        self.metadata.write_text(json.dumps(self.data))

    def run_script(self, name, *args, success=True):
        result = subprocess.run(
            ["bash", str(SCRIPTS / name), *map(str, args)],
            cwd=self.root, env=self.env, capture_output=True, text=True, timeout=10,
        )
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
            if name == "verify-release-apk.sh":
                self.assertFalse((self.root / "outputs").exists())
        self.assertNotIn("test-only-password", result.stdout + result.stderr)
        return result

    def test_valid_release_collects_exact_apk_and_mapping(self):
        self.run_script("verify-release-apk.sh")
        self.assertEqual((self.root / f"outputs/NodeFlow-release-{VERSION}.apk").read_bytes(), b"fake-apk")
        self.assertEqual((self.root / f"outputs/NodeFlow-{VERSION}-mapping.txt").read_bytes(), self.mapping.read_bytes())
        self.assertIn(f"versionName={VERSION}\n", (self.root / "github-output").read_text())

    def test_rejects_missing_mapping(self):
        self.mapping.unlink()
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_missing_metadata(self):
        self.metadata.unlink()
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_missing_apk(self):
        self.apk.unlink()
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_wrong_variant(self):
        self.data["variantName"] = "debug"
        self.write_metadata()
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_multiple_outputs(self):
        self.data["elements"] *= 2
        self.write_metadata()
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_unsigned_filename(self):
        self.data["elements"][0]["outputFile"] = "app-release-unsigned.apk"
        self.write_metadata()
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_path_traversal(self):
        self.data["elements"][0]["outputFile"] = "../debug/app-debug.apk"
        self.write_metadata()
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_output_injection(self):
        self.data["elements"][0]["versionName"] = VERSION + "\ninjected=value"
        self.write_metadata()
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_debuggable_apk(self):
        self.env["FAKE_DEBUGGABLE"] = "true"
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_wrong_apk_package(self):
        self.env["FAKE_PACKAGE"] = "example.other"
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_wrong_certificate(self):
        self.env["FAKE_CERT_DIGEST"] = "0" * 64
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_multiple_signers(self):
        self.env["FAKE_MULTI_SIGNER"] = "1"
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_signature_verification_failure(self):
        self.env["FAKE_VERIFY_FAIL"] = "1"
        self.run_script("verify-release-apk.sh", success=False)

    def test_rejects_keytool_failure(self):
        self.env["FAKE_KEYTOOL_FAIL"] = "1"
        self.run_script("verify-release-apk.sh", success=False)

    def test_smoke_runs_offline_cold_starts_and_deep_links(self):
        self.run_script("smoke-release-apk.sh", self.apk)
        calls = (self.root / "adb.log").read_text()
        self.assertLess(calls.index("shell svc wifi disable"), calls.index("install"))
        self.assertLess(calls.index("shell svc data disable"), calls.index("install"))
        self.assertEqual(calls.count("shell am force-stop"), 2)
        self.assertEqual(calls.count("shell am start"), 5)
        for path in ("t/1", "go/android", "member/nodeflow_ci"):
            self.assertIn(f"https://www.v2ex.com/{path}", calls)

    def test_smoke_rejects_install_failure(self):
        self.env["FAKE_INSTALL_FAIL"] = "1"
        self.run_script("smoke-release-apk.sh", self.apk, success=False)

    def test_smoke_rejects_launch_failure(self):
        self.env["FAKE_LAUNCH_STATUS"] = "timeout"
        self.run_script("smoke-release-apk.sh", self.apk, success=False)

    def test_smoke_rejects_missing_process(self):
        self.env["FAKE_NO_PROCESS"] = "1"
        self.run_script("smoke-release-apk.sh", self.apk, success=False)

    def test_smoke_rejects_crash_screen(self):
        self.env["FAKE_CRASH"] = "1"
        self.run_script("smoke-release-apk.sh", self.apk, success=False)

    def test_smoke_rejects_background_activity(self):
        self.env["FAKE_ACTIVITY"] = "com.android.launcher/.Launcher"
        self.run_script("smoke-release-apk.sh", self.apk, success=False)

    def test_smoke_rejects_process_restart(self):
        self.env["FAKE_RESTART"] = "1"
        self.run_script("smoke-release-apk.sh", self.apk, success=False)


if __name__ == "__main__":
    unittest.main()
