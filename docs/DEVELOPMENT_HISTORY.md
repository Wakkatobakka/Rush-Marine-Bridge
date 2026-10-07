# Making the BREW game playable

The input was the preserved Capcom English BREW 1.1.11 package for a 128×160 CDM2030 handset. It contains an original ARM/Thumb executable, BREW metadata, signatures, BAR resources and a small progress baseline.

1. **Module execution:** the first bridge loaded the original ARM module and reached BREW module/class/start entry points on S25 Ultra.
2. **Requested phone services:** device information, progress reads, BAR loading, images/bitmaps/display destinations and the original timer callbacks were implemented from observed calls.
3. **Real pixels:** rectangle/update handling produced the original Enable Sound prompt in a pixel-backed framebuffer.
4. **Persistent input:** one guest stayed alive between timer slices and handset key press/release events. Original menus, story and gameplay transitions became reachable.
5. **Android image decoding:** BAR PNG records carry a 12-byte prefix. The desktop decoder tolerated it; Android did not. Removing the prefix restored missing graphics without changing the original BAR file.
6. **Controls from behavior:** the original HELP resource supplied handset semantics, but guessed numeric/OEM constants still failed during phone play. The later guest probes measured actual movement. The final pad uses dedicated 1/3/7/9 diagonal codes and confirmed FIRE/AUTO/BACK values.
7. **Media calls:** the formerly unidentified interface was measured as an IMedia-style path. Its supported subset feeds original MIDI/QCP resources to Android. Overlapping startup MIDI requests were reproduced and handled with one active sequencer.
8. **Omni integration:** the working BREW runtime moved into the shared bridge shell with a larger proportional stage, compact green controls, readable reports and persistent-session/pause/input ownership.
9. **Public release:** v0.1.1 removes the embedded game payload, adds verified local ZIP import and preserves the Omni update identity.

Physical development evidence includes the v0.0.9 S25 Ultra / Android 16 report: 454,759,078 guest instructions, 11,121 display updates, 616 key events, 18 successful PNG decodes with zero failures, zero unhandled interface calls during that captured run, and four Android audio starts with zero recorded failures. This is evidence for that earlier run, not certification of every level or every audio file.

v0.1.1 host tests read the game through the new importer/store and exercise the retained original guest. The new Android import/update flow and complete-game behavior still require phone testing.

The private preservation package retains the original inputs, earlier evidence packages and update key. Public source and releases omit those game inputs and credentials.

10. **Phone import correction:** v0.1.1 rejected the selected compatible ZIP with "Unsafe game-data storage path". A host storage alias reproduced the error. v0.1.2 resolves the trusted storage root and keeps payload containment checks; phone retry remains pending.
