# Game data

The APK contains the bridge only. Obtain your own compatible preserved **English BREW 1.1.11, CDM2030 128×160** Rush Marine package.

## Direct import on Android

Leave the original ZIP zipped. Open Rush Marine Bridge, select **IMPORT RUSH MARINE ZIP**, choose the file, wait for **VERIFIED · READY TO PLAY**, then select **ENTER RUSH MARINE**.

Accepted layouts include the original wrapper folder, files at the ZIP root, and the prepared Omni layout under `content/brew/`. Each required file is matched by its complete relative path. Duplicate copies of a required file are rejected.

| Required file | Bytes | SHA-256 |
| --- | ---: | --- |
| 277700/mmassault.mod | 320356 | `e6335ec88581199227aeaba40412bd53c61ed4c80f95b07c02d575b36c156840` |
| 277700/mmassault.sig | 2748 | `3f5926a30d48fd81d9551af402b58e0447cea0a73529fa4289b6fcb0fdbad5c1` |
| 277700/mmassaultbacksmall.bar | 200931 | `350b5dfe8492bf27b1c7f6c2366e8df7985defd1793e4a5f6d75f6fe8f7260d9` |
| 277700/progress.bin | 29 | `11e431c215c5bd334cecbd43148274edf3ffdbd6cd6479fe279577fbe5f52ce6` |
| 277700.mif | 10066 | `3e8dbf746eba5e25a4f33791b81b71c37fbcecb04a822c3cf2f680dcc3d5de6a` |

No conversion of the original ARM module is required. The `.mod`, `.bar`, `.sig`, `.mif`, and original `progress.bin` remain unchanged.

## Extracted files

If the package is already extracted, use the optional `Rush_Marine_Game_Data_Zip_Helper_v0.1.2.zip` from Releases. It requires Python 3, but no Android SDK, Java, or external Python packages.

On Windows, extract the helper and double-click `RUN-GAME-DATA-ZIP-HELPER-WINDOWS.bat`. Select the folder containing `277700.mif` and the `277700` folder. Copy the resulting `Rush_Marine_Data_for_Bridge_v0.1.2.zip` to your phone and import it normally.

The helper validates the same five file sizes and hashes as the Android app. It does not download or upload anything.

## Import behavior

The Android importer streams the selected ZIP through a bounded parser. It rejects unsafe paths, duplicate required files, missing files, incorrect sizes/hashes, excessive entry counts and excessive expansion. Only verified required files enter app-private storage. A failed import keeps any previously verified dataset.

The active files are checked again at startup and before their bytes are supplied to the guest. Other archive contents are not installed. No broad storage or internet permission is used.

Import limits: 512 entries, 16 MiB of compressed bytes read, and 16 MiB of expanded entry content. This importer is for the original game/data ZIP, not a large preservation/source archive.

## Save boundary

The importer treats `progress.bin` as the exact supported original seed. It does not import arbitrary modified saves. Runtime write-through saving remains unfinished. A fresh guest starts from the original progress baseline; a live session survives bridge menu/report navigation until process loss or an explicit fresh reboot.
