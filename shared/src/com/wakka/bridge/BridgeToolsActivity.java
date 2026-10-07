package com.wakka.bridge;

import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.view.Gravity;
import android.widget.ScrollView;

/** Diagnostics survive UI polish and remain reachable without booting a game. */
public abstract class BridgeToolsActivity extends BridgeReportActivity {
    private TextView report;
    private String fullReport="";
    /** Optional report-first presentation, reusable by BREW and other profiles. */
    protected boolean compactReportLayout() { return false; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); GameSpec g=backend().spec(); FrameLayout safe=BridgeUi.safeRoot(this);
        if(compactReportLayout()) { buildCompactReport(safe,g); return; }
        LinearLayout root=BridgeUi.scrollColumn(this,safe);
        Button back=BridgeUi.button(this,"‹ RETURN TO BRIDGE",false,g.accent); back.setOnClickListener(v->finish());
        root.addView(back,BridgeUi.lp(this,48,14));
        root.addView(BridgeUi.label(this,"BRIDGEKEEPER TOOLS",26,BridgeUi.INK,true),BridgeUi.lp(this,-2,8));
        root.addView(BridgeUi.label(this,g.title,15,BridgeUi.MUTED,false),BridgeUi.lp(this,-2,12));
        root.addView(BridgeUi.section(this,"SESSION REPORT"));
        root.addView(BridgeUi.label(this,"Capture the current session or export the last saved report. Reports include runtime details, cumulative counters, input context, graphics status, errors and the retained log.",13,BridgeUi.MUTED,false),BridgeUi.lp(this,-2,12));
        addButton(root,"Save report to Downloads",()->exportReport(0));
        addButton(root,"Copy report",()->copyReport());
        addButton(root,"Save report as…",()->exportReport(1));
        addButton(root,"Share report…",()->exportReport(2));
        addButton(root,"Refresh diagnostics",()->refreshReport());
        backend().addToolSections(this,root);
        root.addView(BridgeUi.section(this,"CONTROLS"));
        root.addView(BridgeUi.label(this,g.controlsHelp,13,BridgeUi.INK,false));
        root.addView(BridgeUi.section(this,"CURRENT BASELINE / KNOWN ISSUES"));
        root.addView(BridgeUi.label(this,g.baseline+"\n"+g.knownIssues,13,BridgeUi.MUTED,false));
        root.addView(BridgeUi.section(this,"DIAGNOSTICS"));
        report=BridgeUi.label(this,"",11,0xffbfe5e4,false); report.setTypeface(android.graphics.Typeface.MONOSPACE);
        report.setTextIsSelectable(true); root.addView(report); setContentView(safe); refreshReport();
    }
    private void buildCompactReport(FrameLayout safe,GameSpec g) {
        LinearLayout root=BridgeUi.column(this); root.setPadding(BridgeUi.dp(this,12),BridgeUi.dp(this,12),BridgeUi.dp(this,12),BridgeUi.dp(this,12));
        safe.addView(root,new FrameLayout.LayoutParams(-1,-1));
        root.addView(BridgeUi.label(this,"TOOLS / DIAGNOSTICS",18,g.accent,true),BridgeUi.lp(this,32,2));
        root.addView(BridgeUi.label(this,g.title+" · current session report",12,BridgeUi.MUTED,false),BridgeUi.lp(this,24,6));
        LinearLayout actions=BridgeUi.row(this);
        addCompactAction(actions,"COPY REPORT",()->copyReport(),g.accent);
        addCompactAction(actions,"SAVE TO DOWNLOADS",()->exportReport(0),g.accent);
        addCompactAction(actions,"REFRESH",()->refreshReport(),g.accent);
        root.addView(actions,BridgeUi.lp(this,50,6));
        LinearLayout exports=BridgeUi.row(this);
        addCompactAction(exports,"SHARE",()->exportReport(2),g.accent);
        addCompactAction(exports,"SAVE AS…",()->exportReport(1),g.accent);
        addCompactAction(exports,"RETURN",()->finish(),g.accent);
        root.addView(exports,BridgeUi.lp(this,44,4));
        backend().addToolSections(this,root);
        ScrollView scroll=new ScrollView(this); scroll.setBackground(BridgeUi.round(this,BridgeUi.PANEL,g.accent,8));
        report=BridgeUi.label(this,"",10,BridgeUi.INK,false);report.setTypeface(android.graphics.Typeface.MONOSPACE);report.setTextIsSelectable(true);
        report.setPadding(BridgeUi.dp(this,10),BridgeUi.dp(this,10),BridgeUi.dp(this,10),BridgeUi.dp(this,10));
        scroll.addView(report,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(safe);refreshReport();
    }
    private void addCompactAction(LinearLayout row,String label,Runnable action,int accent) {
        Button b=BridgeUi.button(this,label,false,accent);b.setTextSize(10);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setBackground(BridgeUi.round(this,BridgeUi.PANEL,accent,8));b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(BridgeUi.dp(this,2),0,BridgeUi.dp(this,2),0);row.addView(b,p);
    }
    private void copyReport() {
        refreshReport();
        try {
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Runtime report",fullReport));
            android.widget.Toast.makeText(this,"Report copied",android.widget.Toast.LENGTH_SHORT).show();
        } catch(RuntimeException e) {
            android.util.Log.e("WakkanBridge","Full report clipboard copy failed",e);
            android.widget.Toast.makeText(this,"Could not copy the full report. Use Save or Share.",android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private void addButton(LinearLayout root,String label,Runnable action) {
        Button b=BridgeUi.button(this,label,false,BridgeUi.CYAN); b.setOnClickListener(v->action.run());
        root.addView(b,BridgeUi.lp(this,52,8));
    }
    protected final void refreshReport() {
        String text=backend().started()||!new java.io.File(new java.io.File(getFilesDir(),"reports"),"latest-report.txt").isFile()
                ?ReportStore.compose(this,backend()):ReportStore.latest(this);
        fullReport=text;
        if(text.length()>100000) text=text.substring(0,5000)+"\n[Preview shortened. Saved/shared report retains the full captured log.]\n"+text.substring(text.length()-95000);
        report.setText(text);
    }
    @Override protected void reportSaved(String text) { super.reportSaved(text); refreshReport(); }
}
