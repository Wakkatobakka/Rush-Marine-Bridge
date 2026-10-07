package com.wakka.rushbridge;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(4,7,11);
    static final int PANEL = Color.rgb(9,15,22);
    static final int PANEL2 = Color.rgb(12,21,29);
    static final int CYAN = Color.rgb(0,218,255);
    static final int RED = Color.rgb(255,49,74);
    static final int TEXT = Color.rgb(235,246,250);
    static final int MUTED = Color.rgb(133,157,168);
    static final int GREEN = Color.rgb(130,190,92);

    static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}

    static TextView text(Context c,String s,float sp,int color,boolean bold){
        TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);
        if(bold)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        return t;
    }
    static GradientDrawable box(int fill,int stroke,float radius,Context c){
        GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(c,radius));
        if(stroke!=0)g.setStroke(dp(c,1),stroke);return g;
    }
    static Button button(Context c,String label,int stroke,int fill){
        Button b=new Button(c);b.setText(label);b.setTextColor(TEXT);b.setTextSize(13);b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c,8),0,dp(c,8),0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setBackground(box(fill,stroke,10,c));return b;
    }
    static void addSpace(LinearLayout l,int dp){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(1,Ui.dp(l.getContext(),dp)));}
    static LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    static LinearLayout.LayoutParams weight(float weight,int h){return new LinearLayout.LayoutParams(0,h,weight);}
    static void margins(View v,int l,int t,int r,int b){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)v.getLayoutParams();p.setMargins(dp(v.getContext(),l),dp(v.getContext(),t),dp(v.getContext(),r),dp(v.getContext(),b));v.setLayoutParams(p);}

    /** Android 15/16 enforce edge-to-edge for modern targets. Every bridge surface
     * consumes the real system-bar/cutout insets instead of relying on guessed top padding. */
    static void applySystemInsets(View v){
        final int baseL=v.getPaddingLeft(),baseT=v.getPaddingTop(),baseR=v.getPaddingRight(),baseB=v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener((view,insets)->{
            int l=insets.getSystemWindowInsetLeft(),t=insets.getSystemWindowInsetTop();
            int r=insets.getSystemWindowInsetRight(),b=insets.getSystemWindowInsetBottom();
            view.setPadding(baseL+l,baseT+t,baseR+r,baseB+b);
            return insets;
        });
        v.requestApplyInsets();
    }
    private Ui(){}
}
