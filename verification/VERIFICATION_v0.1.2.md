# Rush Marine Bridge v0.1.2 verification

Current status: **storage-alias fix host verified; v0.1.2 phone acceptance pending**.

v0.1.1 phone screenshots show the correct ZIP selected, then "Unsafe game-data storage path". Its canonical-versus-absolute comparison rejected an OS-owned app-storage alias. The same error was reproduced with a host symlink before the fix. v0.1.2 canonicalizes the trusted app-storage root once and retains strict checks on payload descendants. This device-specific cause is inferred from the error and the reproduced alias case; the corrected APK still needs a phone retry.

## Built artifact

- Package: `com.wakka.omnibridge.rushmarine`
- Version: 0.1.2 / versionCode 3
- APK bytes: 250631
- APK SHA-256: `155ede709b35fd698c2cfd2df4e6894cdc78308707d92cd3cc68e16a88264150`
- Certificate SHA-256: `39eee91824b7870d8d58be89c7a4a21fa77fe6b6921fd84b15e01e0834096d11`
- Signature schemes v2 and v3 verify; zip alignment check passes.
- Certificate matches Rush Marine Omni v0.1.0. Its versionCode was 1.

## Current executed checks

- 56 importer assertions, including trusted Android-style storage aliases, rejection of redirected payload files, safe repair without deleting redirect targets: raw/wrapped/Omni layouts, persisted verification, malformed/missing/altered/oversized inputs, duplicate files, unsafe paths, entry/expansion limits, directory expansion limits, corrupted storage detection/repair, and failed-import preservation.
- 16 native-input ownership and pause assertions.
- Exact real game ZIP imports pass for the original archive, prepared Omni game-data ZIP and ZIP produced by the optional helper. All five imported bytes/sizes/hashes match the supported input identity.
- The local helper creates an importable ZIP and rejects missing/modified inputs while retaining any previous output.
- Original guest boot, 128×160 framebuffer, held-key/release, all eight movement directions, snapshot/no-reset, MIDI media requests and startup overlap probes pass using files read from the new verified store.
- Public source/APK checks find no original game payload, renamed original payload, official signing key or signing credentials in the published material.
- CPU/HLE, scaling/native UI helpers and the shared shell match their pinned baseline hashes.
- Independent public-source rebuild matches every non-signature APK entry byte, using a separate local certificate.

These are host/JVM checks. The supplied v0.1.1 phone screenshots confirm launcher/install/file-picker access and the failed import; no v0.1.2 Android device or emulator run was available for this pass.

## Historical physical evidence

The retained v0.0.9 S25 Ultra / Android 16 report records 454,759,078 guest instructions, 11,121 display updates, 616 key events, 18 PNG decodes with zero failures, zero unhandled interface calls during that captured run, and four Android audio starts with zero recorded failures. The private preservation package retains that report and recording through the original development archive.

This evidence establishes that earlier physical run. It does not promote the new Android importer or certify every game level/audio file.

## Outstanding phone scope

v0.1.2 update/install, successful phone import, imported game startup, Android audio decoder output, lifecycle/report UI acceptance, other Android models and a full playthrough remain device checks. See `TESTING/ACCEPTANCE_TESTS.md`.

Save write-through remains unfinished. Live-session resume is not saving progress across process restarts. QCP playback is device dependent; all QCP resources have not been individually device-validated.

No general BREW compatibility claim is made.
