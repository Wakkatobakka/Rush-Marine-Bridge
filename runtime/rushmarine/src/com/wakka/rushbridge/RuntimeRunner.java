package com.wakka.rushbridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.graphics.BitmapFactory;
import android.os.Build;

import com.wakka.rushbridge.runtime.BrewRuntime;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns the one persistent BREW guest session used by the Android play surface. */
final class RuntimeRunner {
    interface Callback { void done(BrewRuntime.Result result,String fullReport); }
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private static volatile BrewRuntime.Session liveSession;
    static volatile BrewRuntime.Result lastResult;
    static volatile String lastReport;

    private static final Map<Integer,MediaPlayer> AUDIO_PLAYERS=new HashMap<>();
    // Target handset behavior is effectively single-sequencer for MIDI. Keep QCP/SFX independent,
    // but never let two long Standard MIDI resources play over each other.
    private static final java.util.HashSet<Integer> AUDIO_MIDI_OBJECTS=new java.util.HashSet<>();
    private static final java.util.HashSet<Integer> AUDIO_PENDING_STARTS=new java.util.HashSet<>();
    private static long lastAudioSerial;
    private static int audioHostStarts;
    private static int audioHostStops;
    private static int audioHostFailures;
    private static int audioHostLastResource;
    private static volatile String audioHostStatus="IDLE";
    private static volatile boolean hostAudioPaused=true;

    static void run(Context ctx,Callback cb){
        Context app=ctx.getApplicationContext();
        EXEC.execute(()->{
            resetHostAudioInternal();
            BrewRuntime.Result result;
            String report;
            try{
                VerifiedZipStore payload=BrewPayload.store(app.getFilesDir());
                payload.verify();
                byte[] mod=payload.read("277700/mmassault.mod");
                byte[] progress=payload.read("277700/progress.bin");
                byte[] bar=payload.read("277700/mmassaultbacksmall.bar");
                BrewRuntime.Session session=BrewRuntime.startSession(mod,progress,bar,pixelDecoder());
                liveSession=session;
                result=session.pumpTimers(6);
                handleAudioEvents(app,result);
                report=composeReport(app,result);
            }catch(Throwable t){
                liveSession=null;
                result=new BrewRuntime.Result();result.failure=t.getClass().getSimpleName()+": "+t.getMessage();
                java.io.StringWriter stack=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(stack));
                report="RUSH MARINE RUNTIME BASELINE v0.0.10\nHOST FAILURE\n"+stack;
            }
            publish(app,result,report,true);
            deliver(ctx,cb,result,report);
        });
    }

    /** Advance the original game's timer queue without rebuilding the guest. */
    static void pump(Context ctx,int ticks,Callback cb){
        Context app=ctx.getApplicationContext();
        EXEC.execute(()->{
            BrewRuntime.Session s=liveSession;
            BrewRuntime.Result r;
            if(s==null){r=new BrewRuntime.Result();r.failure="No persistent BREW session";}
            else r=hostAudioPaused?s.snapshot():s.pumpTimers(Math.max(0,ticks));
            handleAudioEvents(app,r);
            lastResult=r;
            // Avoid rebuilding/serializing a megabyte-scale trace every 50 ms. A
            // key event or explicit report capture checkpoints the full trace.
            deliver(ctx,cb,r,lastReport);
        });
    }

    /** Deliver a real BREW key-down event and leave the key held in guest time. */
    static void pressKey(Context ctx,int keyCode,Callback cb){
        Context app=ctx.getApplicationContext();
        EXEC.execute(()->{
            BrewRuntime.Session s=liveSession;
            BrewRuntime.Result r;
            String report;
            if(s==null){r=new BrewRuntime.Result();r.failure="No persistent BREW session";report=composeReport(app,r);}
            else {r=s.pressKey(keyCode);handleAudioEvents(app,r);report=composeReport(app,r);}
            publish(app,r,report,true);
            deliver(ctx,cb,r,report);
        });
    }

    /** Deliver the matching real BREW key-up event. */
    static void releaseKey(Context ctx,int keyCode,Callback cb){
        Context app=ctx.getApplicationContext();
        EXEC.execute(()->{
            BrewRuntime.Session s=liveSession;
            BrewRuntime.Result r;
            String report;
            if(s==null){r=new BrewRuntime.Result();r.failure="No persistent BREW session";report=composeReport(app,r);}
            else {r=s.releaseKey(keyCode);handleAudioEvents(app,r);report=composeReport(app,r);}
            publish(app,r,report,true);
            deliver(ctx,cb,r,report);
        });
    }

    /** Deliver one real BREW press/release pair to the original applet handler. */
    static void tapKey(Context ctx,String label,int timerTicksAfter,Callback cb){
        int code=BrewRuntime.keyCodeForLabel(label);
        tapKey(ctx,code,timerTicksAfter,cb);
    }

    static void tapKey(Context ctx,int keyCode,int timerTicksAfter,Callback cb){
        Context app=ctx.getApplicationContext();
        EXEC.execute(()->{
            BrewRuntime.Session s=liveSession;
            BrewRuntime.Result r;
            String report;
            if(s==null){
                r=new BrewRuntime.Result();r.failure="No persistent BREW session";
                report=composeReport(app,r);
            }else{
                r=s.tapKey(keyCode,Math.max(0,timerTicksAfter));
                handleAudioEvents(app,r);
                report=composeReport(app,r);
            }
            publish(app,r,report,true);
            deliver(ctx,cb,r,report);
        });
    }

    /** Explicitly snapshot the current live guest for Tools/Diagnostics. */
    static void capture(Context ctx,Callback cb){
        Context app=ctx.getApplicationContext();
        EXEC.execute(()->{
            BrewRuntime.Session s=liveSession;
            BrewRuntime.Result r=s==null?lastResult:s.snapshot();
            if(r==null){r=new BrewRuntime.Result();r.failure="No device run captured yet";}
            handleAudioEvents(app,r);
            String report=composeReport(app,r);
            publish(app,r,report,true);
            deliver(ctx,cb,r,report);
        });
    }

    static boolean isSessionLive(){BrewRuntime.Result r=lastResult;return liveSession!=null&&r!=null&&r.sessionLive&&r.liveFailure==null;}

    /** Latest completed serialized operation; no boot, timer step, audio event or storage initialization. */
    static String currentReport(Context c){
        BrewRuntime.Result r=lastResult;return r==null?loadLast(c):composeReport(c,r);
    }

    static String loadLast(Context c){
        if(lastReport!=null)return lastReport;
        try{File f=new File(c.getFilesDir(),"reports/last_report.txt");if(!f.exists())return "No device run captured yet.\n";try(InputStream in=new java.io.FileInputStream(f)){return new String(readStream(in),StandardCharsets.UTF_8);}}
        catch(Exception e){return "Could not read last report: "+e+"\n";}
    }

    private static BrewRuntime.PixelDecoder pixelDecoder(){
        return (png,width,height)->{
            Bitmap bm=BitmapFactory.decodeByteArray(png,0,png.length);
            if(bm==null)return null;
            int w=bm.getWidth(),h=bm.getHeight();int[] px=new int[w*h];bm.getPixels(px,0,w,0,0,w,h);return px;
        };
    }

    static String audioShortStatus(){
        String s=audioHostStatus;
        if(s==null||s.length()==0)return "IDLE";
        if(s.startsWith("PLAYING"))return "PLAY";
        if(s.startsWith("FAIL"))return "FAIL";
        return s.length()>12?s.substring(0,12):s;
    }

    static void setHostAudioPaused(boolean paused){
        hostAudioPaused=paused;
        EXEC.execute(()->{
            for(Map.Entry<Integer,MediaPlayer> entry:AUDIO_PLAYERS.entrySet()){
                MediaPlayer mp=entry.getValue();
                try{if(paused){if(mp.isPlaying())mp.pause();}else{if(!mp.isPlaying())mp.start();if(AUDIO_PENDING_STARTS.remove(entry.getKey()))audioHostStarts++;}}catch(Exception ignored){}
            }
            if(!AUDIO_PLAYERS.isEmpty())audioHostStatus=paused?"PAUSED":"PLAYING";
        });
    }

    static void stopHostAudio(){EXEC.execute(RuntimeRunner::resetHostAudioInternal);}

    private static void resetHostAudioInternal(){
        for(MediaPlayer mp:AUDIO_PLAYERS.values()){try{mp.stop();}catch(Exception ignored){}try{mp.release();}catch(Exception ignored){}}
        AUDIO_PLAYERS.clear();AUDIO_MIDI_OBJECTS.clear();AUDIO_PENDING_STARTS.clear();lastAudioSerial=0;audioHostStarts=0;audioHostStops=0;audioHostFailures=0;audioHostLastResource=0;audioHostStatus="IDLE";
    }

    private static void handleAudioEvents(Context app,BrewRuntime.Result r){
        if(r==null||r.audioEvents==null)return;
        for(BrewRuntime.AudioEvent ev:r.audioEvents){
            if(ev==null||ev.serial<=lastAudioSerial)continue;
            lastAudioSerial=Math.max(lastAudioSerial,ev.serial);
            if(ev.kind==BrewRuntime.AudioEvent.STOP){stopAudioObject(ev.mediaObject);continue;}
            if(ev.kind==BrewRuntime.AudioEvent.PLAY)playAudioObject(app,ev);
        }
    }

    private static void stopAudioObject(int mediaObject){
        MediaPlayer old=AUDIO_PLAYERS.remove(mediaObject);
        AUDIO_MIDI_OBJECTS.remove(mediaObject);
        AUDIO_PENDING_STARTS.remove(mediaObject);
        if(old!=null){try{old.stop();}catch(Exception ignored){}try{old.release();}catch(Exception ignored){}audioHostStops++;}
        audioHostStatus=AUDIO_PLAYERS.isEmpty()?"STOPPED":"PLAYING";
    }

    private static void stopOtherMidiPlayers(int keepObject){
        Integer[] active=AUDIO_MIDI_OBJECTS.toArray(new Integer[0]);
        for(int obj:active)if(obj!=keepObject)stopAudioObject(obj);
    }

    private static void playAudioObject(Context app,BrewRuntime.AudioEvent ev){
        if(ev.data==null||ev.data.length<4){audioHostFailures++;audioHostStatus=String.format(Locale.US,"FAIL 0x%04X NO DATA",ev.resourceId);return;}
        boolean midi=isMidi(ev.data);
        // The startup trace starts 0x232A (51.17 s) and then 0x2335 (35.02 s) on a second
        // media object without an explicit guest Stop. Android happily mixes them; the target
        // feature-phone MIDI path did not need concurrent sequencers. Preempt prior MIDI only.
        if(midi)stopOtherMidiPlayers(ev.mediaObject);
        stopAudioObject(ev.mediaObject);
        String ext=midi?"mid":isQcp(ev.data)?"qcp":"bin";
        File dir=new File(app.getCacheDir(),"rush_audio");dir.mkdirs();
        File f=new File(dir,String.format(Locale.US,"res_%04X.%s",ev.resourceId,ext));
        try{
            if(!f.exists()||f.length()!=ev.data.length){try(FileOutputStream o=new FileOutputStream(f)){o.write(ev.data);}}
            MediaPlayer mp=new MediaPlayer();
            AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(isMidi(ev.data)?AudioAttributes.CONTENT_TYPE_MUSIC:AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            mp.setAudioAttributes(aa);mp.setDataSource(f.getAbsolutePath());
            final int obj=ev.mediaObject,res=ev.resourceId;
            mp.setOnCompletionListener(done->EXEC.execute(()->{
                MediaPlayer cur=AUDIO_PLAYERS.get(obj);
                if(cur==done){AUDIO_PLAYERS.remove(obj);AUDIO_MIDI_OBJECTS.remove(obj);AUDIO_PENDING_STARTS.remove(obj);try{done.release();}catch(Exception ignored){}if(AUDIO_PLAYERS.isEmpty())audioHostStatus="DONE";}
            }));
            mp.setOnErrorListener((bad,what,extra)->{EXEC.execute(()->{
                MediaPlayer cur=AUDIO_PLAYERS.remove(obj);AUDIO_MIDI_OBJECTS.remove(obj);AUDIO_PENDING_STARTS.remove(obj);if(cur!=null)try{cur.release();}catch(Exception ignored){}
                audioHostFailures++;audioHostStatus=String.format(Locale.US,"FAIL 0x%04X MEDIA %d/%d",res,what,extra);
            });return true;});
            mp.prepare();if(!hostAudioPaused){mp.start();audioHostStarts++;}else AUDIO_PENDING_STARTS.add(obj);AUDIO_PLAYERS.put(obj,mp);if(midi)AUDIO_MIDI_OBJECTS.add(obj);audioHostLastResource=ev.resourceId;
            audioHostStatus=String.format(Locale.US,(hostAudioPaused?"PAUSED":"PLAYING")+" 0x%04X %s",ev.resourceId,ext.toUpperCase(Locale.US));
        }catch(Throwable t){
            MediaPlayer old=AUDIO_PLAYERS.remove(ev.mediaObject);AUDIO_MIDI_OBJECTS.remove(ev.mediaObject);AUDIO_PENDING_STARTS.remove(ev.mediaObject);if(old!=null)try{old.release();}catch(Exception ignored){}
            audioHostFailures++;audioHostLastResource=ev.resourceId;audioHostStatus=String.format(Locale.US,"FAIL 0x%04X %s",ev.resourceId,t.getClass().getSimpleName());
        }
    }

    private static boolean isMidi(byte[] d){return d.length>=4&&d[0]=='M'&&d[1]=='T'&&d[2]=='h'&&d[3]=='d';}
    private static boolean isQcp(byte[] d){return d.length>=12&&d[0]=='R'&&d[1]=='I'&&d[2]=='F'&&d[3]=='F'&&d[8]=='Q'&&d[9]=='L'&&d[10]=='C'&&d[11]=='M';}

    private static String composeReport(Context app,BrewRuntime.Result result){
        StringBuilder b=new StringBuilder();
        b.append("RUSH MARINE BRIDGE v0.1.2\n");
        b.append("DEVICE RUN REPORT\n");
        b.append("Captured: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date())).append('\n');
        b.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        b.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("ABIs: ").append(String.join(", ",Build.SUPPORTED_ABIS)).append('\n');
        b.append("Package: ").append(app.getPackageName()).append("\n");
        b.append("Persistent session: ").append(result.sessionLive?"LIVE":"NOT LIVE").append('\n');
        b.append("Live key events: ").append(result.keyEvents).append(" (press ").append(result.keyPresses).append(" / release ").append(result.keyReleases).append(")\n");
        if(result.lastKeyCode!=0)b.append(String.format(Locale.US,"Last key: 0x%04X pressRc=%d releaseRc=%d\n",result.lastKeyCode&0xFFFF,result.lastKeyPressReturn,result.lastKeyReleaseReturn));
        b.append("Guest steps: ").append(result.steps).append('\n');
        b.append("Timer callbacks: ").append(result.timerCallbacksExecuted).append(" / display updates: ").append(result.displayUpdates).append('\n');
        b.append("DrawRect / BitBlt: ").append(result.drawRectCalls).append(" / ").append(result.bitBltCalls).append('\n');
        b.append("BAR data/images: ").append(result.resourceLoads).append(" / ").append(result.imageLoads).append('\n');
        b.append("PNG pixel decodes: ").append(result.imagePixelDecodes).append(" success / ").append(result.imagePixelDecodeFailures).append(" failed\n");
        b.append("Guest IMedia: objects ").append(result.mediaCreates).append(" / data ").append(result.mediaSetDataCalls).append(" / play ").append(result.mediaPlayRequests).append(" / stop ").append(result.mediaStopRequests).append('\n');
        if(result.lastAudioResourceId!=0)b.append(String.format(Locale.US,"Last guest audio resource: 0x%04X / serial %d\n",result.lastAudioResourceId,result.lastAudioSerial));
        b.append("Android audio host: ").append(audioHostStatus).append(" (starts ").append(audioHostStarts).append(" / stops ").append(audioHostStops).append(" / failures ").append(audioHostFailures).append(")\n");
        b.append("Unhandled iface calls: ").append(result.unknownInterfaceCalls);
        if(result.unknownInterfaceCalls>0)b.append(String.format(Locale.US," (last cls=%08X off=0x%X)",result.lastUnknownInterfaceClass,result.lastUnknownInterfaceOffset));
        b.append('\n');
        b.append("Framebuffer: ").append(result.pixelBackedDisplay?"PASS ":"NO ").append(result.framebufferWidth).append('x').append(result.framebufferHeight).append(" nonblack=").append(result.framebufferNonBlackPixels).append('\n');
        b.append('\n');
        if(result.report!=null)b.append(result.report);
        if(result.liveFailure!=null)b.append("\nLIVE FAILURE FRONTIER: ").append(result.liveFailure).append('\n');
        else if(result.failure!=null)b.append("\nFAILURE: ").append(result.failure).append('\n');
        else b.append("\nCURRENT FRONTIER: cardinal controls are physically confirmed; v0.0.10 fixes bespoke diagonals by using the raw-keypad-proven 1/3/7/9 keys and prevents overlapping long MIDI sequencers at startup.\n");
        return b.toString();
    }

    private static void publish(Context app,BrewRuntime.Result r,String report,boolean save){
        lastResult=r;lastReport=report;if(save)saveInternal(app,report);
    }

    private static void deliver(Context ctx,Callback cb,BrewRuntime.Result r,String report){
        if(cb==null)return;
        if(ctx instanceof android.app.Activity)((android.app.Activity)ctx).runOnUiThread(()->cb.done(r,report));
        else cb.done(r,report);
    }

    private static void saveInternal(Context c,String report){
        try{File d=new File(c.getFilesDir(),"reports");d.mkdirs();try(FileOutputStream o=new FileOutputStream(new File(d,"last_report.txt"))){o.write(report.getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}
    }
    static byte[] readAll(Context c,String asset)throws Exception{try(InputStream in=c.getAssets().open(asset)){return readStream(in);}}
    private static byte[] readStream(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[16384];for(int n;(n=in.read(x))>=0;)b.write(x,0,n);return b.toByteArray();}
    private RuntimeRunner(){}
}
