package com.wakka.rushbridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.wakka.bridge.BridgeBackend;
import com.wakka.bridge.BridgeUi;
import com.wakka.bridge.GameSpec;
import com.wakka.bridge.KeypadView;
import com.wakka.bridge.SessionGate;
import com.wakka.rushbridge.runtime.BrewRuntime;
import java.util.Locale;

/** One persistent Rush Marine BREW guest, with the original serialized runner and audio host. */
public final class BrewBackend implements BridgeBackend {
    private final Context app;
    private final SessionGate gate=new SessionGate();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final BrewInputRouter input;
    private BrewSurfaceView display;
    private TextView overlay;
    private RushControls custom;
    private KeypadView keypad;
    private boolean bootRequested,pumpInFlight;
    private int boots;
    private long completedPumps;
    private final Runnable pump=()->pump();
    public BrewBackend(Context context) {
        app=context.getApplicationContext();
        input=new BrewInputRouter(new BrewInputRouter.Sink() {
            public void press(int code){RuntimeRunner.pressKey(app,code,(r,report)->handler.post(()->accept(r)));}
            public void release(int code){RuntimeRunner.releaseKey(app,code,(r,report)->handler.post(()->accept(r)));}
            public void tap(int code){RuntimeRunner.tapKey(app,code,1,(r,report)->handler.post(()->accept(r)));}
        });
    }
    @Override public GameSpec spec() {
        return new GameSpec("rushmarine","Mega Man: Rush Marine","BREW 1.1.11 · original ARM guest · offline",
            "Rush Marine Bridge 0.0.10","BREW / ARMv5 / Thumb","ENTER RUSH MARINE","art/rush_logo.png",null,
            "Eight-way movement uses measured guest keys; diagonals are dedicated 1/3/7/9. FIRE / OK holds Fire. AUTO / 0 toggles auto-fire on explicit tap. BACK opens the guest menu. Keypad preserves native numeric keys; unverified soft keys, * and # remain inert. Report opens the readable live report. Android Back returns to the bridge. Controls are non-inverted.",
            "Public import build awaits phone testing. Full game completion is unverified. Original 128×160 framebuffer is scaled without cropping. BREW/HLE coverage is Rush Marine-specific. QCP audio is device dependent. Progress saving between app restarts is not implemented; a fresh session starts from the supplied baseline.",Ui.GREEN);
    }
    @Override public boolean ready(){return BrewPayload.store(app.getFilesDir()).problem()==null;}
    @Override public String readiness(){String problem=BrewPayload.store(app.getFilesDir()).problem();return problem==null?"VERIFIED · READY TO PLAY":"IMPORT GAME DATA TO PLAY\n"+problem;}
    @Override public View attachDisplay(Activity activity) {
        FrameLayout stage=new FrameLayout(activity);stage.setPadding(Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8));
        stage.setBackgroundColor(Ui.BG);FrameLayout bezel=new FrameLayout(activity);bezel.setPadding(Ui.dp(activity,2),Ui.dp(activity,2),Ui.dp(activity,2),Ui.dp(activity,2));bezel.setBackground(Ui.box(android.graphics.Color.BLACK,Ui.GREEN,5,activity));
        stage.addView(bezel,new FrameLayout.LayoutParams(-1,-1));display=new BrewSurfaceView(activity);bezel.addView(display,new FrameLayout.LayoutParams(-1,-1));
        overlay=Ui.text(activity,"ORIGINAL BREW SESSION\nStarting…",13,Ui.TEXT,true);overlay.setGravity(android.view.Gravity.CENTER);overlay.setBackgroundColor(0xa0000000);bezel.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        accept(RuntimeRunner.lastResult);return stage;
    }
    @Override public View createControls(Activity activity,boolean raw) {
        custom=null;keypad=null;
        if(!raw){custom=new RushControls(activity,this);return custom;}
        String[] labels={"L soft","▲","R soft","BACK","◀","OK","▶","▼","1","2","3","4","5","6","7","8","9","*","0","#"};
        int[] masks=new int[20];for(int i=0;i<labels.length;i++)masks[i]=BrewInputRouter.maskFor(labels[i].equals("BACK")?BrewRuntime.AVK_CLR:BrewRuntime.keyCodeForLabel(labels[i]));
        keypad=new KeypadView(activity,labels,masks,new KeypadView.Sink(){public void set(int owner,int mask){setContact(owner,mask);}public void remove(int owner){removeContact(owner);}},Ui.GREEN);
        return keypad;
    }
    public void setContact(int owner,int mask){if(running())input.setMask(owner,mask);}
    public void removeContact(int owner){input.remove(owner);}
    public void tap(int code){if(running())input.tap(code);}
    @Override public void detachViews(){display=null;overlay=null;custom=null;keypad=null;}
    @Override public void start() {
        if(bootRequested){accept(RuntimeRunner.lastResult);updateGate();return;}
        bootRequested=true;boots++;RuntimeRunner.run(app,(r,report)->handler.post(()->{accept(r);updateGate();}));
    }
    @Override public boolean started(){return bootRequested;}
    @Override public boolean running(){return bootRequested&&!gate.blocked()&&RuntimeRunner.isSessionLive();}
    @Override public void setForeground(boolean value){gate.foreground(value);updateGate();}
    @Override public void setUserPaused(boolean value){gate.userPaused(value);updateGate();}
    @Override public boolean userPaused(){return gate.isUserPaused();}
    private void updateGate(){
        handler.removeCallbacks(pump);RuntimeRunner.setHostAudioPaused(!running());
        if(running()&&!pumpInFlight)handler.post(pump);
    }
    private void pump(){
        if(!running()||pumpInFlight)return;pumpInFlight=true;
        RuntimeRunner.pump(app,5,(r,report)->handler.post(()->{pumpInFlight=false;completedPumps++;accept(r);if(running())handler.postDelayed(pump,50);}));
    }
    private void accept(BrewRuntime.Result result){
        if(result==null)return;
        if(display!=null&&result.pixelBackedDisplay&&result.framebufferArgb!=null){display.setFrame(result.framebufferArgb,result.framebufferWidth,result.framebufferHeight);if(overlay!=null)overlay.setVisibility(View.GONE);}
        if(overlay!=null&&(result.liveFailure!=null||result.failure!=null)){overlay.setVisibility(View.VISIBLE);overlay.setText("RUNTIME STOPPED\nOpen Report for diagnostics");}
    }
    @Override public void releaseInput(){if(custom!=null)custom.cancelAll();if(keypad!=null)keypad.cancelAll();input.clear();}
    @Override public boolean hardwareKey(int key,boolean down){
        int code;
        switch(key){
            case KeyEvent.KEYCODE_DPAD_UP:code=BrewRuntime.AVK_UP;break;case KeyEvent.KEYCODE_DPAD_DOWN:code=BrewRuntime.AVK_DOWN;break;
            case KeyEvent.KEYCODE_DPAD_LEFT:code=BrewRuntime.AVK_LEFT;break;case KeyEvent.KEYCODE_DPAD_RIGHT:code=BrewRuntime.AVK_RIGHT;break;
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_BUTTON_A:code=BrewRuntime.AVK_SELECT;break;
            case KeyEvent.KEYCODE_BUTTON_START:case KeyEvent.KEYCODE_ESCAPE:code=BrewRuntime.AVK_CLR;break;
            default:if(key>=KeyEvent.KEYCODE_0&&key<=KeyEvent.KEYCODE_9)code=BrewRuntime.AVK_0+key-KeyEvent.KEYCODE_0;else return false;
        }
        if(down){if(running())input.setKey(-key-1,code);}else input.remove(-key-1);return true;
    }
    @Override public String status(){
        BrewRuntime.Result r=RuntimeRunner.lastResult;
        if(r==null)return bootRequested?"BREW starting…":"BREW ready";
        if(r.liveFailure!=null||r.failure!=null)return "BREW stopped · report available";
        return (gate.blocked()?"PAUSED":"LIVE")+" · UPD "+r.displayUpdates+" · KEYS "+r.keyEvents+" · AUDIO "+RuntimeRunner.audioShortStatus();
    }
    /** Wait behind queued key/timer operations before refreshing the readable report. */
    public void captureReport(Runnable done){
        if(!bootRequested){handler.post(done);return;}
        RuntimeRunner.capture(app,(r,rep)->handler.post(done));
    }
    @Override public String snapshot(){
        return "RUSH MARINE BRIDGE SESSION\nApp version: 0.1.2\nRuntime baseline: 0.0.10\n"
            +"Foreground: "+gate.isForeground()+" / explicit pause: "+userPaused()+"\n"
            +"Session boots this process: "+boots+" / completed pump batches: "+completedPumps+"\n"
            +String.format(Locale.US,"Native held mask: 0x%08X / owners: %d\n",input.mask(),input.contacts())
            +"Snapshot boundary: last completed serialized guest operation; queued events may complete afterward.\n"
            +"Native progress: imported seed read; saving between restarts is not implemented.\nVerified game data: "+ready()+"\n\n"
            +RuntimeRunner.currentReport(app);
    }
    @Override public void addToolSections(Activity activity,LinearLayout root){
        LinearLayout row=BridgeUi.row(activity);
        Button fresh=BridgeUi.button(activity,"REBOOT RUNTIME (FRESH)",false,Ui.RED);fresh.setTextSize(10);
        fresh.setEnabled(ready());fresh.setOnClickListener(v->{releaseInput();handler.removeCallbacks(pump);RuntimeRunner.setHostAudioPaused(true);boots++;
            RuntimeRunner.run(app,(r,rep)->handler.post(()->{bootRequested=true;accept(r);updateGate();if(!activity.isFinishing())activity.recreate();}));});
        row.addView(fresh,new LinearLayout.LayoutParams(0,BridgeUi.dp(activity,44),1));
        Button vault=BridgeUi.button(activity,"GAME DATA",false,Ui.GREEN);vault.setTextSize(10);
        vault.setOnClickListener(v->activity.startActivity(new Intent(activity,VaultActivity.class)));row.addView(vault,new LinearLayout.LayoutParams(0,BridgeUi.dp(activity,44),1));
        root.addView(row,BridgeUi.lp(activity,44,8));
    }
}
