# Shipaton dev environment: Java (Android Studio's bundled JBR) + Android SDK.
# Safe to source multiple times (idempotent PATH handling).
# Usage: source /mnt/drive/Work/Project/Shipaton/env.sh

export JAVA_HOME="/opt/android-studio/jbr"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"

_shipaton_prepend_path() {
  case ":$PATH:" in
    *":$1:"*) ;;
    *) PATH="$1:$PATH" ;;
  esac
}
_shipaton_prepend_path "$ANDROID_HOME/platform-tools"
_shipaton_prepend_path "$ANDROID_HOME/cmdline-tools/latest/bin"
_shipaton_prepend_path "$JAVA_HOME/bin"
unset -f _shipaton_prepend_path
export PATH
