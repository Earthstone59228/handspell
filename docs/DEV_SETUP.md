# Dev environment setup (Arch Linux box)

This documents the machine-level setup done outside the repo: JDK, Android SDK,
env vars, uv/Python, and the phone used for on-device testing. It does not
cover the Gradle project itself (created separately).

## Java

No system JDK is installed. We use Android Studio's bundled JetBrains Runtime
(JBR), which is a real OpenJDK build, as `JAVA_HOME`:

```
JAVA_HOME=/opt/android-studio/jbr
```

Verify: `java -version` → OpenJDK 25.0.3.

## Android SDK

SDK root: `~/Android/Sdk` (`ANDROID_HOME` / `ANDROID_SDK_ROOT`).

Installed cmdline-tools (`latest`) from Google's official zip:

```
curl -fL -o commandlinetools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
# unzip so the inner "cmdline-tools" dir becomes ~/Android/Sdk/cmdline-tools/latest
```

Accepted all licenses non-interactively:

```
yes | sdkmanager --licenses
```

Installed/confirmed packages (via `sdkmanager --list_installed`):

- `platform-tools` 37.0.1
- `build-tools;36.0.0` and `build-tools;37.0.0` (current stable)
- `platforms;android-36` and `platforms;android-37.0` (kept, was pre-installed)
- `sources;android-37.0`
- `emulator` 37.1.11
- `cmdline-tools;latest`

Note: `sdkmanager` now prints a deprecation notice and delegates to a newer
"Android CLI" tool it downloads on first run; this is expected and harmless.

## Environment variables

Single source of truth: `env.sh` at the repo root (sourceable, idempotent):

```bash
source /mnt/drive/Work/Project/Shipaton/env.sh
```

It exports:

```
JAVA_HOME=/opt/android-studio/jbr
ANDROID_HOME=$HOME/Android/Sdk
ANDROID_SDK_ROOT=$HOME/Android/Sdk
PATH prepended with: $ANDROID_HOME/platform-tools, $ANDROID_HOME/cmdline-tools/latest/bin, $JAVA_HOME/bin
```

### Why both `.bashrc` and `.bash_profile` were edited

`bash -c '...'` (non-login, non-interactive) does not source any startup file
by default. `bash -lc '...'` (login) sources `/etc/profile` then
`~/.bash_profile`, which conditionally sources `~/.bashrc` — but `~/.bashrc`
immediately `return`s for non-interactive shells (its very first line is
`[[ $- != *i* ]] && return`), so anything placed only in `.bashrc` never runs
under `bash -lc`.

So the marker block was added in **both** files:

- `~/.bash_profile` — sources `env.sh` unconditionally (before the `.bashrc`
  call), so `bash -lc` picks it up directly, and also exports `BASH_ENV`
  pointing at `env.sh` so that any plain `bash -c` spawned as a *child* of a
  login/interactive shell also sources it (bash honors an inherited
  `BASH_ENV` for non-interactive non-login shells).
- `~/.bashrc` — same block, for ordinary interactive terminals, plus
  re-exporting `BASH_ENV` there too.

Both blocks are wrapped in `# >>> shipaton android/java env >>>` /
`# <<< shipaton android/java env <<<` markers.

Caveat: a `bash -c` invoked with a **completely fresh environment** that has
no ancestor shell which ever exported `BASH_ENV` (e.g. `env -i bash -c ...`)
will not source anything — this is a fundamental bash limitation, not
specific to this setup.

Verified:
```
bash -lc 'java -version; sdkmanager --version; adb devices'      # works
bash -c 'java -version; sdkmanager --version; adb devices'       # works when run from a normal login/interactive shell (inherits BASH_ENV)
```

## Phone

Samsung SM-S938B (Galaxy S25 Ultra), serial `R5GL14MCNJN`, connected over USB.
Recorded 2026-09-13: Android 16, API level 36, security patch 2026-07-05, front camera present
(`dumpsys media.camera` lists Facing: Front). It re-enumerated once during setup and dropped to
`unauthorized`; if that happens, `adb kill-server` does not help — the phone must be unlocked and
replugged so the "Allow USB debugging?" dialog appears again.

```
adb devices          # should show "device" (not "unauthorized"/"offline")
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
```

If it shows `unauthorized`: unlock the phone and tap "Allow" on the
"Allow USB debugging?" dialog (check "always allow from this computer" to
avoid repeating this). If it shows nothing at all, check the USB cable/
port and that USB debugging is enabled in Developer Options.

## Python / training pipeline

Managed with `uv` (installed to `~/.local/bin` via the official installer,
`curl -LsSf https://astral.sh/uv/install.sh | sh`).

Project: `training/` — `pyproject.toml` pins `requires-python = ">=3.12,<3.13"`
(the newest CPython with MediaPipe wheels; MediaPipe 1.0.1 only ships
`cp3.9`–`cp3.12` classifiers). Dependencies: `mediapipe`, `numpy`,
`scikit-learn`, plus `pytest` in the `dev` dependency group.

```bash
cd training
uv sync --all-groups        # creates .venv, installs everything from uv.lock
uv run python --version     # 3.12.14
uv run scripts/smoke_landmarker.py
uv run pytest
```

The MediaPipe HandLandmarker model (`training/models/hand_landmarker.task`,
the "full"/float16 variant) is documented with its URL/sha256/license in
`training/models/README.md`.

### Known quirk: MediaPipe/TFLite crashes under desktop-session env vars

On this box, constructing a `HandLandmarker` gets silently `SIGKILL`ed
(exit 137, no kernel OOM/journal trace) when common Wayland/X11/D-Bus/
terminal-session environment variables (`XDG_*`, `WAYLAND_DISPLAY`,
`DBUS_SESSION_BUS_ADDRESS`, `HYPRLAND_*`, `KITTY_*`, `DISPLAY`, `TERM`, ...)
are present — reproduced by bisecting environment variables one at a time; a
fully clean environment (`env -i`) never crashes. Root cause is unclear (no
kernel OOM-kill, no Xid, no journald error at all — looks like an external
kill, possibly from one of this machine's custom session daemons, not from
MediaPipe/TFLite itself), and is out of scope to chase further here. The
workaround — strip that handful of session env vars in-process before
`import mediapipe` — is applied at the top of
`training/scripts/smoke_landmarker.py`; do the same in any other script that
constructs a MediaPipe task. This is unrelated to the JAVA_HOME/ANDROID_HOME
setup above.
