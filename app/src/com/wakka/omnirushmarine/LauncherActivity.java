package com.wakka.omnirushmarine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import com.wakka.bridge.BridgeUi;
import com.wakka.bridge.GameSpec;
import com.wakka.rushbridge.BrewBackend;
import com.wakka.rushbridge.BrewPayload;
import com.wakka.rushbridge.VerifiedZipStore;
import java.io.InputStream;

/** Dirge-style standalone front door with on-phone, local-only BREW ZIP import. */
public final class LauncherActivity extends Activity {
    private static final int PICK_ZIP=700;
    private Button play,importData;
    private TextView state;
    private boolean importing;
    private String importFailure;
    private BrewBackend backend(){return RushCatalog.backend(this);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);buildHome();}
    @Override protected void onResume(){super.onResume();refresh();}
    private void buildHome() {
        GameSpec spec=backend().spec();FrameLayout safe=BridgeUi.safeRoot(this);
        LinearLayout root=BridgeUi.scrollColumn(this,safe),header=BridgeUi.row(this);
        header.addView(BridgeUi.label(this,"◆  WAKKAN // BRIDGEKEEPER",11,BridgeUi.CYAN,true),new LinearLayout.LayoutParams(0,-2,1));
        header.addView(BridgeUi.label(this,"v"+BrewPayload.VERSION,11,BridgeUi.MUTED,true));root.addView(header,BridgeUi.lp(this,-2,14));
        FrameLayout hero=new FrameLayout(this);hero.setBackground(BridgeUi.round(this,BridgeUi.PANEL,spec.accent,22));
        try(InputStream in=getAssets().open("art/rush_logo.png")) {
            ImageView image=new ImageView(this);image.setImageBitmap(BitmapFactory.decodeStream(in));image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(BridgeUi.dp(this,12),BridgeUi.dp(this,12),BridgeUi.dp(this,12),BridgeUi.dp(this,12));hero.addView(image,new FrameLayout.LayoutParams(-1,-1));
        }catch(Exception ignored){TextView title=BridgeUi.label(this,"RUSH MARINE\nBRIDGE",30,BridgeUi.INK,true);title.setGravity(Gravity.CENTER);hero.addView(title);}
        root.addView(hero,BridgeUi.lp(this,190,12));
        TextView sub=BridgeUi.label(this,"Mega Man: Rush Marine · original BREW game",13,BridgeUi.MUTED,false);sub.setGravity(Gravity.CENTER);root.addView(sub,BridgeUi.lp(this,-2,12));
        LinearLayout card=BridgeUi.card(this);card.addView(BridgeUi.label(this,"GAME DATA",10,BridgeUi.CYAN,true));
        state=BridgeUi.label(this,"Checking…",14,BridgeUi.INK,true);state.setPadding(0,BridgeUi.dp(this,6),0,BridgeUi.dp(this,4));card.addView(state);
        card.addView(BridgeUi.label(this,"Provide your own compatible English BREW 1.1.11 ZIP. Import happens on your phone. Offline play after import.",12,BridgeUi.MUTED,false));
        root.addView(card,BridgeUi.lp(this,-2,12));
        importData=BridgeUi.button(this,"IMPORT RUSH MARINE ZIP",false,spec.accent);
        importData.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");
            try{startActivityForResult(pick,PICK_ZIP);}catch(android.content.ActivityNotFoundException e){showMessage("No file picker available","Open this app on a device with a document picker installed.");}});
        root.addView(importData,BridgeUi.lp(this,54,10));
        play=BridgeUi.button(this,"ENTER RUSH MARINE",true,spec.accent);
        play.setOnClickListener(v->{if(backend().ready())startActivity(new Intent(this,GameActivity.class));else refresh();});root.addView(play,BridgeUi.lp(this,68,12));
        LinearLayout actions=BridgeUi.row(this);
        Button controls=BridgeUi.button(this,"CONTROLS",false,spec.accent);
        controls.setOnClickListener(v->showMessage("Rush Marine controls",spec.controlsHelp));actions.addView(controls,new LinearLayout.LayoutParams(0,BridgeUi.dp(this,54),1));
        Button tools=BridgeUi.button(this,"TOOLS / REPORT",false,spec.accent);tools.setOnClickListener(v->startActivity(new Intent(this,ToolsActivity.class)));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,BridgeUi.dp(this,54),1);tp.leftMargin=BridgeUi.dp(this,8);actions.addView(tools,tp);root.addView(actions,BridgeUi.lp(this,-2,12));
        Button about=BridgeUi.button(this,"ABOUT / TEST STATUS",false,spec.accent);
        about.setOnClickListener(v->showMessage("Rush Marine Bridge v"+BrewPayload.VERSION,
            "Independent Android bridge by Wakkatobakka. Original game by Capcom; BREW is Qualcomm's platform.\n\n"
            +"Runs the original ARM/Thumb module through a title-specific compatibility runtime. Original game files are supplied by you.\n\n"
            +spec.knownIssues+"\n\nThis app declares no internet permission. Import and report export do not send data automatically."));
        root.addView(about,BridgeUi.lp(this,48,16));
        TextView footer=BridgeUi.label(this,"RUSH MARINE BRIDGE · v"+BrewPayload.VERSION+"\nFirst BREW release · pre-release",11,BridgeUi.MUTED,false);footer.setGravity(Gravity.CENTER);root.addView(footer);
        setContentView(safe);refresh();
    }
    private void refresh() {
        if(play==null)return;boolean ready=backend().ready();
        importData.setEnabled(!importing);play.setEnabled(ready&&!importing);
        play.setText(backend().started()?"RESUME RUSH MARINE":"ENTER RUSH MARINE");
        state.setText(importing?"IMPORTING · checking the selected ZIP…":(importFailure!=null?importFailure+"\n"+backend().readiness():backend().readiness()));
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_ZIP||result!=RESULT_OK||data==null||data.getData()==null)return;
        final android.net.Uri uri=data.getData();importing=true;importFailure=null;refresh();
        final android.content.Context app=getApplicationContext();
        new Thread(()->{
            String failure=null;
            try(InputStream in=app.getContentResolver().openInputStream(uri)) {
                if(in==null)throw new java.io.IOException("Could not open the selected file");
                VerifiedZipStore store=BrewPayload.store(app.getFilesDir());store.importZip(in);store.verify();
            }catch(Exception e){failure=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            final String error=failure;
            runOnUiThread(()->{importing=false;importFailure=error==null?null:"IMPORT FAILED · "+error;
                if(!isFinishing()&&!isDestroyed()){refresh();if(error!=null)showMessage("Import failed",error+"\n\nAny previously verified import is kept.");
                else Toast.makeText(this,"Rush Marine data verified. Ready to play.",Toast.LENGTH_LONG).show();}});
        },"RushMarineImport").start();
    }
    private void showMessage(String title,String body){new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("Close",null).show();}
}
