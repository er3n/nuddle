#!/usr/bin/env bash
# The two E2E emulators (docs/testing.md, E2E): emulator-5554 is Selin, emulator-5556 is Eren.
# Stock Android with Google Play (FCM needs it), hardware profile of a Galaxy S23 (NUD-10).
#   emulators.sh create          create both AVDs if missing
#   emulators.sh boot [--cold]   create, then boot both headless and wait; --cold wipes their data
#   emulators.sh stop            shut both down
# Idempotent: a running emulator is reused. Needs ANDROID_HOME (mise.toml sets it).
set -euo pipefail

: "${ANDROID_HOME:?ANDROID_HOME is not set; run through mise}"
IMAGE="system-images;android-36;google_apis_playstore;arm64-v8a"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
BOOT_TIMEOUT_S=300

# role:avd:port
DEVICES=("selin:nuddle-selin:5554" "eren:nuddle-eren:5556")

create_one() {
  local avd=$1
  if [[ -d "$AVD_HOME/$avd.avd" ]]; then return; fi
  echo "creating AVD $avd"
  echo no | "$AVDMANAGER" create avd --name "$avd" --package "$IMAGE" --force >/dev/null
  # Galaxy S23: 1080 x 2340 px, 480 dpi (about 360 x 780 dp). Published values, not measured.
  cat >>"$AVD_HOME/$avd.avd/config.ini" <<EOF
hw.lcd.width=1080
hw.lcd.height=2340
hw.lcd.density=480
hw.ramSize=2048
hw.keyboard=yes
hw.audioInput=no
EOF
}

running() {
  "$ADB" -s "emulator-$1" get-state 2>/dev/null | grep -q device
}

boot_one() {
  local avd=$1 port=$2 cold=$3
  if running "$port"; then
    echo "emulator-$port ($avd) already running"
    return
  fi
  local flags=(-avd "$avd" -port "$port" -no-window -no-audio -no-boot-anim -no-snapshot-save)
  if [[ $cold == 1 ]]; then flags+=(-wipe-data); fi
  echo "booting emulator-$port ($avd)"
  nohup "$EMULATOR" "${flags[@]}" >"${TMPDIR:-/tmp}/nuddle-$avd.log" 2>&1 &
}

wait_one() {
  local port=$1 serial="emulator-$1" waited=0
  "$ADB" -s "$serial" wait-for-device
  until [[ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do
    sleep 2
    waited=$((waited + 2))
    if ((waited > BOOT_TIMEOUT_S)); then
      echo "$serial did not boot within ${BOOT_TIMEOUT_S}s" >&2
      exit 1
    fi
  done
  # Animations make Maestro slower and flakier.
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$ADB" -s "$serial" shell settings put global "$s" 0
  done
  "$ADB" -s "$serial" shell svc power stayon true
  echo "$serial booted"
}

cmd=${1:-}
cold=0
if [[ ${2:-} == --cold ]]; then cold=1; fi

case $cmd in
  create)
    for d in "${DEVICES[@]}"; do IFS=: read -r _ avd _ <<<"$d"; create_one "$avd"; done
    ;;
  boot)
    for d in "${DEVICES[@]}"; do
      IFS=: read -r _ avd port <<<"$d"
      create_one "$avd"
      if [[ $cold == 1 ]] && running "$port"; then "$ADB" -s "emulator-$port" emu kill; sleep 3; fi
      boot_one "$avd" "$port" "$cold"
    done
    for d in "${DEVICES[@]}"; do IFS=: read -r _ _ port <<<"$d"; wait_one "$port"; done
    ;;
  stop)
    for d in "${DEVICES[@]}"; do
      IFS=: read -r _ _ port <<<"$d"
      if running "$port"; then "$ADB" -s "emulator-$port" emu kill; fi
    done
    ;;
  *)
    echo "usage: $0 create | boot [--cold] | stop" >&2
    exit 2
    ;;
esac
