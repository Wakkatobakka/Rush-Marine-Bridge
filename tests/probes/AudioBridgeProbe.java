import com.wakka.rushbridge.runtime.BrewRuntime;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Drives the original Enable Sound -> startup path far enough to prove guest IMedia extraction/play requests. */
public final class AudioBridgeProbe {
  public static void main(String[] args) throws Exception {
    byte[] mod=Files.readAllBytes(Path.of(args[0]));
    byte[] progress=Files.readAllBytes(Path.of(args[1]));
    byte[] bar=Files.readAllBytes(Path.of(args[2]));
    BrewRuntime.PixelDecoder dec=(png,w,h)->{
      BufferedImage im=ImageIO.read(new ByteArrayInputStream(png));
      if(im==null)return null;
      int[] px=new int[im.getWidth()*im.getHeight()];
      im.getRGB(0,0,im.getWidth(),im.getHeight(),px,0,im.getWidth());return px;
    };
    BrewRuntime.Session s=BrewRuntime.startSession(mod,progress,bar,dec);
    BrewRuntime.Result r=s.pumpTimers(6);
    // Initial prompt has YES selected; SELECT accepts sound.
    r=s.tapKey(BrewRuntime.AVK_5,12);
    for(int i=0;i<500 && r.sessionLive && r.mediaPlayRequests==0;i++) r=s.pumpTimers(1);
    boolean media=r.mediaCreates>0&&r.mediaSetDataCalls>0&&r.mediaPlayRequests>0&&r.lastAudioResourceId==0x232A;
    boolean bytes=false;
    if(r.audioEvents!=null)for(BrewRuntime.AudioEvent e:r.audioEvents)if(e.kind==BrewRuntime.AudioEvent.PLAY&&e.resourceId==0x232A&&e.data!=null&&e.data.length>=4&&e.data[0]=='M'&&e.data[1]=='T'&&e.data[2]=='h'&&e.data[3]=='d')bytes=true;
    System.out.println("GUEST_IMEDIA_HLE="+(media?"PASS":"FAIL"));
    System.out.println("MIDI_PAYLOAD_BRIDGE="+(bytes?"PASS":"FAIL"));
    System.out.printf("mediaCreates=%d data=%d play=%d stop=%d resource=0x%04X serial=%d unknown=%d live=%s fail=%s%n",r.mediaCreates,r.mediaSetDataCalls,r.mediaPlayRequests,r.mediaStopRequests,r.lastAudioResourceId,r.lastAudioSerial,r.unknownInterfaceCalls,r.sessionLive,r.liveFailure);
    if(!(media&&bytes))System.exit(2);
  }
}
