# Rush Marine Bridge v0.1.1

First public BREW release candidate, based on Rush Marine Omni v0.1.0 and the selected Rush Marine 0.0.10 runtime.

- Removes all five original BREW game files from the APK.
- Adds direct local import of a compatible original game ZIP or prepared Omni game-data ZIP.
- Verifies required paths, sizes and SHA-256 hashes before enabling Play.
- Retains the original ARM/Thumb interpreter/HLE, framebuffer, measured controls and MIDI arbitration.
- Retains the larger Omni play area, compact green controls and full report/export flow.
- Uses the player-facing name Rush Marine Bridge and original Wakkan sonar artwork.
- Keeps package `com.wakka.omnibridge.rushmarine` and its official signing certificate; versionCode increases to 2.
- Replaces the staged-data vault presentation with explicit game-data and save-status information.
- Adds an optional local ZIP helper for extracted game files.

Status: pre-release; host verified. New Android import/update/MediaPlayer/lifecycle acceptance and full completion are not claimed. Save write-through remains unfinished.
