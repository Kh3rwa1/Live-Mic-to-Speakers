#!/usr/bin/env bash
# CI-only: avdmanager <20 writes android-0 for Android 17's Major.Minor packages.
# Keep a known compatible parser in the `latest` path used by emulator-runner.
set -euo pipefail
: "${ANDROID_HOME:?Error: ANDROID_HOME is required}"
: "${RUNNER_TEMP:?Error: RUNNER_TEMP is required}"
: "${GITHUB_PATH:?Error: GITHUB_PATH is required}"
version=20.0
current="$ANDROID_HOME/cmdline-tools/$version"
latest="$ANDROID_HOME/cmdline-tools/latest"
installer=$(command -v sdkmanager || true)
if [ -z "$installer" ]; then installer="$latest/bin/sdkmanager"; fi
if [ ! -x "$installer" ]; then
  echo 'Error: SDK bootstrap manager is unavailable' >&2
  exit 1
fi
"$installer" --install "cmdline-tools;$version"
if [ ! -x "$current/bin/sdkmanager" ] || [ ! -x "$current/bin/avdmanager" ]; then
  echo "Error: command-line tools $version were not installed correctly" >&2
  exit 1
fi
if ! grep -Eq '^Pkg.Revision[[:space:]]*=[[:space:]]*20([.][0-9]+)*[[:space:]]*$' "$current/source.properties"; then
  echo "Error: unexpected command-line tools revision" >&2
  exit 1
fi
# Preserve, rather than delete, the runner's old directory. Merely adding PATH is
# insufficient: emulator-runner prepends cmdline-tools/latest again.
if [ "$(readlink -f "$latest")" != "$(readlink -f "$current")" ]; then
  if [ -e "$latest" ] || [ -L "$latest" ]; then
    previous=$(mktemp -d "$RUNNER_TEMP/android-command-line-tools.XXXXXX")
    mv "$latest" "$previous/latest"
  fi
  ln -s "$current" "$latest"
fi
printf '%s\n' "$latest/bin" >> "$GITHUB_PATH"
"$latest/bin/sdkmanager" --version
