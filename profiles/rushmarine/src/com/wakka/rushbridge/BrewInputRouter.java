package com.wakka.rushbridge;

import com.wakka.bridge.InputLatch;
import com.wakka.rushbridge.runtime.BrewRuntime;

/** Owner-coalesced native BREW events. Never substitutes a DoJa key family. */
public final class BrewInputRouter {
    public interface Sink { void press(int code); void release(int code); void tap(int code); }
    private static final int[] KEYS={BrewRuntime.AVK_0,BrewRuntime.AVK_1,BrewRuntime.AVK_2,
        BrewRuntime.AVK_3,BrewRuntime.AVK_4,BrewRuntime.AVK_5,BrewRuntime.AVK_6,BrewRuntime.AVK_7,
        BrewRuntime.AVK_8,BrewRuntime.AVK_9,BrewRuntime.AVK_CLR,BrewRuntime.AVK_UP,
        BrewRuntime.AVK_DOWN,BrewRuntime.AVK_LEFT,BrewRuntime.AVK_RIGHT,BrewRuntime.AVK_SELECT};
    private final InputLatch owners=new InputLatch();
    private final Sink sink;
    private int sent;
    public BrewInputRouter(Sink sink) { this.sink=sink; }
    public static int maskFor(int code) {
        for(int key:KEYS) if(code==key) return 1<<(code-BrewRuntime.AVK_0);
        return 0;
    }
    public static int sectorKey(int sector) {
        switch(sector) {
            case 0:return BrewRuntime.AVK_RIGHT; case 1:return BrewRuntime.AVK_9;
            case 2:return BrewRuntime.AVK_DOWN; case 3:return BrewRuntime.AVK_7;
            case 4:return BrewRuntime.AVK_LEFT; case 5:return BrewRuntime.AVK_1;
            case 6:return BrewRuntime.AVK_UP; case 7:return BrewRuntime.AVK_3;
            default:return 0;
        }
    }
    public synchronized void setKey(int owner,int key) { setMask(owner,maskFor(key)); }
    public synchronized void setMask(int owner,int mask) {
        int allowed=0;for(int key:KEYS)allowed|=maskFor(key);
        apply(owners.set(owner,mask&allowed));
    }
    public synchronized void remove(int owner) { apply(owners.remove(owner)); }
    public synchronized void clear() { apply(owners.clear()); }
    /** Explicit AUTO/BACK pulse uses the original one-timer native tap path. */
    public synchronized boolean tap(int key) {
        int mask=maskFor(key);if(mask==0||(sent&mask)!=0)return false;
        sink.tap(key);return true;
    }
    public synchronized int mask() { return owners.mask(); }
    public synchronized int contacts() { return owners.contacts(); }
    private void apply(int next) {
        int released=sent&~next,pressed=next&~sent;
        for(int key:KEYS)if((released&maskFor(key))!=0)sink.release(key);
        for(int key:KEYS)if((pressed&maskFor(key))!=0)sink.press(key);
        sent=next;
    }
}
