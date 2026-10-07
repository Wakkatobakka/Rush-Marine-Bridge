import com.wakka.rushbridge.runtime.BrewRuntime;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Desktop regression for the persistent guest session + real BREW key sink used by v0.0.8. */
public final class LiveInputProbe {
  public static void main(String[] args) throws Exception {
    byte[] mod=Files.readAllBytes(Path.of(args[0]));
    byte[] progress=Files.readAllBytes(Path.of(args[1]));
    byte[] bar=Files.readAllBytes(Path.of(args[2]));
    BrewRuntime.PixelDecoder dec=(png,w,h)->{
      // Android BitmapFactory rejects the BAR resource prefix. The runtime must
      // pass the decoder an exact PNG stream beginning at the signature.
      if(png.length<8 || (png[0]&255)!=0x89 || png[1]!='P' || png[2]!='N' || png[3]!='G')
        throw new IllegalArgumentException("decoder received prefixed/non-PNG bytes");
      BufferedImage im=ImageIO.read(new ByteArrayInputStream(png));
      if(im==null)return null;
      int[] px=new int[im.getWidth()*im.getHeight()];
      im.getRGB(0,0,im.getWidth(),im.getHeight(),px,0,im.getWidth());
      return px;
    };
    BrewRuntime.Session s=BrewRuntime.startSession(mod,progress,bar,dec);
    BrewRuntime.Result r=s.pumpTimers(6);
    boolean boot=r.sessionLive&&r.pixelBackedDisplay&&r.displayUpdates>=6;
    boolean pngDecode=r.imagePixelDecodes>=4&&r.imagePixelDecodeFailures==0;
    r=s.pressKey(BrewRuntime.AVK_6);
    int updatesAtPress=r.displayUpdates;
    r=s.pumpTimers(20);
    boolean heldAdvanced=r.displayUpdates>updatesAtPress;
    r=s.releaseKey(BrewRuntime.AVK_6);
    boolean key=r.sessionLive&&r.keyPresses==1&&r.keyReleases==1&&r.keyEvents==2&&r.lastKeyCode==BrewRuntime.AVK_6&&heldAdvanced;
    r=s.tapKey(BrewRuntime.AVK_5,12);
    boolean tap=r.sessionLive&&r.keyPresses==2&&r.keyReleases==2&&r.keyEvents==4&&r.lastKeyCode==BrewRuntime.AVK_5;
    boolean blt=r.bitBltCalls>0;
    r=s.pumpTimers(350);
    boolean longRun=r.sessionLive&&r.liveFailure==null&&r.displayUpdates>300;
    System.out.println("PERSISTENT_SESSION="+(boot?"PASS":"FAIL"));
    System.out.println("STRICT_PNG_DECODE="+(pngDecode?"PASS":"FAIL"));
    System.out.println("LIVE_KEY_HOLD_RELEASE="+(key?"PASS":"FAIL"));
    System.out.println("LIVE_KEY_TAP_COMPAT="+(tap?"PASS":"FAIL"));
    System.out.println("IDISPLAY_BITBLT="+(blt?"PASS":"FAIL"));
    System.out.println("LONG_TIMER_RUN="+(longRun?"PASS":"FAIL"));
    System.out.println("steps="+r.steps+" updates="+r.displayUpdates+" bitblt="+r.bitBltCalls+" pngDecodes="+r.imagePixelDecodes+" pngFailures="+r.imagePixelDecodeFailures+" keyEvents="+r.keyEvents+" live="+r.sessionLive+" failure="+r.liveFailure);
    if(!(boot&&pngDecode&&key&&tap&&blt&&longRun))System.exit(2);
  }
}
