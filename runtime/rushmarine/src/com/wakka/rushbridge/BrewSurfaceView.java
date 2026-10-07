package com.wakka.rushbridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/** Nearest-neighbour presentation surface for the original 128x160 BREW framebuffer. */
final class BrewSurfaceView extends View {
    private final Paint paint=new Paint();
    private Bitmap frame;
    BrewSurfaceView(Context c){super(c);paint.setFilterBitmap(false);setBackgroundColor(Color.BLACK);}
    void setFrame(int[] argb,int width,int height){
        if(argb==null||width<=0||height<=0||argb.length<width*height)return;
        frame=Bitmap.createBitmap(argb,width,height,Bitmap.Config.ARGB_8888);
        invalidate();
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(frame==null)return;
        int vw=getWidth(),vh=getHeight();float scale=Math.min(vw/(float)frame.getWidth(),vh/(float)frame.getHeight());
        int dw=Math.max(1,Math.round(frame.getWidth()*scale)),dh=Math.max(1,Math.round(frame.getHeight()*scale));
        int left=(vw-dw)/2,top=(vh-dh)/2;
        c.drawBitmap(frame,null,new Rect(left,top,left+dw,top+dh),paint);
    }
}
