package com.wakka.rushbridge.runtime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny clean-room ARMv5/Thumb interpreter + BREW HLE bring-up probe.
 *
 * This deliberately implements only the CPU and BREW surface exercised by the
 * preserved Mega Man: Rush Marine 1.1.11 module so far. It executes the real
 * mmassault.mod bytes; it does not contain translated game code.
 */
public final class BrewRuntime {
    private BrewRuntime() {}

    /**
     * Platform hook used only for decoding preserved PNG resources. The clean-room
     * runtime itself stays Android-free so the same guest execution can be
     * regression-tested on a desktop JVM.
     */
    public interface PixelDecoder {
        int[] decodePng(byte[] png, int width, int height) throws Exception;
    }

    public static final int CLASS_ID = 0x01094C5E;
    public static final int EVT_KEY_PRESS = 0x0100;
    public static final int EVT_KEY_RELEASE = 0x0102;

    // Rush Marine key semantics measured against the original ARM guest.
    // These values are not inferred from their hexadecimal suffixes:
    // desktop guest A/B tests proved E031/E032/E033/E034 = up/down/left/right,
    // E035 = select/fire, E021 = handset 0 / auto-fire toggle, and E030 = back/clear.
    public static final int AVK_0 = 0xE021;
    public static final int AVK_1 = 0xE022;
    public static final int AVK_2 = 0xE023;
    public static final int AVK_3 = 0xE024;
    public static final int AVK_4 = 0xE025;
    public static final int AVK_5 = 0xE026;
    public static final int AVK_6 = 0xE027;
    public static final int AVK_7 = 0xE028;
    public static final int AVK_8 = 0xE029;
    public static final int AVK_9 = 0xE02A;
    public static final int AVK_CLR = 0xE030;
    public static final int AVK_UP = 0xE031;
    public static final int AVK_DOWN = 0xE032;
    public static final int AVK_LEFT = 0xE033;
    public static final int AVK_RIGHT = 0xE034;
    public static final int AVK_SELECT = 0xE035;

    public static int keyCodeForLabel(String label) {
        if (label == null) return 0;
        switch (label) {
            // Rush Marine does not need the soft keys, star, or pound for gameplay.
            // Leave unverified raw-reference slots inert rather than mislabel a code.
            case "L soft": return 0;
            case "R soft": return 0;
            case "▲": return AVK_UP;
            case "▼": return AVK_DOWN;
            case "◀": return AVK_LEFT;
            case "▶": return AVK_RIGHT;
            case "OK": return AVK_SELECT;
            case "*": return 0;
            case "#": return 0;
            case "0": return AVK_0;
            case "1": return AVK_1;
            case "2": return AVK_2;
            case "3": return AVK_3;
            case "4": return AVK_4;
            case "5": return AVK_5;
            case "6": return AVK_6;
            case "7": return AVK_7;
            case "8": return AVK_8;
            case "9": return AVK_9;
            default: return 0;
        }
    }
    private static final int BASE = 0x00100000;
    private static final int MEMSZ = 0x04000000;
    private static final int TRAP_BASE = 0xF0000000;
    private static final int RET_SENT = 0xEFFFFFF0;

    /** Host-facing audio command emitted by the original guest IMedia path. */
    public static final class AudioEvent {
        public static final int PLAY=1, STOP=2;
        public final long serial;
        public final int kind;
        public final int mediaObject;
        public final int resourceId;
        public final byte[] data;
        AudioEvent(long serial,int kind,int mediaObject,int resourceId,byte[] data){
            this.serial=serial;this.kind=kind;this.mediaObject=mediaObject;this.resourceId=resourceId;this.data=data;
        }
    }

    public static final class Result {
        public boolean moduleLoaded;
        public boolean appletCreated;
        public boolean startEventAccepted;
        public int moduleAddress;
        public int appletAddress;
        public int handlerAddress;
        public long steps;
        public int fileOpenCount;
        public int fileReadBytes;
        public boolean progressFileRead;
        public int resourceLoads;
        public int imageLoads;
        public int imageInfoCalls;
        public int imagePixelDecodes;
        public int imagePixelDecodeFailures;
        public int mediaCreates;
        public int mediaSetDataCalls;
        public int mediaPlayRequests;
        public int mediaStopRequests;
        public int lastAudioResourceId;
        public long lastAudioSerial;
        public AudioEvent[] audioEvents;
        public int unknownInterfaceCalls;
        public int lastUnknownInterfaceClass;
        public int lastUnknownInterfaceOffset;
        public int timerSchedules;
        public int lastTimerCallback;
        public int lastTimerData;
        public boolean timerCallbackExecuted;
        public long timerCallbackSteps;
        public int compatibleBitmapCreates;
        public int displayDestinationGets;
        public int displayDestinationSets;
        public int imageDrawCalls;
        public int drawRectCalls;
        public int bitBltCalls;
        public int displayUpdates;
        public int timerCallbacksExecuted;
        public long timerCallbacksStepsTotal;
        public String extendedTimerFailure;
        public int framebufferWidth;
        public int framebufferHeight;
        public int framebufferNonBlackPixels;
        public int[] framebufferArgb;
        public boolean pixelBackedDisplay;
        public boolean sessionLive;
        public int keyEvents;
        public int keyPresses;
        public int keyReleases;
        public int lastKeyCode;
        public int lastKeyPressReturn;
        public int lastKeyReleaseReturn;
        public String liveFailure;
        public String failure;
        public String report;
    }


    /**
     * Persistent clean-room BREW guest session.
     *
     * v0.0.3 proved the original executable can boot, draw into a pixel-backed
     * framebuffer, and execute its timer callback.  This object keeps that exact
     * guest memory/heap/interface state alive so Android keypad events can be
     * delivered to the original applet handler rather than queued in the UI.
     */
    public static final class Session {
        private final Result out = new Result();
        private final StringBuilder log = new StringBuilder(131072);
        private final Mem m;
        private final CPU c;
        private final Host h;
        private int applet;
        private int handler;
        private boolean alive = true;

        private Session(byte[] mod, byte[] progress, byte[] bar, PixelDecoder decoder) {
            m = new Mem();
            m.copy(BASE, mod);
            c = new CPU(m, log);
            h = new Host(c, log, progress, bar, decoder);
            try {
                append(log, "RUSH MARINE BREW BRIDGE // PERSISTENT ORIGINAL ARM SESSION");
                append(log, "Module bytes: " + mod.length);
                append(log, String.format(Locale.US, "Expected class: 0x%08X", CLASS_ID));
                append(log, "");

                int ppmod = 0x02004000;
                append(log, "=== AEEMod_Load ===");
                int loadRc = call(c, BASE, new int[]{h.shell, 0, ppmod}, 500_000, log);
                int module = m.r32(ppmod);
                out.moduleAddress = module;
                out.moduleLoaded = loadRc == 0 && module != 0;
                append(log, String.format(Locale.US, "AEEMod_Load rc=%d module=0x%08X", loadRc, module));
                if (!out.moduleLoaded) throw new RuntimeException("AEEMod_Load did not produce a module object");

                int vt = m.r32(module);
                int create = m.r32(vt + 8);
                int ppobj = 0x02004004;
                append(log, "");
                append(log, String.format(Locale.US, "=== IModule.CreateInstance @ 0x%08X ===", create));
                int createRc = call(c, create, new int[]{module, h.shell, CLASS_ID, ppobj}, 2_000_000, log);
                applet = m.r32(ppobj);
                out.appletAddress = applet;
                out.appletCreated = createRc == 0 && applet != 0;
                append(log, String.format(Locale.US, "CreateInstance rc=%d applet=0x%08X", createRc, applet));
                if (!out.appletCreated) throw new RuntimeException("Rush Marine applet construction failed");

                handler = m.r32(applet + 0x18);
                out.handlerAddress = handler;
                append(log, "");
                append(log, String.format(Locale.US, "=== EVT_APP_START via handler 0x%08X ===", handler));
                int startRc = call(c, handler, new int[]{applet, 1, 0, 0}, 5_000_000, log);
                out.startEventAccepted = startRc != 0;
                append(log, "EVT_APP_START returned " + startRc + (out.startEventAccepted ? " (accepted)" : ""));
                out.sessionLive = out.moduleLoaded && out.appletCreated && out.startEventAccepted;
                sync();
            } catch (Throwable t) {
                fail(t);
            }
        }

        public synchronized boolean isAlive() { return alive && out.sessionLive && out.liveFailure == null; }

        public synchronized Result pumpTimers(int count) {
            if (!isAlive()) return snapshot();
            try {
                for (int i = 0; i < count && h.lastTimerCallback != 0; i++) {
                    pumpOneTimer();
                }
            } catch (Throwable t) {
                failLive(t, "timer");
            }
            return snapshot();
        }

        private void pumpOneTimer() {
            int timerCb = h.lastTimerCallback;
            int timerData = h.lastTimerData;
            h.lastTimerCallback = 0;
            h.lastTimerData = 0;
            int index = out.timerCallbacksExecuted + 1;
            append(log, "");
            append(log, String.format(Locale.US,
                    "=== LIVE TIMER CALLBACK #%d 0x%08X data=0x%08X ===",
                    index, timerCb, timerData));
            long before = c.steps;
            call(c, timerCb, new int[]{timerData}, 5_000_000, log);
            long timerSteps = c.steps - before;
            if (!out.timerCallbackExecuted) out.timerCallbackSteps = timerSteps;
            out.timerCallbacksStepsTotal += timerSteps;
            out.timerCallbacksExecuted++;
            out.timerCallbackExecuted = true;
            append(log, "Live timer callback returned through original game code");
            sync();
        }

        /** Deliver a BREW key-down event while keeping the guest session alive. */
        public synchronized Result pressKey(int keyCode) {
            if (!isAlive()) return snapshot();
            if (keyCode == 0) {
                append(log, "LIVE KEY PRESS ignored: unmapped key code 0");
                return snapshot();
            }
            try {
                append(log, "");
                append(log, String.format(Locale.US, "=== LIVE KEY PRESS 0x%04X ===", keyCode & 0xFFFF));
                long before = c.steps;
                int pressRc = call(c, handler, new int[]{applet, EVT_KEY_PRESS, keyCode, 0}, 5_000_000, log);
                out.keyEvents++;
                out.keyPresses++;
                out.lastKeyCode = keyCode;
                out.lastKeyPressReturn = pressRc;
                append(log, String.format(Locale.US,
                        "EVT_KEY_PRESS key=0x%04X returned %d steps=%d",
                        keyCode & 0xFFFF, pressRc, c.steps - before));
                sync();
            } catch (Throwable t) {
                failLive(t, String.format(Locale.US, "key press 0x%04X", keyCode & 0xFFFF));
            }
            return snapshot();
        }

        /** Deliver the matching BREW key-up event. */
        public synchronized Result releaseKey(int keyCode) {
            if (!isAlive()) return snapshot();
            if (keyCode == 0) {
                append(log, "LIVE KEY RELEASE ignored: unmapped key code 0");
                return snapshot();
            }
            try {
                append(log, String.format(Locale.US, "=== LIVE KEY RELEASE 0x%04X ===", keyCode & 0xFFFF));
                long before = c.steps;
                int releaseRc = call(c, handler, new int[]{applet, EVT_KEY_RELEASE, keyCode, 0}, 5_000_000, log);
                out.keyEvents++;
                out.keyReleases++;
                out.lastKeyCode = keyCode;
                out.lastKeyReleaseReturn = releaseRc;
                append(log, String.format(Locale.US,
                        "EVT_KEY_RELEASE key=0x%04X returned %d steps=%d",
                        keyCode & 0xFFFF, releaseRc, c.steps - before));
                sync();
            } catch (Throwable t) {
                failLive(t, String.format(Locale.US, "key release 0x%04X", keyCode & 0xFFFF));
            }
            return snapshot();
        }

        /** Convenience desktop/test path: one complete press/release pair plus timer slices. */
        public synchronized Result tapKey(int keyCode, int timerTicksAfter) {
            if (!isAlive()) return snapshot();
            pressKey(keyCode);
            if (isAlive() && h.lastTimerCallback != 0) pumpOneTimer();
            releaseKey(keyCode);
            try {
                for (int i = 0; i < timerTicksAfter && isAlive() && h.lastTimerCallback != 0; i++) pumpOneTimer();
                sync();
            } catch (Throwable t) {
                failLive(t, String.format(Locale.US, "key tap 0x%04X", keyCode & 0xFFFF));
            }
            return snapshot();
        }

        public synchronized Result snapshot() {
            sync();
            Result r = copyResult(out);
            r.report = log.toString();
            return r;
        }

        private void sync() {
            out.steps = c.steps;
            out.fileOpenCount = h.fileOpenCount;
            out.fileReadBytes = h.fileReadBytes;
            out.progressFileRead = h.progressFileRead;
            out.resourceLoads = h.resourceLoads;
            out.imageLoads = h.imageLoads;
            out.imageInfoCalls = h.imageInfoCalls;
            out.imagePixelDecodes = h.imagePixelDecodes;
            out.imagePixelDecodeFailures = h.imagePixelDecodeFailures;
            out.mediaCreates = h.mediaCreates;
            out.mediaSetDataCalls = h.mediaSetDataCalls;
            out.mediaPlayRequests = h.mediaPlayRequests;
            out.mediaStopRequests = h.mediaStopRequests;
            out.lastAudioResourceId = h.lastAudioResourceId;
            out.lastAudioSerial = h.audioSerial;
            out.audioEvents = h.audioEvents.toArray(new AudioEvent[0]);
            out.unknownInterfaceCalls = h.unknownInterfaceCalls;
            out.lastUnknownInterfaceClass = h.lastUnknownInterfaceClass;
            out.lastUnknownInterfaceOffset = h.lastUnknownInterfaceOffset;
            out.timerSchedules = h.timerSchedules;
            out.lastTimerCallback = h.lastTimerCallback;
            out.lastTimerData = h.lastTimerData;
            out.compatibleBitmapCreates = h.compatibleBitmapCreates;
            out.displayDestinationGets = h.displayDestinationGets;
            out.displayDestinationSets = h.displayDestinationSets;
            out.imageDrawCalls = h.imageDrawCalls;
            out.drawRectCalls = h.drawRectCalls;
            out.bitBltCalls = h.bitBltCalls;
            out.displayUpdates = h.displayUpdates;
            snapshotFrame(out, h);
        }

        private void fail(Throwable t) {
            alive = false;
            out.sessionLive = false;
            out.failure = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            append(log, "");
            append(log, "SESSION BOOT FAILURE: " + out.failure);
            sync();
        }

        private void failLive(Throwable t, String phase) {
            alive = false;
            out.sessionLive = false;
            out.liveFailure = phase + " -> " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            append(log, "");
            append(log, "LIVE SESSION FRONTIER: " + out.liveFailure);
            sync();
        }
    }

    public static Session startSession(byte[] mod, byte[] progress, byte[] bar, PixelDecoder decoder) {
        return new Session(mod, progress, bar, decoder);
    }

    private static Result copyResult(Result a) {
        Result b = new Result();
        b.moduleLoaded=a.moduleLoaded; b.appletCreated=a.appletCreated; b.startEventAccepted=a.startEventAccepted;
        b.moduleAddress=a.moduleAddress; b.appletAddress=a.appletAddress; b.handlerAddress=a.handlerAddress;
        b.steps=a.steps; b.fileOpenCount=a.fileOpenCount; b.fileReadBytes=a.fileReadBytes; b.progressFileRead=a.progressFileRead;
        b.resourceLoads=a.resourceLoads; b.imageLoads=a.imageLoads; b.imageInfoCalls=a.imageInfoCalls;
        b.imagePixelDecodes=a.imagePixelDecodes; b.imagePixelDecodeFailures=a.imagePixelDecodeFailures;
        b.mediaCreates=a.mediaCreates; b.mediaSetDataCalls=a.mediaSetDataCalls; b.mediaPlayRequests=a.mediaPlayRequests; b.mediaStopRequests=a.mediaStopRequests;
        b.lastAudioResourceId=a.lastAudioResourceId; b.lastAudioSerial=a.lastAudioSerial; b.audioEvents=a.audioEvents==null?null:a.audioEvents.clone();
        b.unknownInterfaceCalls=a.unknownInterfaceCalls; b.lastUnknownInterfaceClass=a.lastUnknownInterfaceClass; b.lastUnknownInterfaceOffset=a.lastUnknownInterfaceOffset;
        b.timerSchedules=a.timerSchedules; b.lastTimerCallback=a.lastTimerCallback; b.lastTimerData=a.lastTimerData;
        b.timerCallbackExecuted=a.timerCallbackExecuted; b.timerCallbackSteps=a.timerCallbackSteps;
        b.compatibleBitmapCreates=a.compatibleBitmapCreates; b.displayDestinationGets=a.displayDestinationGets;
        b.displayDestinationSets=a.displayDestinationSets; b.imageDrawCalls=a.imageDrawCalls; b.drawRectCalls=a.drawRectCalls;
        b.bitBltCalls=a.bitBltCalls; b.displayUpdates=a.displayUpdates; b.timerCallbacksExecuted=a.timerCallbacksExecuted;
        b.timerCallbacksStepsTotal=a.timerCallbacksStepsTotal; b.extendedTimerFailure=a.extendedTimerFailure;
        b.framebufferWidth=a.framebufferWidth; b.framebufferHeight=a.framebufferHeight;
        b.framebufferNonBlackPixels=a.framebufferNonBlackPixels;
        b.framebufferArgb=a.framebufferArgb==null?null:a.framebufferArgb.clone();
        b.pixelBackedDisplay=a.pixelBackedDisplay; b.sessionLive=a.sessionLive;
        b.keyEvents=a.keyEvents; b.keyPresses=a.keyPresses; b.keyReleases=a.keyReleases; b.lastKeyCode=a.lastKeyCode;
        b.lastKeyPressReturn=a.lastKeyPressReturn; b.lastKeyReleaseReturn=a.lastKeyReleaseReturn;
        b.liveFailure=a.liveFailure; b.failure=a.failure; b.report=a.report;
        return b;
    }

    public static Result runBootProbe(byte[] mod) {
        return runBootProbe(mod, new byte[0], new byte[0], null);
    }

    public static Result runBootProbe(byte[] mod, byte[] progress, byte[] bar) {
        return runBootProbe(mod, progress, bar, null);
    }

    public static Result runBootProbe(byte[] mod, byte[] progress, byte[] bar, PixelDecoder decoder) {
        Result out = new Result();
        StringBuilder log = new StringBuilder(16384);
        Host h = null;
        CPU c = null;
        try {
            Mem m = new Mem();
            m.copy(BASE, mod);
            c = new CPU(m, log);
            h = new Host(c, log, progress, bar, decoder);

            append(log, "RUSH MARINE BREW BRIDGE // ORIGINAL ARM MODULE PROBE");
            append(log, "Module bytes: " + mod.length);
            append(log, String.format(Locale.US, "Expected class: 0x%08X", CLASS_ID));
            append(log, "");

            int ppmod = 0x02004000;
            append(log, "=== AEEMod_Load ===");
            int loadRc = call(c, BASE, new int[]{h.shell, 0, ppmod}, 500_000, log);
            int module = m.r32(ppmod);
            out.moduleAddress = module;
            out.moduleLoaded = loadRc == 0 && module != 0;
            append(log, String.format(Locale.US, "AEEMod_Load rc=%d module=0x%08X", loadRc, module));

            if (!out.moduleLoaded) throw new RuntimeException("AEEMod_Load did not produce a module object");

            int vt = m.r32(module);
            int create = m.r32(vt + 8);
            int ppobj = 0x02004004;
            append(log, "");
            append(log, String.format(Locale.US, "=== IModule.CreateInstance @ 0x%08X ===", create));
            int createRc = call(c, create, new int[]{module, h.shell, CLASS_ID, ppobj}, 2_000_000, log);
            int obj = m.r32(ppobj);
            out.appletAddress = obj;
            out.appletCreated = createRc == 0 && obj != 0;
            append(log, String.format(Locale.US, "CreateInstance rc=%d applet=0x%08X", createRc, obj));

            if (!out.appletCreated) throw new RuntimeException("Rush Marine applet construction failed");

            int handler = m.r32(obj + 0x18);
            out.handlerAddress = handler;
            append(log, "");
            append(log, String.format(Locale.US, "=== EVT_APP_START via handler 0x%08X ===", handler));
            int startRc = call(c, handler, new int[]{obj, 1, 0, 0}, 5_000_000, log);
            out.startEventAccepted = startRc != 0;
            append(log, "EVT_APP_START returned " + startRc + (out.startEventAccepted ? " (accepted)" : ""));

            // BREW timers are callback + user-data pairs. Pump several real callbacks
            // so the probe reaches the game's timer-driven paint/update work. A deeper
            // callback failure after the first proven callback is recorded as the next
            // frontier instead of erasing the already-proven startup milestone.
            for (int timerIndex = 0; timerIndex < 6 && h.lastTimerCallback != 0; timerIndex++) {
                int timerCb = h.lastTimerCallback;
                int timerData = h.lastTimerData;
                h.lastTimerCallback = 0;
                h.lastTimerData = 0;
                append(log, "");
                append(log, String.format(Locale.US,
                        "=== TIMER CALLBACK #%d 0x%08X data=0x%08X ===",
                        timerIndex + 1, timerCb, timerData));
                long beforeTimer = c.steps;
                try {
                    call(c, timerCb, new int[]{timerData}, 5_000_000, log);
                    long timerSteps = c.steps - beforeTimer;
                    if (timerIndex == 0) out.timerCallbackSteps = timerSteps;
                    out.timerCallbacksStepsTotal += timerSteps;
                    out.timerCallbacksExecuted++;
                    out.timerCallbackExecuted = true;
                    append(log, "Timer callback returned through original game code");
                } catch (Throwable deeper) {
                    if (timerIndex == 0) throw deeper;
                    out.extendedTimerFailure = deeper.getClass().getSimpleName() + ": " + String.valueOf(deeper.getMessage());
                    append(log, "EXTENDED TIMER FRONTIER: " + out.extendedTimerFailure);
                    break;
                }
            }

            out.steps = c.steps;
            out.fileOpenCount = h.fileOpenCount;
            out.fileReadBytes = h.fileReadBytes;
            out.progressFileRead = h.progressFileRead;
            out.resourceLoads = h.resourceLoads;
            out.imageLoads = h.imageLoads;
            out.imageInfoCalls = h.imageInfoCalls;
            out.imagePixelDecodes = h.imagePixelDecodes;
            out.imagePixelDecodeFailures = h.imagePixelDecodeFailures;
            out.mediaCreates = h.mediaCreates;
            out.mediaSetDataCalls = h.mediaSetDataCalls;
            out.mediaPlayRequests = h.mediaPlayRequests;
            out.mediaStopRequests = h.mediaStopRequests;
            out.lastAudioResourceId = h.lastAudioResourceId;
            out.lastAudioSerial = h.audioSerial;
            out.audioEvents = h.audioEvents.toArray(new AudioEvent[0]);
            out.unknownInterfaceCalls = h.unknownInterfaceCalls;
            out.lastUnknownInterfaceClass = h.lastUnknownInterfaceClass;
            out.lastUnknownInterfaceOffset = h.lastUnknownInterfaceOffset;
            out.timerSchedules = h.timerSchedules;
            out.lastTimerCallback = h.lastTimerCallback;
            out.lastTimerData = h.lastTimerData;
            out.compatibleBitmapCreates = h.compatibleBitmapCreates;
            out.displayDestinationGets = h.displayDestinationGets;
            out.displayDestinationSets = h.displayDestinationSets;
            out.imageDrawCalls = h.imageDrawCalls;
            out.drawRectCalls = h.drawRectCalls;
            out.bitBltCalls = h.bitBltCalls;
            out.displayUpdates = h.displayUpdates;
            snapshotFrame(out, h);
            append(log, "");
            append(log, "FIRST-LIGHT RESULT");
            append(log, "  module load      : " + pass(out.moduleLoaded));
            append(log, "  class 01094C5E   : " + pass(out.appletCreated));
            append(log, "  app start event  : " + pass(out.startEventAccepted));
            append(log, "  executed steps   : " + out.steps);
            append(log, "  file opens       : " + out.fileOpenCount);
            append(log, "  file bytes read  : " + out.fileReadBytes);
            append(log, "  progress.bin HLE : " + pass(out.progressFileRead));
            append(log, "  BAR data loads   : " + out.resourceLoads);
            append(log, "  BAR image loads  : " + out.imageLoads);
            append(log, "  image info calls : " + out.imageInfoCalls);
            append(log, "  PNG pixel decode : " + out.imagePixelDecodes + " success / " + out.imagePixelDecodeFailures + " failed");
            append(log, "  media objects    : " + out.mediaCreates + " / data=" + out.mediaSetDataCalls + " / play=" + out.mediaPlayRequests + " / stop=" + out.mediaStopRequests);
            append(log, "  timer schedules  : " + out.timerSchedules);
            append(log, String.format(Locale.US, "  last timer cb    : 0x%08X", out.lastTimerCallback));
            append(log, "  timer callbacks  : " + out.timerCallbacksExecuted + " (" + out.timerCallbacksStepsTotal + " steps total)");
            append(log, "  first timer cb   : " + pass(out.timerCallbackExecuted) + " (" + out.timerCallbackSteps + " steps)");
            append(log, "  bitmap creates   : " + out.compatibleBitmapCreates);
            append(log, "  display get/set  : " + out.displayDestinationGets + " / " + out.displayDestinationSets);
            append(log, "  image draws      : " + out.imageDrawCalls);
            append(log, "  draw rect calls  : " + out.drawRectCalls);
            append(log, "  bitblt calls     : " + out.bitBltCalls);
            append(log, "  display updates  : " + out.displayUpdates);
            append(log, "  pixel framebuffer: " + pass(out.pixelBackedDisplay) + " " + out.framebufferWidth + "x" + out.framebufferHeight + " nonblack=" + out.framebufferNonBlackPixels);
            if (out.extendedTimerFailure != null) append(log, "  deeper frontier  : " + out.extendedTimerFailure);
            append(log, "  next boundary    : physical validation of direct diagonals + startup single-MIDI arbitration");
        } catch (Throwable t) {
            out.failure = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            append(log, "");
            append(log, "FAILURE: " + out.failure);
        } finally {
            if (c != null) out.steps = c.steps;
            if (h != null) {
                out.fileOpenCount = h.fileOpenCount;
                out.fileReadBytes = h.fileReadBytes;
                out.progressFileRead = h.progressFileRead;
                out.resourceLoads = h.resourceLoads;
                out.imageLoads = h.imageLoads;
                out.imageInfoCalls = h.imageInfoCalls;
            out.imagePixelDecodes = h.imagePixelDecodes;
            out.imagePixelDecodeFailures = h.imagePixelDecodeFailures;
            out.mediaCreates = h.mediaCreates;
            out.mediaSetDataCalls = h.mediaSetDataCalls;
            out.mediaPlayRequests = h.mediaPlayRequests;
            out.mediaStopRequests = h.mediaStopRequests;
            out.lastAudioResourceId = h.lastAudioResourceId;
            out.lastAudioSerial = h.audioSerial;
            out.audioEvents = h.audioEvents.toArray(new AudioEvent[0]);
                out.timerSchedules = h.timerSchedules;
                out.lastTimerCallback = h.lastTimerCallback;
                out.lastTimerData = h.lastTimerData;
                out.compatibleBitmapCreates = h.compatibleBitmapCreates;
                out.displayDestinationGets = h.displayDestinationGets;
                out.displayDestinationSets = h.displayDestinationSets;
                out.imageDrawCalls = h.imageDrawCalls;
                out.drawRectCalls = h.drawRectCalls;
                out.bitBltCalls = h.bitBltCalls;
                out.displayUpdates = h.displayUpdates;
                snapshotFrame(out, h);
            }
        }
        out.report = log.toString();
        return out;
    }

    private static void snapshotFrame(Result out, Host h) {
        BitmapState frame = h.deviceBitmap == 0 ? null : h.bitmaps.get(h.deviceBitmap);
        if (frame == null || frame.pixels == null) return;
        out.framebufferWidth = frame.width;
        out.framebufferHeight = frame.height;
        out.framebufferArgb = frame.pixels.clone();
        out.pixelBackedDisplay = true;
        int nonBlack = 0;
        for (int px : frame.pixels) if ((px & 0x00FFFFFF) != 0) nonBlack++;
        out.framebufferNonBlackPixels = nonBlack;
    }

    private static String pass(boolean b) { return b ? "PASS" : "FAIL"; }
    private static void append(StringBuilder sb, String s) {
        // Live sessions can run for minutes and generate millions of HLE calls. Keep
        // enough recent trace to diagnose the frontier without letting a phone-game
        // session grow an unbounded Java StringBuilder.
        final int MAX=2_000_000, TRIM_TO=1_000_000;
        if(sb.length()>MAX){
            int cut=sb.length()-TRIM_TO;
            int nl=sb.indexOf("\n",cut);
            if(nl<0)nl=cut; else nl++;
            sb.delete(0,nl);
            sb.insert(0,"[... older live trace trimmed; cumulative counters preserved ...]\n");
        }
        sb.append(s).append('\n');
    }

    private static int call(CPU c, int addr, int[] args, long maxSteps, StringBuilder log) {
        Arrays.fill(c.r, 0);
        for (int i = 0; i < args.length && i < 4; i++) c.r[i] = args[i];
        c.r[13] = 0x03F00000;
        for (int i = 4; i < args.length; i++) c.m.w32(c.r[13] + (i - 4) * 4, args[i]);
        c.r[14] = RET_SENT;
        c.r[15] = addr & ~1;
        c.thumb = (addr & 1) != 0;
        long start = c.steps;
        while (c.steps - start < maxSteps && c.step()) { /* execute */ }
        if (c.r[15] != RET_SENT && c.steps - start >= maxSteps) {
            throw new RuntimeException("step budget exhausted at " + hex(c.r[15]));
        }
        append(log, String.format(Locale.US, "RETURN 0x%08X steps=%d pc=0x%08X", c.r[0], c.steps - start, c.r[15]));
        return c.r[0];
    }

    private static String hex(int v) { return String.format(Locale.US, "0x%08X", v); }

    private static final class Mem {
        final byte[] b = new byte[MEMSZ];
        void chk(int a, int n) {
            long ua = Integer.toUnsignedLong(a);
            if (ua + n > b.length) throw new RuntimeException(String.format(Locale.US, "MEM OOB %08X+%d", a, n));
        }
        int r8(int a) { chk(a,1); return b[a] & 0xFF; }
        int r16(int a) { chk(a,2); return (b[a]&255) | ((b[a+1]&255)<<8); }
        int r32(int a) { chk(a,4); return (b[a]&255) | ((b[a+1]&255)<<8) | ((b[a+2]&255)<<16) | ((b[a+3]&255)<<24); }
        void w8(int a,int v){ chk(a,1); b[a]=(byte)v; }
        void w16(int a,int v){ chk(a,2); b[a]=(byte)v; b[a+1]=(byte)(v>>>8); }
        void w32(int a,int v){ chk(a,4); b[a]=(byte)v; b[a+1]=(byte)(v>>>8); b[a+2]=(byte)(v>>>16); b[a+3]=(byte)(v>>>24); }
        void copy(int a, byte[] d){ chk(a,d.length); System.arraycopy(d,0,b,a,d.length); }
    }

    private static final class Shift { int v,c; Shift(int v,int c){this.v=v;this.c=c;} }
    private static final class Arith { int v,c,o; Arith(int v,int c,int o){this.v=v;this.c=c;this.o=o;} }

    private static final class CPU {
        final Mem m;
        final int[] r = new int[16];
        int N,Z,C,V;
        boolean thumb;
        long steps;
        int cur;
        int heap = 0x01000000;
        final HostBridge bridge = new HostBridge();
        final StringBuilder log;

        CPU(Mem m, StringBuilder log){this.m=m;this.log=log;}

        int alloc(int n,int align){
            int p=(heap+align-1)&~(align-1); heap=p+n;
            if (Integer.compareUnsigned(heap,0x02000000)>=0) throw new RuntimeException("guest heap full");
            return p;
        }
        int reg(int n){ if(n==15) return cur + (thumb?4:8); return r[n]; }
        void setreg(int n,int v){r[n]=v;}
        void setNZ(int v){N=(v<0)?1:0; Z=(v==0)?1:0;}
        Arith addc(int a,int b,int cin){
            long ua=Integer.toUnsignedLong(a), ub=Integer.toUnsignedLong(b), s=ua+ub+cin;
            int v=(int)s; int carry=(s>>>32)!=0?1:0;
            int ov=(((a^v)&(b^v))<0)?1:0;
            return new Arith(v,carry,ov);
        }
        Arith subc(int a,int b,int cin){
            long ua=Integer.toUnsignedLong(a); long sub=Integer.toUnsignedLong(b)+(1-cin);
            int v=(int)(ua-sub); int carry=Long.compareUnsigned(ua,sub)>=0?1:0;
            int be=(int)(b+(1-cin)); int ov=(((a^be)&(a^v))<0)?1:0;
            return new Arith(v,carry,ov);
        }
        boolean cond(int c){
            switch(c){
                case 0:return Z==1; case 1:return Z==0; case 2:return C==1; case 3:return C==0;
                case 4:return N==1; case 5:return N==0; case 6:return V==1; case 7:return V==0;
                case 8:return C==1&&Z==0; case 9:return C==0||Z==1; case 10:return N==V; case 11:return N!=V;
                case 12:return Z==0&&N==V; case 13:return Z==1||N!=V; case 14:return true; default:return false;
            }
        }
        Shift shift(int val,int typ,int amt,boolean byreg){
            if(amt==0){
                if(byreg) return new Shift(val,C);
                if(typ==0) return new Shift(val,C);
                if(typ==1) return new Shift(0,(val>>>31)&1);
                if(typ==2) return new Shift(val<0?-1:0,(val>>>31)&1);
                return new Shift((C<<31)|(val>>>1),val&1);
            }
            if(typ==0){
                if(amt<32) return new Shift(val<<amt,(val>>>(32-amt))&1);
                if(amt==32) return new Shift(0,val&1); return new Shift(0,0);
            }
            if(typ==1){
                if(amt<32) return new Shift(val>>>amt,(val>>>(amt-1))&1);
                if(amt==32) return new Shift(0,(val>>>31)&1); return new Shift(0,0);
            }
            if(typ==2){
                if(amt>=32) return new Shift(val<0?-1:0,(val>>>31)&1);
                return new Shift(val>>amt,(val>>>(amt-1))&1);
            }
            amt%=32; if(amt==0) return new Shift(val,(val>>>31)&1);
            return new Shift(Integer.rotateRight(val,amt),(val>>>(amt-1))&1);
        }
        Shift op2(int ins){
            if((ins&(1<<25))!=0){
                int imm=ins&0xff, rot=((ins>>>8)&15)*2;
                if(rot==0) return new Shift(imm,C);
                int v=Integer.rotateRight(imm,rot); return new Shift(v,(v>>>31)&1);
            }
            int rm=ins&15, val=reg(rm), typ=(ins>>>5)&3;
            if((ins&(1<<4))!=0){ int rs=(ins>>>8)&15, amt=reg(rs)&0xff; return shift(val,typ,amt,true); }
            return shift(val,typ,(ins>>>7)&31,false);
        }
        boolean step(){
            int pc=r[15]; if(pc==RET_SENT) return false;
            if(Integer.compareUnsigned(pc,TRAP_BASE)>=0){cur=pc; bridge.dispatch(this,pc); steps++; return true;}
            cur=pc; if(thumb) stepThumb(); else stepArm(); steps++;
            int next=r[15];
            if(next!=RET_SENT && Integer.compareUnsigned(next,BASE)<0){
                throw new RuntimeException(String.format(Locale.US,
                    "guest branch below module from=%08X to=%08X thumb=%s lr=%08X r0=%08X r1=%08X r2=%08X r3=%08X sp=%08X",
                    pc,next,thumb,r[14],r[0],r[1],r[2],r[3],r[13]));
            }
            return true;
        }
        void stepArm(){
            int pc=cur, ins=m.r32(pc); r[15]=pc+4; int cc=ins>>>28; if(!cond(cc))return;
            int bxmask=ins&0x0ffffff0;
            if(bxmask==0x012fff10 || bxmask==0x012fff30){int rm=ins&15,target=reg(rm);if(bxmask==0x012fff30)r[14]=pc+4;thumb=(target&1)!=0;r[15]=target&~1;return;}
            if(((ins>>>25)&7)==5){int off=(ins&0xffffff)<<2;if((off&0x02000000)!=0)off-=0x04000000;if((ins&(1<<24))!=0)r[14]=pc+4;r[15]=pc+8+off;return;}
            if((ins&0x0f8000f0)==0x00800090){
                boolean U=((ins>>>22)&1)!=0,A=((ins>>>21)&1)!=0,S=((ins>>>20)&1)!=0;int rdhi=(ins>>>16)&15,rdlo=(ins>>>12)&15,rs=(ins>>>8)&15,rm=ins&15;
                long prod=U?(long)reg(rm)*(long)reg(rs):Integer.toUnsignedLong(reg(rm))*Integer.toUnsignedLong(reg(rs));
                if(A)prod+= (Integer.toUnsignedLong(reg(rdhi))<<32)|Integer.toUnsignedLong(reg(rdlo));
                setreg(rdlo,(int)prod);setreg(rdhi,(int)(prod>>>32));if(S){N=prod<0?1:0;Z=prod==0?1:0;}return;
            }
            if((ins&0x0fc000f0)==0x00000090){boolean A=((ins>>>21)&1)!=0,S=((ins>>>20)&1)!=0;int rd=(ins>>>16)&15,rn=(ins>>>12)&15,rs=(ins>>>8)&15,rm=ins&15;int v=reg(rm)*reg(rs);if(A)v+=reg(rn);setreg(rd,v);if(S)setNZ(v);return;}
            if((ins&0x0e000090)==0x00000090){
                int P=(ins>>>24)&1,U=(ins>>>23)&1,I=(ins>>>22)&1,W=(ins>>>21)&1,L=(ins>>>20)&1,rn=(ins>>>16)&15,rd=(ins>>>12)&15,S=(ins>>>6)&1,H=(ins>>>5)&1;
                int off=I!=0?(((ins>>>8)&15)<<4)|(ins&15):reg(ins&15);int base=reg(rn),addr=base,delta=U!=0?off:-off;if(P!=0)addr=base+delta;
                if(L!=0){int v;if(S!=0&&H!=0){v=m.r16(addr);if((v&0x8000)!=0)v|=0xffff0000;}else if(S!=0){v=m.r8(addr);if((v&0x80)!=0)v|=0xffffff00;}else if(H!=0)v=m.r16(addr);else throw bad("half xfer",ins,pc);setreg(rd,v);}
                else {if(H!=0)m.w16(addr,reg(rd));else throw bad("half store",ins,pc);} if(P==0)base+=delta;if(W!=0||P==0)setreg(rn,P==0?base:addr);return;
            }
            if(((ins>>>25)&7)==4){
                int P=(ins>>>24)&1,U=(ins>>>23)&1,W=(ins>>>21)&1,L=(ins>>>20)&1,rn=(ins>>>16)&15,list=ins&0xffff,n=Integer.bitCount(list),base=reg(rn);int start=U!=0?base+(P!=0?4:0):base-4*n+(P!=0?0:4),addr=start;
                for(int rr=0;rr<16;rr++)if(((list>>>rr)&1)!=0){if(L!=0)setreg(rr,m.r32(addr));else m.w32(addr,reg(rr));addr+=4;} if(W!=0)setreg(rn,base+(U!=0?4*n:-4*n));if(L!=0&&(list&0x8000)!=0){int t=r[15];thumb=(t&1)!=0;r[15]=t&~1;}return;
            }
            if(((ins>>>26)&3)==1){
                int I=(ins>>>25)&1,P=(ins>>>24)&1,U=(ins>>>23)&1,B=(ins>>>22)&1,W=(ins>>>21)&1,L=(ins>>>20)&1,rn=(ins>>>16)&15,rd=(ins>>>12)&15;int off;
                if(I!=0){int rm=ins&15,typ=(ins>>>5)&3,amt=(ins>>>7)&31;off=shift(reg(rm),typ,amt,false).v;}else off=ins&0xfff;
                int base=reg(rn),delta=U!=0?off:-off,addr=P!=0?base+delta:base;
                if(L!=0){int v=B!=0?m.r8(addr):m.r32(addr);if(rd==15){thumb=(v&1)!=0;r[15]=v&~1;}else setreg(rd,v);}else{if(B!=0)m.w8(addr,reg(rd));else m.w32(addr,reg(rd));}
                if(W!=0||P==0)setreg(rn,base+delta);return;
            }
            if((ins&0x0fbf0fff)==0x010f0000){int rd=(ins>>>12)&15;setreg(rd,(N<<31)|(Z<<30)|(C<<29)|(V<<28)|(thumb?(1<<5):0));return;}
            if((ins&0x0fff0ff0)==0x016f0f10){int rd=(ins>>>12)&15,rm=ins&15,v=reg(rm);setreg(rd,Integer.numberOfLeadingZeros(v));return;}
            if(((ins>>>26)&3)==0){
                int op=(ins>>>21)&15,S=(ins>>>20)&1,rn=(ins>>>16)&15,rd=(ins>>>12)&15,a=reg(rn);Shift sh=op2(ins);int b=sh.v,res=0,carry=C,ov=V;boolean write=true;Arith ar;
                switch(op){
                    case 0:res=a&b;carry=sh.c;break;case 1:res=a^b;carry=sh.c;break;case 2:ar=subc(a,b,1);res=ar.v;carry=ar.c;ov=ar.o;break;case 3:ar=subc(b,a,1);res=ar.v;carry=ar.c;ov=ar.o;break;
                    case 4:ar=addc(a,b,0);res=ar.v;carry=ar.c;ov=ar.o;break;case 5:ar=addc(a,b,C);res=ar.v;carry=ar.c;ov=ar.o;break;case 6:ar=subc(a,b,C);res=ar.v;carry=ar.c;ov=ar.o;break;case 7:ar=subc(b,a,C);res=ar.v;carry=ar.c;ov=ar.o;break;
                    case 8:res=a&b;carry=sh.c;write=false;break;case 9:res=a^b;carry=sh.c;write=false;break;case 10:ar=subc(a,b,1);res=ar.v;carry=ar.c;ov=ar.o;write=false;break;case 11:ar=addc(a,b,0);res=ar.v;carry=ar.c;ov=ar.o;write=false;break;
                    case 12:res=a|b;carry=sh.c;break;case 13:res=b;carry=sh.c;break;case 14:res=a&~b;carry=sh.c;break;case 15:res=~b;carry=sh.c;break;
                }
                if(S!=0||!write){setNZ(res);C=carry;V=ov;}if(write){if(rd==15){thumb=(res&1)!=0;r[15]=res&~1;}else setreg(rd,res);}return;
            }
            throw bad("UNKNOWN ARM",ins,pc);
        }
        void stepThumb(){
            int pc=cur,ins=m.r16(pc);r[15]=pc+2;
            if((ins&0xe000)==0x0000 && (ins&0x1800)!=0x1800){int op=(ins>>>11)&3,imm=(ins>>>6)&31,rs=(ins>>>3)&7,rd=ins&7;Shift sh=shift(reg(rs),op,imm,false);setreg(rd,sh.v);setNZ(sh.v);C=sh.c;return;}
            if((ins&0xf800)==0x1800){int I=(ins>>>10)&1,sub=(ins>>>9)&1,rn=(ins>>>6)&7,rs=(ins>>>3)&7,rd=ins&7,b=I!=0?rn:reg(rn),a=reg(rs);Arith x=sub!=0?subc(a,b,1):addc(a,b,0);setreg(rd,x.v);setNZ(x.v);C=x.c;V=x.o;return;}
            if((ins&0xe000)==0x2000){int op=(ins>>>11)&3,rd=(ins>>>8)&7,imm=ins&255;Arith x;if(op==0){setreg(rd,imm);setNZ(imm);}else if(op==1){x=subc(reg(rd),imm,1);setNZ(x.v);C=x.c;V=x.o;}else if(op==2){x=addc(reg(rd),imm,0);setreg(rd,x.v);setNZ(x.v);C=x.c;V=x.o;}else{x=subc(reg(rd),imm,1);setreg(rd,x.v);setNZ(x.v);C=x.c;V=x.o;}return;}
            if((ins&0xfc00)==0x4000){
                int op=(ins>>>6)&15,rs=(ins>>>3)&7,rd=ins&7,a=reg(rd),b=reg(rs),v=0,c=C,o=V;boolean wr=true;Arith x;Shift sh;
                switch(op){case 0:v=a&b;break;case 1:v=a^b;break;case 2:sh=shift(a,0,b&255,true);v=sh.v;c=sh.c;break;case 3:sh=shift(a,1,b&255,true);v=sh.v;c=sh.c;break;case 4:sh=shift(a,2,b&255,true);v=sh.v;c=sh.c;break;
                    case 5:x=addc(a,b,C);v=x.v;c=x.c;o=x.o;break;case 6:x=subc(a,b,C);v=x.v;c=x.c;o=x.o;break;case 7:sh=shift(a,3,b&255,true);v=sh.v;c=sh.c;break;case 8:v=a&b;wr=false;break;
                    case 9:v=-b;c=b==0?1:0;o=b==0x80000000?1:0;break;case 10:x=subc(a,b,1);v=x.v;c=x.c;o=x.o;wr=false;break;case 11:x=addc(a,b,0);v=x.v;c=x.c;o=x.o;wr=false;break;
                    case 12:v=a|b;break;case 13:v=a*b;break;case 14:v=a&~b;break;default:v=~b;break;}
                setNZ(v);C=c;V=o;if(wr)setreg(rd,v);return;
            }
            if((ins&0xfc00)==0x4400){int op=(ins>>>8)&3,h1=(ins>>>7)&1,h2=(ins>>>6)&1,rs=((ins>>>3)&7)|(h2<<3),rd=(ins&7)|(h1<<3);if(op==0){setreg(rd,reg(rd)+reg(rs));return;}if(op==1){Arith x=subc(reg(rd),reg(rs),1);setNZ(x.v);C=x.c;V=x.o;return;}if(op==2){int v=reg(rs);if(rd==15){thumb=(v&1)!=0;r[15]=v&~1;}else setreg(rd,v);return;}int t=reg(rs);thumb=(t&1)!=0;r[15]=t&~1;return;}
            if((ins&0xf800)==0x4800){int rd=(ins>>>8)&7,addr=((pc+4)&~3)+((ins&255)<<2);setreg(rd,m.r32(addr));return;}
            if((ins&0xf000)==0x5000){int ro=(ins>>>6)&7,rb=(ins>>>3)&7,rd=ins&7,addr=reg(rb)+reg(ro),op=(ins>>>9)&7,v;switch(op){case 0:m.w32(addr,reg(rd));break;case 1:m.w16(addr,reg(rd));break;case 2:m.w8(addr,reg(rd));break;case 3:v=m.r8(addr);setreg(rd,(v&0x80)!=0?v|0xffffff00:v);break;case 4:setreg(rd,m.r32(addr));break;case 5:setreg(rd,m.r16(addr));break;case 6:setreg(rd,m.r8(addr));break;default:v=m.r16(addr);setreg(rd,(v&0x8000)!=0?v|0xffff0000:v);break;}return;}
            if((ins&0xe000)==0x6000){int B=(ins>>>12)&1,L=(ins>>>11)&1,imm=(ins>>>6)&31,rb=(ins>>>3)&7,rd=ins&7,addr=reg(rb)+(B!=0?imm:imm*4);if(L!=0)setreg(rd,B!=0?m.r8(addr):m.r32(addr));else if(B!=0)m.w8(addr,reg(rd));else m.w32(addr,reg(rd));return;}
            if((ins&0xf000)==0x8000){int L=(ins>>>11)&1,imm=((ins>>>6)&31)*2,rb=(ins>>>3)&7,rd=ins&7,addr=reg(rb)+imm;if(L!=0)setreg(rd,m.r16(addr));else m.w16(addr,reg(rd));return;}
            if((ins&0xf000)==0x9000){int L=(ins>>>11)&1,rd=(ins>>>8)&7,addr=reg(13)+((ins&255)<<2);if(L!=0)setreg(rd,m.r32(addr));else m.w32(addr,reg(rd));return;}
            if((ins&0xf000)==0xa000){int SP=(ins>>>11)&1,rd=(ins>>>8)&7,base=SP!=0?reg(13):((pc+4)&~3);setreg(rd,base+((ins&255)<<2));return;}
            if((ins&0xff00)==0xb000){int d=(ins&0x7f)<<2;setreg(13,(ins&0x80)!=0?reg(13)-d:reg(13)+d);return;}
            if((ins&0xf600)==0xb400){int L=(ins>>>11)&1,R=(ins>>>8)&1,list=ins&255;if(L==0){int n=Integer.bitCount(list)+(R!=0?1:0),sp=reg(13)-4*n,a=sp;for(int rr=0;rr<8;rr++)if(((list>>>rr)&1)!=0){m.w32(a,reg(rr));a+=4;}if(R!=0){m.w32(a,reg(14));}setreg(13,sp);}else{int a=reg(13);for(int rr=0;rr<8;rr++)if(((list>>>rr)&1)!=0){setreg(rr,m.r32(a));a+=4;}if(R!=0){int v=m.r32(a);a+=4;thumb=(v&1)!=0;r[15]=v&~1;}setreg(13,a);}return;}
            if((ins&0xf000)==0xc000){int L=(ins>>>11)&1,rb=(ins>>>8)&7,list=ins&255,a=reg(rb);for(int rr=0;rr<8;rr++)if(((list>>>rr)&1)!=0){if(L!=0)setreg(rr,m.r32(a));else m.w32(a,reg(rr));a+=4;}setreg(rb,a);return;}
            if((ins&0xf000)==0xd000){int cc=(ins>>>8)&15;if(cc==15)throw new RuntimeException("THUMB SWI @"+hex(pc));if(cc==14)throw new RuntimeException("THUMB undef @"+hex(pc));if(cond(cc)){int off=(ins&255)<<1;if((off&0x100)!=0)off-=0x200;r[15]=pc+4+off;}return;}
            if((ins&0xf800)==0xe000){int off=(ins&0x7ff)<<1;if((off&0x800)!=0)off-=0x1000;r[15]=pc+4+off;return;}
            if((ins&0xf800)==0xf000){int off=(ins&0x7ff)<<12;if((off&0x400000)!=0)off-=0x800000;r[14]=pc+4+off;return;}
            if((ins&0xf800)==0xf800){int target=r[14]+((ins&0x7ff)<<1);r[14]=(pc+2)|1;r[15]=target&~1;thumb=true;return;}
            throw new RuntimeException(String.format(Locale.US,"UNKNOWN THUMB %04X @%08X",ins,pc));
        }
        RuntimeException bad(String what,int ins,int pc){return new RuntimeException(String.format(Locale.US,"%s %08X @%08X",what,ins,pc));}
    }

    private static final class TrapInfo {
        static final int SYS=1,SHELL=2,IFACE=3,FILE=4;
        final int type,index,arg; final String name;
        TrapInfo(int t,int i,int a,String n){type=t;index=i;arg=a;name=n;}
    }

    private static final class HostBridge {
        final Map<Integer,TrapInfo> traps = new HashMap<>();
        Host host;
        void dispatch(CPU c,int addr){
            TrapInfo t=traps.get(addr); if(t==null)throw new RuntimeException("UNMAPPED TRAP "+hex(addr));
            if(t.type==TrapInfo.SYS)host.genericSys(c,t.name,t.index);
            else if(t.type==TrapInfo.SHELL)host.shellCall(c,t.index);
            else if(t.type==TrapInfo.FILE)host.fileCall(c,t.arg,t.index);
            else host.ifaceCall(c,t.arg,t.index);
            c.r[15]=c.r[14]&~1; c.thumb=(c.r[14]&1)!=0;
        }
    }

    private static final class ImageState {
        final int id; final byte[] data; final int width, height; final int[] pixels;
        ImageState(int id, byte[] data, int width, int height, int[] pixels){
            this.id=id;this.data=data;this.width=width;this.height=height;this.pixels=pixels;
        }
    }

    private static final class BitmapState {
        final int width, height;
        final int[] pixels;
        BitmapState(int width,int height){
            this.width=width;this.height=height;
            this.pixels=new int[Math.max(0,width*height)];
            Arrays.fill(this.pixels,0xFF000000);
        }
    }

    private static final class FileState {
        final String name;
        byte[] data;
        int pos;
        final int mode;
        FileState(String name, byte[] data, int mode){this.name=name;this.data=data==null?new byte[0]:data.clone();this.mode=mode;}
    }

    private static final class ResourceData {
        final int id; final int type; final int ptr; final byte[] data;
        ResourceData(int id,int type,int ptr,byte[] data){this.id=id;this.type=type;this.ptr=ptr;this.data=data;}
    }

    private static final class MediaState {
        final int object;
        int state;
        int clsData;
        int dataPtr;
        int dataSize;
        int resourceId;
        byte[] data;
        int notifyCallback;
        int notifyUser;
        int volume=200;
        MediaState(int object){this.object=object;}
    }

    private static final class Host {
        static final int CLSID_DISPLAY=0x01001001;
        static final int CLSID_FILEMGR=0x01001003;
        static final int CLSID_HOST_BITMAP=0x7E000001;
        static final int CLSID_MEDIA=0x01005501;
        final CPU c; final StringBuilder log; int nextTrap=TRAP_BASE;
        final int table1=0x02000000, table2=0x02001000, shell=0x02002000, shellvt=0x02003000;
        final Map<Integer,Integer> interfaces = new HashMap<>();
        final Map<Integer,FileState> openFiles = new HashMap<>();
        final Map<Integer,ImageState> images = new HashMap<>();
        final Map<Integer,BitmapState> bitmaps = new HashMap<>();
        final Map<Integer,ResourceData> resourceByPtr = new HashMap<>();
        final Map<Integer,MediaState> media = new HashMap<>();
        final java.util.ArrayList<AudioEvent> audioEvents = new java.util.ArrayList<>();
        final Map<String,byte[]> filePayloads = new HashMap<>();
        int fileOpenCount;
        int fileReadBytes;
        boolean progressFileRead;
        int resourceLoads;
        int imageLoads;
        int imageInfoCalls;
        int imagePixelDecodes;
        int imagePixelDecodeFailures;
        int mediaCreates;
        int mediaSetDataCalls;
        int mediaPlayRequests;
        int mediaStopRequests;
        int lastAudioResourceId;
        long audioSerial;
        int unknownInterfaceCalls;
        int lastUnknownInterfaceClass;
        int lastUnknownInterfaceOffset;
        int timerSchedules;
        int lastTimerCallback;
        int lastTimerData;
        int compatibleBitmapCreates;
        int displayDestinationGets;
        int displayDestinationSets;
        int imageDrawCalls;
        int drawRectCalls;
        int bitBltCalls;
        int displayUpdates;
        int fakeUptimeMs=1000;
        int displayDestination;
        int deviceBitmap;
        final byte[] barBytes;
        final PixelDecoder decoder;
        Host(CPU c,StringBuilder log,byte[] progress,byte[] bar,PixelDecoder decoder){
            this.c=c;this.log=log;this.decoder=decoder;c.bridge.host=this;c.m.w32(BASE-8,table1);c.m.w32(BASE-4,table2);
            filePayloads.put("progress.bin",progress==null?new byte[0]:progress.clone());
            this.barBytes=bar==null?new byte[0]:bar.clone();
            filePayloads.put("mmassaultbacksmall.bar",this.barBytes);
            for(int i=0;i<256;i++){c.m.w32(table1+i*4,mk(new TrapInfo(TrapInfo.SYS,i,0,"SYS_A")));c.m.w32(table2+i*4,mk(new TrapInfo(TrapInfo.SYS,i,0,"SYS_B")));}
            for(int i=0;i<256;i++)c.m.w32(shellvt+i*4,mk(new TrapInfo(TrapInfo.SHELL,i,0,"ISHELL")));
            c.m.w32(shell,shellvt);
        }
        int mk(TrapInfo ti){int a=nextTrap;nextTrap+=4;c.bridge.traps.put(a,ti);return a;}
        void log(String s){append(log,s);}
        String regs(){return String.format(Locale.US,"r0=%08X r1=%08X r2=%08X r3=%08X",c.r[0],c.r[1],c.r[2],c.r[3]);}
        String cstr(int ptr){
            StringBuilder b=new StringBuilder();
            for(int i=0;i<256;i++){int ch=c.m.r8(ptr+i);if(ch==0)break;b.append((char)ch);}return b.toString();
        }
        int stackArg(int n){return c.m.r32(c.r[13]+(n-4)*4);}
        byte[] barResource(int id){
            int index=-1;
            if(id>=0x2329 && id<=0x233B) index=id-0x2329;
            else if(id>=0x1389 && id<=0x13C0) index=19+(id-0x1389);
            if(index<0 || index>=75 || barBytes.length<0x160)return null;
            int off=c32(barBytes,0x30+index*4);
            int end=index+1<75?c32(barBytes,0x30+(index+1)*4):barBytes.length;
            if(off<0 || end<off || end>barBytes.length)return null;
            return Arrays.copyOfRange(barBytes,off,end);
        }
        int c32(byte[] a,int o){return (a[o]&255)|((a[o+1]&255)<<8)|((a[o+2]&255)<<16)|((a[o+3]&255)<<24);}
        int loadResData(int id,int type){
            byte[] d=barResource(id);if(d==null){log(String.format(Locale.US,"  -> LoadResData MISS id=%04X type=%04X",id,type));return 0;}
            int p=c.alloc(d.length+4,4);for(int i=0;i<d.length;i++)c.m.w8(p+i,d[i]&255);
            resourceByPtr.put(p,new ResourceData(id,type,p,d.clone()));
            int pSize=stackArg(5);if(pSize!=0)c.m.w32(pSize,d.length);
            resourceLoads++;log(String.format(Locale.US,"  -> LoadResData id=%04X type=%04X ptr=%08X bytes=%d",id,type,p,d.length));return p;
        }
        void genericSys(CPU c,String name,int idx){int off=idx*4;log(String.format(Locale.US,"TRAP %s+0x%X %s lr=%08X",name,off,regs(),c.r[14]));if(off==0x68){int n=c.r[0];if(n<0 || n>8*1024*1024)throw new RuntimeException("invalid malloc size "+n+" at lr="+hex(c.r[14]));int p=c.alloc(n,8);c.r[0]=p;log(String.format(Locale.US,"  -> malloc %08X",p));}else if(off==0xB0){fakeUptimeMs+=10;c.r[0]=fakeUptimeMs;log("  -> uptimeMs "+fakeUptimeMs);}else c.r[0]=0;}
        int makeInterface(int clsid){
            int vt=c.alloc(0x400,4),obj=c.alloc(0x20,4);interfaces.put(obj,clsid);
            for(int i=0;i<256;i++)c.m.w32(vt+i*4,mk(new TrapInfo(TrapInfo.IFACE,i,obj,"IFACE")));c.m.w32(obj,vt);
            if(clsid==CLSID_MEDIA){media.put(obj,new MediaState(obj));mediaCreates++;log(String.format(Locale.US,"  -> IMedia object created %08X",obj));}
            return obj;
        }
        int makeFile(String name,byte[] data,int mode){int vt=c.alloc(0x100,4),obj=c.alloc(0x20,4);openFiles.put(obj,new FileState(name,data,mode));for(int i=0;i<64;i++)c.m.w32(vt+i*4,mk(new TrapInfo(TrapInfo.FILE,i,obj,"IFILE")));c.m.w32(obj,vt);return obj;}
        int makeImage(int id,byte[] data){
            int obj=makeInterface(0x7F000000|id),w=0,h=0,pngOff=-1;
            for(int i=0;i+24<=data.length;i++){
                if((data[i]&255)==0x89 && data[i+1]=='P' && data[i+2]=='N' && data[i+3]=='G'
                        && (data[i+4]&255)==0x0D && (data[i+5]&255)==0x0A
                        && (data[i+6]&255)==0x1A && (data[i+7]&255)==0x0A){
                    pngOff=i;w=be32(data,i+16);h=be32(data,i+20);break;
                }
            }
            int[] px=null;
            if(decoder!=null && pngOff>=0 && w>0 && h>0){
                try{
                    // BAR resources carry a small BREW resource header before the PNG.
                    // Desktop ImageIO tolerated that prefix; Android BitmapFactory does not.
                    // Give every host decoder the exact PNG stream beginning at 89 50 4E 47.
                    byte[] png=Arrays.copyOfRange(data,pngOff,data.length);
                    px=decoder.decodePng(png,w,h);
                    if(px!=null){imagePixelDecodes++;log(String.format(Locale.US,"  -> PNG pixels decoded id=%04X off=%d count=%d",id,pngOff,px.length));}
                    else {imagePixelDecodeFailures++;log(String.format(Locale.US,"  -> PNG pixel decode returned null id=%04X off=%d",id,pngOff));}
                } catch(Exception e){
                    imagePixelDecodeFailures++;
                    log("  -> PNG decode failed id="+String.format(Locale.US,"%04X",id)+" off="+pngOff+": "+e.getMessage());
                }
            }
            images.put(obj,new ImageState(id,data,w,h,px));
            return obj;
        }
        int makeBitmap(int w,int h){int obj=makeInterface(CLSID_HOST_BITMAP);bitmaps.put(obj,new BitmapState(w,h));return obj;}
        int ensureDeviceBitmap(){
            if(deviceBitmap==0) deviceBitmap=makeBitmap(128,160);
            return deviceBitmap;
        }
        int be32(byte[] a,int o){return ((a[o]&255)<<24)|((a[o+1]&255)<<16)|((a[o+2]&255)<<8)|(a[o+3]&255);}
        void ifaceCall(CPU c,int obj,int idx){
            int cls=interfaces.containsKey(obj)?interfaces.get(obj):0,off=idx*4;
            log(String.format(Locale.US,"TRAP IFACE(%08X)+0x%X %s",cls,off,regs()));
            if(off==0||off==4){c.r[0]=1;return;}
            MediaState ms=media.get(obj);
            if(ms!=null){
                if(off==0x0C){
                    ms.notifyCallback=c.r[1];ms.notifyUser=c.r[2];c.r[0]=0;
                    log(String.format(Locale.US,"  -> IMedia.RegisterNotify cb=%08X user=%08X",ms.notifyCallback,ms.notifyUser));return;
                }
                if(off==0x10){
                    int parm=c.r[1],value=c.r[2];
                    if(parm==1 && value!=0){
                        ms.clsData=c.m.r32(value);ms.dataPtr=c.m.r32(value+4);ms.dataSize=c.m.r32(value+8);
                        ResourceData rd=resourceByPtr.get(ms.dataPtr);
                        if(rd==null){
                            for(ResourceData x:resourceByPtr.values()){if(ms.dataPtr>=x.ptr && ms.dataPtr<x.ptr+x.data.length){rd=x;break;}}
                        }
                        if(rd!=null){
                            ms.resourceId=rd.id;int n=ms.dataSize>0?Math.min(ms.dataSize,rd.data.length):rd.data.length;ms.data=Arrays.copyOf(rd.data,n);
                            lastAudioResourceId=rd.id;
                        }
                        mediaSetDataCalls++;
                        log(String.format(Locale.US,"  -> IMedia.SetMediaData clsData=%d ptr=%08X size=%d resource=%04X",ms.clsData,ms.dataPtr,ms.dataSize,ms.resourceId));
                    }else if(parm==9){ms.volume=value;log("  -> IMedia volume "+value);}
                    else log(String.format(Locale.US,"  -> IMedia.SetMediaParm parm=%d value=%08X extra=%08X",parm,value,c.r[3]));
                    c.r[0]=0;return;
                }
                if(off==0x28){
                    ms.state=1;mediaStopRequests++;long serial=++audioSerial;
                    audioEvents.add(new AudioEvent(serial,AudioEvent.STOP,obj,ms.resourceId,null));trimAudioEvents();
                    log(String.format(Locale.US,"  -> IMedia.Stop resource=%04X serial=%d",ms.resourceId,serial));c.r[0]=0;return;
                }
                if(off==0x2C){
                    ms.state=3;mediaPlayRequests++;long serial=++audioSerial;lastAudioResourceId=ms.resourceId;
                    audioEvents.add(new AudioEvent(serial,AudioEvent.PLAY,obj,ms.resourceId,ms.data));trimAudioEvents();
                    log(String.format(Locale.US,"  -> IMedia.Play resource=%04X bytes=%d serial=%d",ms.resourceId,ms.data==null?0:ms.data.length,serial));c.r[0]=0;return;
                }
                if(off==0x30){
                    log("  -> IMedia channel-share/setup call accepted");c.r[0]=0;return;
                }
                if(off==0x34){
                    if(c.r[1]!=0)c.m.w8(c.r[1],0);c.r[0]=ms.state;
                    log(String.format(Locale.US,"  -> IMedia.GetState state=%d",ms.state));return;
                }
                if(off==0x18){
                    // Remaining IMedia query used during live playback. Returning success preserves the measured path.
                    c.r[0]=0;log("  -> IMedia auxiliary query accepted");return;
                }
            }
            ImageState im=images.get(obj);
            if(im!=null){
                if(off==0x10){int p=c.r[1];c.m.w16(p,im.width);c.m.w16(p+2,im.height);imageInfoCalls++;c.r[0]=0;log(String.format(Locale.US,"  -> IImage.GetInfo id=%04X %dx%d @%08X",im.id,im.width,im.height,p));return;}
                if(off==0x8){
                    imageDrawCalls++;
                    int x=c.r[1],y=c.r[2];
                    int dstObj=displayDestination==0?ensureDeviceBitmap():displayDestination;
                    BitmapState dst=bitmaps.get(dstObj);
                    if(dst!=null && im.pixels!=null) blit(dst,im.pixels,im.width,im.height,x,y);
                    c.r[0]=0;
                    log(String.format(Locale.US,"  -> IImage.Draw id=%04X x=%d y=%d pixels=%s dst=%08X",im.id,x,y,im.pixels==null?"no":"yes",dstObj));
                    return;
                }
                c.r[0]=0;return;
            }
            BitmapState bm=bitmaps.get(obj);
            if(bm!=null){
                if(off==0x34){
                    int pp=c.r[1],w=c.r[2]&0xffff,h=c.r[3]&0xffff;
                    int made=makeBitmap(w,h);compatibleBitmapCreates++;c.m.w32(pp,made);c.r[0]=0;
                    log(String.format(Locale.US,"  -> IBitmap.CreateCompatibleBitmap %dx%d obj=%08X @%08X",w,h,made,pp));return;
                }
                c.r[0]=0;return;
            }
            if(cls==CLSID_DISPLAY){
                if(off==0x18){
                    // IDisplay.BitBlt(self, xDst, yDst, cx, cy, srcBitmap, xSrc, ySrc, rop).
                    // The calling convention is confirmed from the game's live stack:
                    // r1=x, r2=y, r3=width; height/src/srcX/srcY/ROP follow on stack.
                    int x=c.r[1],y=c.r[2],w=c.r[3],hgt=stackArg(4),srcObj=stackArg(5),sx=stackArg(6),sy=stackArg(7),rop=stackArg(8);
                    int dstObj=displayDestination==0?ensureDeviceBitmap():displayDestination;
                    BitmapState src=bitmaps.get(srcObj),dst=bitmaps.get(dstObj);
                    if(src!=null&&dst!=null) blitBitmap(dst,src,x,y,w,hgt,sx,sy,rop);
                    bitBltCalls++;c.r[0]=0;
                    log(String.format(Locale.US,"  -> IDisplay.BitBlt dst=(%d,%d %dx%d) src=%08X@(%d,%d) rop=%08X active=%08X srcKnown=%s",
                            x,y,w,hgt,srcObj,sx,sy,rop,dstObj,src==null?"no":"yes"));
                    return;
                }
                if(off==0x14){
                    int p=c.r[1];
                    int x=(short)c.m.r16(p), y=(short)c.m.r16(p+2), dx=(short)c.m.r16(p+4), dy=(short)c.m.r16(p+6);
                    int frame=c.r[2], fill=c.r[3], flags=stackArg(4);
                    int dstObj=displayDestination==0?ensureDeviceBitmap():displayDestination;
                    BitmapState dst=bitmaps.get(dstObj);
                    if(dst!=null){
                        if(fill!=0xFFFFFFFF) fillRect(dst,x,y,dx,dy,toArgb(fill));
                        if(frame!=0xFFFFFFFF) strokeRect(dst,x,y,dx,dy,toArgb(frame));
                    }
                    drawRectCalls++;c.r[0]=0;
                    log(String.format(Locale.US,"  -> IDisplay.DrawRect rect=(%d,%d %dx%d) frame=%08X fill=%08X flags=%08X dst=%08X",
                            x,y,dx,dy,frame,fill,flags,dstObj));
                    return;
                }
                if(off==0x1C){
                    displayUpdates++;ensureDeviceBitmap();c.r[0]=0;
                    log(String.format(Locale.US,"  -> IDisplay.Update #%d device=%08X",displayUpdates,deviceBitmap));
                    return;
                }
                if(off==0x3C){
                    if(displayDestination==0) displayDestination=ensureDeviceBitmap();
                    displayDestinationGets++;
                    c.r[0]=displayDestination;
                    log(String.format(Locale.US,"  -> IDisplay.GetDestination obj=%08X",displayDestination));return;
                }
                if(off==0x38){
                    displayDestinationSets++;
                    int requested=c.r[1];
                    displayDestination=requested==0?ensureDeviceBitmap():requested;
                    c.r[0]=0;
                    log(String.format(Locale.US,"  -> IDisplay.SetDestination requested=%08X active=%08X",requested,displayDestination));return;
                }
            }
            if(cls==CLSID_FILEMGR && off==0x8){
                String name=cstr(c.r[1]);int mode=c.r[2];byte[] payload=filePayloads.get(name.toLowerCase(Locale.US));
                if(payload==null){c.r[0]=0;log("  -> OpenFile MISS name="+name+" mode="+mode);return;}
                int f=makeFile(name,payload,mode);fileOpenCount++;c.r[0]=f;log(String.format(Locale.US,"  -> OpenFile name=%s mode=%d obj=%08X bytes=%d",name,mode,f,payload.length));return;
            }
            unknownInterfaceCalls++;
            lastUnknownInterfaceClass=cls;
            lastUnknownInterfaceOffset=off;
            log(String.format(Locale.US,"  -> UNIMPLEMENTED IFACE cls=%08X off=0x%X (returning 0)",cls,off));
            c.r[0]=0;
        }
        void trimAudioEvents(){while(audioEvents.size()>64)audioEvents.remove(0);}
        int toArgb(int brew){
            int r=brew&0xFF, g=(brew>>>8)&0xFF, b=(brew>>>16)&0xFF;
            return 0xFF000000|(r<<16)|(g<<8)|b;
        }
        void fillRect(BitmapState bm,int x,int y,int w,int h,int argb){
            if(w<=0||h<=0)return;
            int x0=Math.max(0,x),y0=Math.max(0,y),x1=Math.min(bm.width,x+w),y1=Math.min(bm.height,y+h);
            if(x1<=x0||y1<=y0)return;
            for(int yy=y0;yy<y1;yy++) Arrays.fill(bm.pixels,yy*bm.width+x0,yy*bm.width+x1,argb);
        }
        void strokeRect(BitmapState bm,int x,int y,int w,int h,int argb){
            if(w<=0||h<=0)return;
            fillRect(bm,x,y,w,1,argb);fillRect(bm,x,y+h-1,w,1,argb);
            fillRect(bm,x,y,1,h,argb);fillRect(bm,x+w-1,y,1,h,argb);
        }
        void blit(BitmapState dst,int[] src,int sw,int sh,int x,int y){
            for(int sy=0;sy<sh;sy++){
                int dy=y+sy;if(dy<0||dy>=dst.height)continue;
                for(int sx=0;sx<sw;sx++){
                    int dx=x+sx;if(dx<0||dx>=dst.width)continue;
                    int sp=src[sy*sw+sx],a=(sp>>>24)&255;
                    if(a==0)continue;
                    int di=dy*dst.width+dx;
                    if(a==255){dst.pixels[di]=sp|0xFF000000;continue;}
                    int dp=dst.pixels[di],ia=255-a;
                    int rr=(((sp>>>16)&255)*a+((dp>>>16)&255)*ia)/255;
                    int gg=(((sp>>>8)&255)*a+((dp>>>8)&255)*ia)/255;
                    int bb=((sp&255)*a+(dp&255)*ia)/255;
                    dst.pixels[di]=0xFF000000|(rr<<16)|(gg<<8)|bb;
                }
            }
        }

        void blitBitmap(BitmapState dst,BitmapState src,int dx,int dy,int w,int h,int sx,int sy,int rop){
            if(w<=0||h<=0)return;
            // Rush Marine currently uses ROP 7 for keyed sprite/image copies. The
            // preserved off-screen bitmaps are black-backed after IImage.Draw, so
            // treat opaque black as the transparent key for that measured path.
            boolean keyed=(rop==7);
            for(int yy=0;yy<h;yy++){
                int yySrc=sy+yy,yyDst=dy+yy;if(yySrc<0||yySrc>=src.height||yyDst<0||yyDst>=dst.height)continue;
                for(int xx=0;xx<w;xx++){
                    int xxSrc=sx+xx,xxDst=dx+xx;if(xxSrc<0||xxSrc>=src.width||xxDst<0||xxDst>=dst.width)continue;
                    int sp=src.pixels[yySrc*src.width+xxSrc];
                    if(keyed && (sp&0x00FFFFFF)==0)continue;
                    dst.pixels[yyDst*dst.width+xxDst]=sp|0xFF000000;
                }
            }
        }

        void fileCall(CPU c,int obj,int idx){
            FileState f=openFiles.get(obj);int off=idx*4;
            log(String.format(Locale.US,"TRAP IFILE(%s)+0x%X %s",f==null?"?":f.name,off,regs()));
            if(f==null){c.r[0]=0;return;}
            if(off==0){c.r[0]=1;return;}
            if(off==4){openFiles.remove(obj);c.r[0]=0;return;}
            if(off==0x14){
                int dst=c.r[1],want=c.r[2],n=Math.max(0,Math.min(want,f.data.length-f.pos));
                for(int i=0;i<n;i++)c.m.w8(dst+i,f.data[f.pos+i]&0xff);f.pos+=n;fileReadBytes+=n;
                if("progress.bin".equalsIgnoreCase(f.name)&&n>0)progressFileRead=true;
                c.r[0]=n;log(String.format(Locale.US,"  -> Read %d/%d bytes pos=%d",n,want,f.pos));return;
            }
            if(off==0x18){
                int src=c.r[1],want=c.r[2],need=f.pos+want;if(need>f.data.length)f.data=Arrays.copyOf(f.data,need);
                for(int i=0;i<want;i++)f.data[f.pos+i]=(byte)c.m.r8(src+i);f.pos+=want;c.r[0]=want;log(String.format(Locale.US,"  -> Write %d bytes pos=%d",want,f.pos));return;
            }
            c.r[0]=0;
        }
        void shellCall(CPU c,int idx){
            int off=idx*4;log(String.format(Locale.US,"TRAP ISHELL+0x%X %s",off,regs()));
            if(off==0||off==4){c.r[0]=1;return;}
            if(off==8){int clsid=c.r[1],pp=c.r[2],obj=makeInterface(clsid);c.m.w32(pp,obj);c.r[0]=0;log(String.format(Locale.US,"  -> CreateInstance cls=%08X obj=%08X",clsid,obj));return;}
            if(off==0x10){
                // ISHELL_GetDeviceInfo. Rush Marine is the preserved 128x160 handset build;
                // AEEDeviceInfo begins with uint16 cxScreen, uint16 cyScreen on this BREW ABI.
                int pInfo=c.r[1];
                c.m.w16(pInfo,128);
                c.m.w16(pInfo+2,160);
                // Give the immediately-used prefix deterministic zeroes beyond dimensions.
                for(int i=4;i<32;i++) c.m.w8(pInfo+i,0);
                c.r[0]=0;
                log(String.format(Locale.US,"  -> GetDeviceInfo 128x160 @%08X",pInfo));
                return;
            }
            if(off==0x2C){timerSchedules++;lastTimerCallback=c.r[2];lastTimerData=c.r[3];c.r[0]=0;log(String.format(Locale.US,"  -> SetTimer %dms cb=%08X data=%08X",c.r[1],lastTimerCallback,lastTimerData));return;}
            if(off==0xA4){c.r[0]=loadResData(c.r[2]&0xffff,c.r[3]);return;}
            if(off==0x4C){int id=c.r[2]&0xffff;byte[] d=barResource(id);if(d==null){c.r[0]=0;log(String.format(Locale.US,"  -> LoadResImage MISS id=%04X",id));return;}int obj=makeImage(id,d);imageLoads++;c.r[0]=obj;ImageState im=images.get(obj);log(String.format(Locale.US,"  -> LoadResImage id=%04X obj=%08X bytes=%d png=%dx%d",id,obj,d.length,im.width,im.height));return;}
            c.r[0]=0;
        }
    }

}