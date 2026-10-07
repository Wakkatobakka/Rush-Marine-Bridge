# Architecture

The bridge keeps the selected Rush Marine 0.0.10 CPU/HLE implementation and the Omni 0.1.0 presentation/session integration. The public release changes the source of game data and the standalone front door.

`VerifiedZipStore` is the shared pure-Java import/storage core. `BrewPayload` pins the five supported paths, sizes and SHA-256 identities. The Android launcher opens the system document picker and imports on a worker thread. Successful verification publishes the required files into app-private storage. Failed imports do not replace valid data.

`RuntimeRunner` verifies and reads that private payload, then passes the module/progress/BAR bytes into the existing `BrewRuntime.Session`. There is no game-asset fallback in the public APK.

The original guest drives logic and requests file/resource/display/timer/media services through the title-specific HLE implementation. `BrewSurfaceView` displays the original 128×160 framebuffer. Android MediaPlayer receives the exact preserved MIDI/QCP resource bytes requested by the guest. One active MIDI sequence is permitted; QCP objects remain separate.

`BrewBackend` retains the serialized guest runner, native input ownership, report capture, foreground/explicit-pause gates, and live session across bridge menu/report navigation. Dedicated diagonal keys are retained. No CPU/rendering rewrite or new save implementation is introduced by the import release.

Shared shell source remains at 0.1.1. The public import front door is a title-specific Activity using the same Bridgekeeper UI helpers; no other title's shared module or APK is changed.

`baseline/runtime-source-sha256.json` pins the unchanged CPU/HLE, scaling view, native UI helper and shared module source. Reports retain cumulative counters and disclose native trace trimming. A report snapshot does not initialize or step a new guest.

In v0.1.2, the trusted app-private storage root is canonicalized once to allow OS-owned directory aliases. Payload descendants still require exact canonical/absolute agreement and containment. Cleanup removes redirected entries without following their targets.
