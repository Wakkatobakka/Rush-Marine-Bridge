# Rush Marine Bridge v0.1.1

Rush Marine Bridge runs a user's own compatible copy of **Mega Man: Rush Marine**, the English BREW 1.1.11 / CDM2030 128×160 release, on modern Android.

This is my first public BREW bridge release, following the DoJa work on Dirge, DeadShot and FFVII Snowboarding. The bridge executes the original ARM/Thumb game module and supplies the phone services it expects.

## Play it

1. Install `Rush_Marine_Bridge_v0.1.1.apk` on Android 8.0 or newer.
2. Open the app and tap **IMPORT RUSH MARINE ZIP**.
3. Select your own compatible original game ZIP. Leave it zipped.
4. Wait for verification, then tap **ENTER RUSH MARINE**.

No PC conversion is required for a compatible original ZIP. The optional Game Data ZIP Helper is for people who only have extracted game files.

## Included

- verified local ZIP import and offline play;
- original ARM/Thumb guest execution and 128×160 framebuffer;
- measured eight-way movement, FIRE/OK, AUTO and BACK;
- custom touch controls and original numeric keypad;
- MIDI/QCP audio request handling;
- pause/resume, readable diagnostics and full captured report exports;
- source/build tooling and public verification records.

## Status

**Pre-release.** The underlying BREW runtime has S25 Ultra / Android 16 gameplay evidence. The new v0.1.1 Android import/update flow is host-verified and still needs a phone acceptance run.

Progress saving between app restarts is not implemented. Full game completion is unverified. QCP playback is device dependent. This release supports the exact documented Rush Marine data set.

The APK uses the same package and certificate as Rush Marine Omni v0.1.0, with a higher versionCode, so it is designed to install as an update to that version. Import your game ZIP once after updating.

## Release downloads

- `Rush_Marine_Bridge_v0.1.1.apk`
- `Rush_Marine_Game_Data_Zip_Helper_v0.1.1.zip` (optional)
- `Rush_Marine_Bridge_v0.1.1_Verification.txt`
- `Rush_Marine_Bridge_v0.1.1_SHA256SUMS.txt`

No original game files or private update key are included. Original game: Capcom. Original platform: Qualcomm BREW. Independent project by Wakkatobakka.
