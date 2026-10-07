import com.wakka.rushbridge.runtime.BrewRuntime;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Proves the guest starts a second long MIDI on a distinct IMedia object without first issuing Stop. */
public final class AudioTransitionProbe {
  static boolean midi(byte[] d){return d!=null&&d.length>=4&&d[0]=='M'&&d[1]=='T'&&d[2]=='h'&&d[3]=='d';}
  public static void main(String[] args)throws Exception{
    byte[] mod=Files.readAllBytes(Path.of(args[0])), progress=Files.readAllBytes(Path.of(args[1])), bar=Files.readAllBytes(Path.of(args[2]));
    BrewRuntime.PixelDecoder dec=(png,w,h)->{BufferedImage im=ImageIO.read(new ByteArrayInputStream(png));if(im==null)return null;int[] px=new int[im.getWidth()*im.getHeight()];im.getRGB(0,0,im.getWidth(),im.getHeight(),px,0,im.getWidth());return px;};
    BrewRuntime.Session s=BrewRuntime.startSession(mod,progress,bar,dec); BrewRuntime.Result r=s.pumpTimers(6);
    r=s.tapKey(BrewRuntime.AVK_SELECT,12); r=s.pumpTimers(350); // sound YES -> title/menu music
    r=s.tapKey(BrewRuntime.AVK_SELECT,20);                 // START -> next music immediately
    BrewRuntime.AudioEvent first=null,second=null;
    if(r.audioEvents!=null)for(BrewRuntime.AudioEvent e:r.audioEvents){if(e.kind!=BrewRuntime.AudioEvent.PLAY)continue;if(first==null)first=e;else if(second==null){second=e;break;}}
    boolean ok=first!=null&&second!=null&&first.resourceId==0x232A&&second.resourceId==0x2335&&first.mediaObject!=second.mediaObject&&midi(first.data)&&midi(second.data)&&r.mediaStopRequests==0;
    System.out.printf("FIRST res=0x%04X obj=%08X midi=%s%n",first==null?0:first.resourceId,first==null?0:first.mediaObject,first!=null&&midi(first.data));
    System.out.printf("SECOND res=0x%04X obj=%08X midi=%s%n",second==null?0:second.resourceId,second==null?0:second.mediaObject,second!=null&&midi(second.data));
    System.out.printf("guestStops=%d%n",r.mediaStopRequests);
    System.out.println("STARTUP_MIDI_OVERLAP_RISK="+(ok?"PASS":"FAIL"));
    if(!ok)System.exit(2);
  }
}
