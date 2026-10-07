package com.wakka.rushbridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import com.wakka.rushbridge.runtime.BrewRuntime;
import java.util.HashMap;
import java.util.Map;

/** Bounded green control deck; direction semantics match the 0.0.10 guest contract. */
final class RushControls extends View {
    private final BrewBackend backend;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pad=new RectF();
    private final RectF[] actions={new RectF(),new RectF(),new RectF()};
    private final Map<Integer,Integer> contacts=new HashMap<>();
    private int padPointer=-1,sector=-1;
    private float tx,ty;
    RushControls(Context c,BrewBackend backend) { super(c);this.backend=backend;setContentDescription("Rush Marine eight-way movement, Fire, Auto and Back"); }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        float gap=Ui.dp(getContext(),8),top=Ui.dp(getContext(),24),bottom=Ui.dp(getContext(),8);
        float size=Math.min(h-top-bottom,(w-gap*3)*.54f);
        pad.set(gap,top,gap+size,top+size);
        float x=pad.right+gap,ah=(size-gap*2)/3f;
        for(int i=0;i<3;i++)actions[i].set(x,top+i*(ah+gap),w-gap,top+i*(ah+gap)+ah);
    }
    private void text(Canvas c,String text,float x,float y,float sp,int color) {
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(text,x,y-(paint.ascent()+paint.descent())/2,paint);
    }
    private void box(Canvas c,RectF r,boolean active) {
        paint.setStyle(Paint.Style.FILL);paint.setColor(active?0xff263d22:Ui.PANEL);c.drawRoundRect(r,Ui.dp(getContext(),12),Ui.dp(getContext(),12),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),1));paint.setColor(Ui.GREEN);c.drawRoundRect(r,Ui.dp(getContext(),12),Ui.dp(getContext(),12),paint);
    }
    @Override protected void onDraw(Canvas c) {
        c.drawColor(Ui.BG);text(c,"RUSH MARINE // PLAY CONTROLS",getWidth()/2f,Ui.dp(getContext(),12),10,Ui.GREEN);
        box(c,pad,sector>=0);float cx=pad.centerX(),cy=pad.centerY(),dx=pad.width()*.3f,dy=pad.height()*.3f;
        paint.setStyle(Paint.Style.STROKE);paint.setColor(0x667fb85c);c.drawLine(cx,pad.top+10,cx,pad.bottom-10,paint);c.drawLine(pad.left+10,cy,pad.right-10,cy,paint);
        text(c,"↑",cx,cy-dy,24,Ui.TEXT);text(c,"↓",cx,cy+dy,24,Ui.TEXT);text(c,"←",cx-dx,cy,24,Ui.TEXT);text(c,"→",cx+dx,cy,24,Ui.TEXT);
        text(c,"↖",cx-dx,cy-dy,16,Ui.MUTED);text(c,"↗",cx+dx,cy-dy,16,Ui.MUTED);text(c,"↙",cx-dx,cy+dy,16,Ui.MUTED);text(c,"↘",cx+dx,cy+dy,16,Ui.MUTED);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.GREEN);c.drawCircle(cx,cy,Ui.dp(getContext(),5),paint);
        if(sector>=0){paint.setColor(Ui.CYAN);c.drawCircle(tx,ty,Ui.dp(getContext(),8),paint);}
        String[] labels={"FIRE / OK","AUTO / 0","BACK"};
        for(int i=0;i<3;i++){box(c,actions[i],contacts.containsValue(i+1));text(c,labels[i],actions[i].centerX(),actions[i].centerY(),13,Ui.TEXT);}
    }
    private int hit(float x,float y) { if(pad.contains(x,y))return 0;for(int i=0;i<3;i++)if(actions[i].contains(x,y))return i+1;return -1; }
    private void movePad(int id,float x,float y) {
        float dx=x-pad.centerX(),dy=y-pad.centerY(),dead=Math.min(pad.width(),pad.height())*.13f;
        if(dx*dx+dy*dy<dead*dead){sector=-1;backend.removeContact(id+1000);}
        else {int s=(int)Math.round(Math.atan2(dy,dx)/(Math.PI/4));if(s<0)s+=8;if(s>=8)s-=8;sector=s;backend.setContact(id+1000,BrewInputRouter.maskFor(BrewInputRouter.sectorKey(s)));}
        tx=Math.max(pad.left,Math.min(pad.right,x));ty=Math.max(pad.top,Math.min(pad.bottom,y));
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            int area=hit(event.getX(index),event.getY(index));
            if(area==0&&padPointer!=-1)area=-1;
            if(area>=0){contacts.put(id,area);if(area==0){padPointer=id;movePad(id,event.getX(index),event.getY(index));}
                else if(area==1)backend.setContact(id+1000,BrewInputRouter.maskFor(BrewRuntime.AVK_SELECT));}
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if(action==MotionEvent.ACTION_MOVE) {
            int at=event.findPointerIndex(padPointer);if(at>=0)movePad(padPointer,event.getX(at),event.getY(at));
        } else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
            Integer area=contacts.remove(id);backend.removeContact(id+1000);
            if(id==padPointer){padPointer=-1;sector=-1;}
            if(area!=null&&hit(event.getX(index),event.getY(index))==area) {
                if(area==2)backend.tap(BrewRuntime.AVK_0);else if(area==3)backend.tap(BrewRuntime.AVK_CLR);
            }
            if(action==MotionEvent.ACTION_UP)performClick();
        } else if(action==MotionEvent.ACTION_CANCEL)cancelAll();
        invalidate();return true;
    }
    void cancelAll() { for(int id:contacts.keySet())backend.removeContact(id+1000);contacts.clear();padPointer=-1;sector=-1;invalidate(); }
    @Override public boolean performClick() { super.performClick();return true; }
}
