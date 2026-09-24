#!/usr/bin/env bash
# 只接受 release 元数据指向的 APK，并核对签名证书和非调试标记。
set -euo pipefail

fail() { echo "::error::$*" >&2; exit 1; }
: "${ANDROID_HOME:?ANDROID_HOME is required}"
: "${SIGN_KEY_STORE_FILE:?SIGN_KEY_STORE_FILE is required}"
: "${SIGN_KEY_STORE_PASSWORD:?SIGN_KEY_STORE_PASSWORD is required}"
: "${SIGN_KEY_ALIAS:?SIGN_KEY_ALIAS is required}"
: "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"

metadata=app/build/outputs/apk/release/output-metadata.json
mapping=app/build/outputs/mapping/release/mapping.txt
[[ -s "$metadata" ]] || fail 'Release output-metadata.json is missing'
[[ -s "$mapping" ]] || fail 'R8 mapping.txt is missing'
jq -e '.variantName == "release" and .applicationId == "app.mystery0.nodeflow" and (.elements | length == 1)' \
  "$metadata" > /dev/null || fail 'Unexpected release metadata or multiple APK outputs'
version_name=$(jq -er '.elements[0].versionName | strings' "$metadata")
output_file=$(jq -er '.elements[0].outputFile | strings' "$metadata")
[[ "$version_name" =~ ^[A-Za-z0-9._-]+$ && "$version_name" == *'.r'* ]] || fail 'Invalid release version name'
[[ "$output_file" != */* && "$output_file" == *.apk && "$output_file" != *unsigned* ]] || fail 'Invalid or unsigned APK output'
apk="$(dirname "$metadata")/$output_file"
[[ -s "$apk" ]] || fail 'Release APK is missing'

apksigner=$(find "$ANDROID_HOME/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner | sort -V | tail -n 1)
[[ -x "$apksigner" ]] || fail 'apksigner is not installed'
apkanalyzer="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
[[ -x "$apkanalyzer" ]] || fail 'apkanalyzer is not installed'
[[ "$("$apkanalyzer" manifest debuggable "$apk")" == false ]] || fail 'APK is debuggable'
[[ "$("$apkanalyzer" manifest application-id "$apk")" == app.mystery0.nodeflow ]] || fail 'Unexpected APK application ID'

# 只导出公钥证书计算摘要，不输出密码、私钥或 keystore 内容。
expected_digest=$(keytool -exportcert -keystore "$SIGN_KEY_STORE_FILE" \
  -storepass:env SIGN_KEY_STORE_PASSWORD -alias "$SIGN_KEY_ALIAS" | sha256sum | awk '{print $1}')
certificates=$("$apksigner" verify --verbose --print-certs "$apk")
actual_digest=$(printf '%s\n' "$certificates" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')
[[ "$actual_digest" == "$expected_digest" ]] || fail 'APK certificate does not match the configured signing key'
[[ "$(printf '%s\n' "$certificates" | grep -c '^Signer #[0-9]* certificate SHA-256 digest: ')" == 1 ]] || fail 'Unexpected number of APK signers'

mkdir -p outputs
apk_path="outputs/NodeFlow-release-${version_name}.apk"
cp "$apk" "$apk_path"
cp "$mapping" "outputs/NodeFlow-${version_name}-mapping.txt"
printf 'versionName=%s\napkPath=%s\n' "$version_name" "$apk_path" >> "$GITHUB_OUTPUT"
echo 'Release APK signature, application ID, debuggable flag and R8 mapping verified'
