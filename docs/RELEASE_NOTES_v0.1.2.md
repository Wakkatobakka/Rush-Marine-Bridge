# Rush Marine Bridge v0.1.2

Fixes the storage-path rejection reported during v0.1.1 phone import testing.

- Resolves the trusted app-storage root before validating game paths, allowing Android directory aliases.
- Keeps ZIP traversal, exact file/hash/size, duplicate, and expansion/entry-limit protections.
- Rejects redirected payload files and removes redirected entries without deleting their targets during repair.
- Adds a regression that reproduced the original error before the fix, plus alias persistence/read and redirect repair checks.
- Retains the package and original Omni signing certificate; versionCode increases to 3.

Use the same compatible game ZIP. Install this APK over v0.1.1, then retry import.

Host checks pass. Successful import and gameplay on the v0.1.2 phone build remain pending. Progress saving across process restarts is still unfinished.
