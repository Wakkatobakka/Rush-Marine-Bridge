package com.wakka.rushbridge;
import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.wakka.bridge.BridgeUi;
/** Read-only game-data details; does not present staged files as functional saves. */
public final class VaultActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);FrameLayout safe=BridgeUi.safeRoot(this);LinearLayout root=BridgeUi.scrollColumn(this,safe);
        root.addView(BridgeUi.label(this,"GAME DATA",22,BridgeUi.CYAN,true),BridgeUi.lp(this,-2,14));
        String problem=BrewPayload.store(getFilesDir()).problem();
        root.addView(BridgeUi.label(this,problem==null?"All five files verified":"No verified import\n"+problem,15,BridgeUi.INK,true),BridgeUi.lp(this,-2,14));
        root.addView(BridgeUi.label(this,"Compatible release: English BREW 1.1.11\nHandset profile: CDM2030 · 128×160\n\nImport your original game ZIP from the bridge home screen. Files are kept in app-private storage and verified before the guest starts. No original game files are included in the APK.",13,BridgeUi.MUTED,false),BridgeUi.lp(this,-2,18));
        for(VerifiedZipStore.RequiredFile file:BrewPayload.FILES)root.addView(BridgeUi.label(this,file.path+" · "+file.bytes+" bytes",12,BridgeUi.INK,false),BridgeUi.lp(this,-2,8));
        root.addView(BridgeUi.label(this,"SAVE STATUS\nThe runtime reads the supplied progress baseline. Saving progress between app restarts is not implemented. Returning from the bridge menu or reports resumes the same live session while the app process remains alive.",13,BridgeUi.MUTED,false),BridgeUi.lp(this,-2,18));
        android.widget.Button back=BridgeUi.button(this,"BACK",false,Ui.GREEN);back.setOnClickListener(v->finish());root.addView(back,BridgeUi.lp(this,50,0));setContentView(safe);
    }
}
