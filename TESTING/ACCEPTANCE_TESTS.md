# Phone acceptance — v0.1.2

The new import/update path is not yet phone-tested. These are the concrete acceptance steps.

1. Install Rush_Marine_Bridge_v0.1.2.apk over Rush Marine Bridge v0.1.1 (or Omni v0.1.0). Use the same PRIVATE phone-test ZIP already downloaded; do not extract it. Confirm it installs as an update and shows the Rush Marine Bridge name/icon.
2. Open the bridge. Before importing, Play is disabled and it asks for game data.
3. Tap IMPORT RUSH MARINE ZIP, select the original compatible ZIP or prepared Omni Rush Marine game-data ZIP, and wait for VERIFIED · READY TO PLAY.
4. Enter the game, answer its sound prompt, start, and check eight-way movement, FIRE, AUTO and BACK with the retained custom controls and raw keypad.
5. Listen through menu-to-game transitions. Check pause/resume, background return, menu return and Report; verify these retain the same live guest.
6. Copy/export a report. Try an unrelated ZIP; the import should fail while the previously verified import remains usable.
7. Force-close and reopen the app. Imported game files should remain verified. Game progress saving is not implemented; a fresh guest starts from its baseline.

Return a report and short recording to record exact device acceptance. A full playthrough remains a separate test. Test Android 8+ devices separately before making a broad compatibility claim.
