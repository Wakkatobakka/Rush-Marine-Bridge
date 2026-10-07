# Rush Marine Bridge

![Rush Marine Bridge — BREW compatibility on modern Android](docs/Rush_Marine_Bridge_GitHub.jpg)

Rush Marine Bridge is an independent Android compatibility bridge for running a user's own compatible copy of **Mega Man: Rush Marine**, the English **BREW 1.1.11 / CDM2030 128×160** release.

It executes the original game's **ARM/Thumb module** through a Java interpreter and supplies the BREW services that this title uses. The original game drives the gameplay.

**Current release:** v0.1.2 — pre-release  
**Android:** 8.0 or newer (minimum API 26; device testing has been on S25 Ultra / Android 16)  
**Package:** `com.wakka.omnibridge.rushmarine`

## Play it

1. Download `Rush_Marine_Bridge_v0.1.2.apk` from [Releases](https://github.com/Wakkatobakka/Rush-Marine-Bridge/releases).
2. Install it on your Android phone.
3. Open Rush Marine Bridge and tap **IMPORT RUSH MARINE ZIP**.
4. Select your own compatible preserved BREW game ZIP. Leave it zipped.
5. After verification succeeds, tap **ENTER RUSH MARINE**.

The supported original archive is commonly named `Mega-Man-Rush-Marine_BREW_EN_Capcom-1111.zip`. The name is only a hint: the five required files must match the supported sizes and SHA-256 hashes.

The prepared Rush Marine game-data ZIP from Wakkan Omni Player is also accepted. Import is performed locally on the phone. **No original game files are included in the APK or this repository.** No PC conversion is needed for an already-compatible ZIP.

If you only have extracted game files, the optional **Game Data ZIP Helper** in the release packages them locally. See [game-data instructions](docs/GAME_DATA.md).

## Why Rush Marine Bridge exists

Dirge, DeadShot, and FFVII Snowboarding were about making old DoJa games practical to play on modern Android. Rush Marine carries that bridge work into BREW.

The starting point was a preserved feature-phone package. Getting that package onto a modern phone meant making its original ARM instructions execute, supplying the phone services it expected, recovering its image and audio paths, and working out what its keypad events actually did.

This was developed through repeated chat-based AI iterations and testing on my phone. The reports and recordings mattered: things that looked correct on the host still broke on Android, and several assumed control mappings turned out to be wrong in real gameplay.

The working BREW runtime was then brought into the shared Wakkan/Omni bridge structure: a familiar front door, a larger game view, compact controls, and readable reports. This standalone public release adds local game-data import while retaining that baseline.

The original game and its preservation are separate from my bridge work. My aim here is to make the preserved game usable on modern Android.

## What the bridge provides

- Original 128×160 game framebuffer, scaled without cropping
- Original ARM/Thumb guest execution and the title's measured BREW service calls
- Eight-way touch movement using the actual dedicated diagonal keys
- **FIRE / OK**, explicit **AUTO / 0**, and guest **BACK**
- Original numeric phone keypad reference layout
- MIDI/QCP audio request handling; QCP playback depends on the device decoder
- Pause/resume and a live session retained across bridge menu/report navigation
- Readable diagnostics, full captured report copy/share/export
- Verified local ZIP import and offline play after import

## Status and known limits

**Pre-release.** The original BREW runtime has physical S25 Ultra / Android 16 gameplay evidence. v0.1.1 phone testing exposed a storage-path rejection during import. v0.1.2 fixes the reproduced storage-alias case; successful phone import and gameplay on this build remain pending.

- **Progress saving between app restarts is not implemented.** The supplied progress baseline is read at a fresh boot. Returning from the bridge menu or reports resumes the live session while the app process remains alive.
- A complete start-to-finish playthrough has not been verified.
- QCP sound-effect playback is device dependent; every audio resource has not been individually device-validated.
- Only the exact supported Rush Marine data set is accepted. This is a title-specific runtime, not a claim of general BREW game support.
- Unverified raw soft-key, `*`, and `#` slots are inert.

See [verification](verification/VERIFICATION_v0.1.2.md) for the separation between historical phone evidence and current host tests.

## Updating from Rush Marine Omni

v0.1.2 keeps the Omni v0.1.0 package and official update certificate, and uses `versionCode` 3. It is designed to install over Omni v0.1.0 or Bridge v0.1.1. The displayed app name changes to **Rush Marine Bridge**.

The game is now user-supplied, so import your compatible game ZIP once after the update. The older `com.wakka.rushbridge` app is a separate installation.

## Controls

| Control | Native behavior |
| --- | --- |
| Touch pad | Eight directions; diagonals use dedicated 1/3/7/9 handset keys |
| FIRE / OK | Fire while held; confirm in menus |
| AUTO / 0 | Toggle auto-fire on an explicit tap |
| BACK | Original game's back/pause/menu action |
| Toolbar Pause | Pause/resume the host session |
| Toolbar Keypad | Switch between custom controls and original numeric keypad |
| Toolbar Report | Open readable reports without restarting the guest |
| Android Back / Menu | Return to the bridge; retain the live session |

## Repository layout

- `app/` — standalone launcher, manifest and original bridge artwork
- `shared/` — retained Wakkan bridge shell 0.1.1
- `profiles/rushmarine/` — controls, session adapter and verified game-data import
- `runtime/rushmarine/` — original BREW interpreter/HLE and Android rendering/audio host
- `payload-builder/` — optional local ZIP helper for extracted game files
- `tests/` — import-policy, input-ownership and original-guest probes
- `tools/` — offline APK build and release checks
- `verification/` — public verification summaries and selected host receipts
- `docs/` — build, game-data, architecture, development history and release notes

See [BUILDING.md](docs/BUILDING.md). The official update key is private. A local source build creates its own signing identity and cannot update an official APK with a different certificate.

## Credits and source

Bridge direction, iteration, integration and phone testing: **Wakkatobakka**.

Original game: **Capcom**. Original platform: **Qualcomm BREW**. The development input was an owner-supplied preserved English BREW package; this release does not claim to have recovered the original game. No individual recovery attribution is established by the supplied package, so none is invented here.

Rush Marine Bridge is independent and is not affiliated with or endorsed by Capcom or Qualcomm. See [third-party notices](THIRD-PARTY-NOTICES.txt).

No repository-wide license has been selected. Source is published for inspection; this release supplies no additional license grant.
