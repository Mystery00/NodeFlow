#!/usr/bin/env bash
# 在一次性模拟器上黑盒验证最终 APK；断网以避免访问真实 V2EX。
set -euo pipefail
fail() { echo "::error::$*" >&2; exit 1; }
apk=${1:?Usage: smoke-release-apk.sh APK}
[[ -s "$apk" ]] || fail 'Release APK is missing'
package=app.mystery0.nodeflow
component="$package/.MainActivity"
adb wait-for-device
adb shell svc wifi disable
adb shell svc data disable
adb install --no-streaming "$apk"

assert_running() {
  local pid activities
  pid=$(adb shell pidof "$package" | tr -d '\r')
  [[ -n "$pid" && "$pid" == "$initial_pid" ]] || fail 'App exited or restarted during the smoke test'
  # 项目有独立的 :crash 进程，不能把错误页当成成功启动。
  if adb shell pidof "$package:crash" > /dev/null; then
    fail 'CrashActivity is running'
  fi
  activities=$(adb shell dumpsys activity activities)
  grep -Eq '(mResumedActivity|topResumedActivity).*app\.mystery0\.nodeflow/\.MainActivity' <<< "$activities" \
    || fail 'MainActivity is not resumed'
}

launch() {
  local result
  result=$(adb shell am start -W -n "$component" "$@")
  grep -q '^Status: ok' <<< "$result" || fail 'Activity launch failed'
  initial_pid=$(adb shell pidof "$package" | tr -d '\r')
  [[ -n "$initial_pid" ]] || fail 'App did not start'
  for ((attempt = 0; attempt < 10; attempt++)); do
    sleep 1
    assert_running
  done
}

# 首次启动、保留数据的冷启动和三个页面的离线深链。
adb shell am force-stop "$package"
launch -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
adb shell am force-stop "$package"
launch -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
for path in t/1 go/android member/nodeflow_ci; do
  launch -a android.intent.action.VIEW -d "https://www.v2ex.com/$path"
done
echo 'Minified release APK installation, cold starts and offline deep links passed'
