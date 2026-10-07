# Building Rush Marine Bridge

Requirements: Python 3, Java 17+ including the `jdk.compiler` module, Android API 35 `android.jar`, and Android Build-Tools 35.0.0 for your host operating system.

The existing Android Build Capsule can supply those Android tools. This public repository does not bundle the SDK or build-tool binaries. No Gradle download or network package resolution is needed once the tools are available.

Set `ANDROID_JAR` to `platforms/android-35/android.jar` and `ANDROID_BUILD_TOOLS` to your host's `build-tools/35.0.0` directory, then run:

```text
python tools/build.py
python tools/check.py --public
```

The output is `dist/Rush_Marine_Bridge_v0.1.2.apk`. The build fingerprints Java source, app resources/assets, the manifest and project configuration into `assets/bridge-build.json` inside the APK.

## Signing

The public source has no official signing key or signing passwords. An initial local build creates a private `signing/` directory for that source checkout. Keep it if you want later builds to update your local build. Do not upload it.

If identity metadata already exists but its key is missing, the build stops rather than silently generating a different update key.

The official v0.1.2 build retains the Rush Marine Omni v0.1.0 signing certificate. Public-source builds use their own certificate; they are separate from official APK updates.

## Host checks

Default checks compile and run the real ZIP importer against synthetic fixtures, exercise input ownership/pause behavior, inspect APK provenance, and enforce the public payload boundary.

To also run the original guest using your own game ZIP, set `RUSH_MARINE_TEST_ZIP` to its absolute path before running the checks. These tests use the same import/storage code compiled into the APK, then run boot, framebuffer, held-key, eight-direction, snapshot and MIDI-request probes against those imported bytes.

These JVM tests do not exercise Android's document picker, MediaPlayer decoder or Activity lifecycle on a physical phone. See `TESTING/ACCEPTANCE_TESTS.md` for that run.
