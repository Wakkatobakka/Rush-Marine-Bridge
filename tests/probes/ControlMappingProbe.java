import com.wakka.rushbridge.runtime.BrewRuntime;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Deterministic desktop guest A/B test for Rush Marine control semantics. */
public final class ControlMappingProbe {
  static byte[] mod, progress, bar;
  static final BrewRuntime.PixelDecoder DEC=(png,w,h)->{
    BufferedImage im=ImageIO.read(new ByteArrayInputStream(png)); if(im==null)return null;
    int[] x=new int[im.getWidth()*im.getHeight()]; im.getRGB(0,0,im.getWidth(),im.getHeight(),x,0,im.getWidth()); return x;
  };
  static BrewRuntime.Session gameplay() throws Exception {
    BrewRuntime.Session s=BrewRuntime.startSession(mod,progress,bar,DEC); BrewRuntime.Result r=s.pumpTimers(6);
    r=s.tapKey(BrewRuntime.AVK_SELECT,12); r=s.pumpTimers(350); // Enable Sound YES -> menu
    r=s.tapKey(BrewRuntime.AVK_SELECT,20); r=s.pumpTimers(100); // START
    for(int i=0;i<15;i++) r=s.tapKey(BrewRuntime.AVK_SELECT,30); // story advance
    r=s.pumpTimers(1000); // deterministic active gameplay
    if(!r.sessionLive) throw new IllegalStateException("guest stopped: "+r.liveFailure);
    return s;
  }
  static int[] player(BrewRuntime.Result r){
    int w=r.framebufferWidth,h=r.framebufferHeight; int[] p=r.framebufferArgb;
    boolean[] mask=new boolean[w*h];
    for(int y=21;y<h;y++) for(int x=0;x<w;x++){
      int c=p[y*w+x], rr=(c>>>16)&255, g=(c>>>8)&255, b=c&255;
      mask[y*w+x]=rr>120 && g<180 && b<120;
    }
    boolean[] seen=new boolean[w*h]; int bestN=0,bx=0,by=0;
    int[] qx=new int[w*h],qy=new int[w*h];
    for(int sy=21;sy<h;sy++) for(int sx=0;sx<w;sx++){
      int si=sy*w+sx; if(!mask[si]||seen[si]) continue;
      int qh=0,qt=0,n=0,sumx=0,sumy=0; qx[qt]=sx;qy[qt++]=sy;seen[si]=true;
      while(qh<qt){int x=qx[qh],y=qy[qh++];n++;sumx+=x;sumy+=y;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=x+dx,ny=y+dy;if(nx<0||ny<21||nx>=w||ny>=h)continue;int ni=ny*w+nx;if(mask[ni]&&!seen[ni]){seen[ni]=true;qx[qt]=nx;qy[qt++]=ny;}}
      }
      if(n>bestN){bestN=n;bx=Math.round((float)sumx/n);by=Math.round((float)sumy/n);}
    }
    if(bestN<20) throw new IllegalStateException("player component not found");
    return new int[]{bx,by,bestN};
  }
  static int[] run(int key)throws Exception{
    BrewRuntime.Session s=gameplay(); BrewRuntime.Result r=s.pressKey(key); r=s.pumpTimers(35); r=s.releaseKey(key); r=s.pumpTimers(2); return player(r);
  }
  static void check(String name,int key,int dxSign,int dySign,int[] base)throws Exception{
    int[] p=run(key); int dx=p[0]-base[0],dy=p[1]-base[1];
    boolean ok=(dxSign==0?Math.abs(dx)<=1:Integer.signum(dx)==dxSign)&&(dySign==0?Math.abs(dy)<=1:Integer.signum(dy)==dySign);
    System.out.printf("%s code=0x%04X dx=%d dy=%d %s%n",name,key,dx,dy,ok?"PASS":"FAIL"); if(!ok)throw new IllegalStateException(name+" movement mismatch");
  }
  public static void main(String[] a)throws Exception{
    mod=Files.readAllBytes(Path.of(a[0]));progress=Files.readAllBytes(Path.of(a[1]));bar=Files.readAllBytes(Path.of(a[2]));
    BrewRuntime.Session bs=gameplay(); BrewRuntime.Result br=bs.pumpTimers(37); int[] base=player(br);
    System.out.printf("BASE x=%d y=%d%n",base[0],base[1]);
    check("UP",BrewRuntime.AVK_UP,0,-1,base);
    check("DOWN",BrewRuntime.AVK_DOWN,0,1,base);
    check("LEFT",BrewRuntime.AVK_LEFT,-1,0,base);
    check("RIGHT",BrewRuntime.AVK_RIGHT,1,0,base);
    check("UP_LEFT",BrewRuntime.AVK_1,-1,-1,base);
    check("UP_RIGHT",BrewRuntime.AVK_3,1,-1,base);
    check("DOWN_LEFT",BrewRuntime.AVK_7,-1,1,base);
    check("DOWN_RIGHT",BrewRuntime.AVK_9,1,1,base);
    System.out.printf("FIRE code=0x%04X%n",BrewRuntime.AVK_SELECT);
    System.out.printf("AUTO code=0x%04X%n",BrewRuntime.AVK_0);
    System.out.printf("BACK code=0x%04X%n",BrewRuntime.AVK_CLR);
    System.out.println("CONTROL_MAPPING=PASS");
  }
}
