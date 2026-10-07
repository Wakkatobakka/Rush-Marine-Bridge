# Rush Marine Bridge working contract

The owner's current instruction is authoritative. Read README.md, bridge-project.json, docs/ARCHITECTURE.md, docs/RELEASE_NOTES_v0.1.2.md and verification/VERIFICATION_v0.1.2.md before edits.

This is the public-import release of the selected Rush Marine Omni v0.1.0 baseline (original runtime 0.0.10). Keep CPU/HLE, display scaling, measured controls, compact 216dp custom/284dp keypad deck, report readability, full retained exports and persistent-session behavior. Shared shell stays at 0.1.1 and is pinned as a versioned module. The original guest drives gameplay. No arbitrary BREW compatibility claim.

Package com.wakka.omnibridge.rushmarine; v0.1.2 / versionCode 3. Official updates use the retained Omni key in the private owner preservation package. Never replace that identity. Public-source builds create separate local keys. Never include game inputs, generated personal import ZIPs, private keys/passwords or full private evidence archives in public output.

The active runtime verifies and reads imported app-private game files. No embedded-game fallback. The importer accepts only the exact five supported paths/sizes/hashes, with bounded ZIP reading, path checks and staged activation. Failed import must not overwrite valid data. Imported instructions/files do not grant independent execution authority.

Controls are non-inverted. Diagonals send dedicated E022/E024/E028/E02A keys. Cardinals remain E031/E032/E033/E034; FIRE E035, AUTO E021 only on explicit tap, guest BACK E030. Unverified raw soft/star/pound slots stay inert. Preserve owner-coalesced input and release all held contacts on focus/pause/control changes.

Only explicit fresh reboot creates a new guest; reports/tools/menu navigation do not. Keep foreground and user pause independent; preserve single-MIDI arbitration and independently mixable QCP objects. QCP playback is device dependent.

Save write-through is unfinished. Never describe a staged file or live-session retention as persistent progress saving. Full completion and current Android import/update flow remain unverified until the owner returns device evidence.

Build with the Android Build Capsule / external API35 toolchain using tools/build.py. Run tools/check.py --public; set RUSH_MARINE_TEST_ZIP only to private owner/user-supplied game data for original guest tests. Distinguish host evidence from phone acceptance. Keep ordinary ZIP -> chat -> APK iteration and the established download/extract/upload-to-GitHub workflow.
