import com.wakka.rushbridge.BrewInputRouter;
import com.wakka.rushbridge.runtime.BrewRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/** Real original guest: coalesced input and snapshot capture never reset or step the game. */
public final class GuestSnapshotChecks {
    public static void main(String[] args)throws Exception{
        BrewRuntime.Session session=BrewRuntime.startSession(Files.readAllBytes(Path.of(args[0])),Files.readAllBytes(Path.of(args[1])),Files.readAllBytes(Path.of(args[2])),(png,w,h)->{
            java.awt.image.BufferedImage im=ImageIO.read(new ByteArrayInputStream(png));int[] px=new int[im.getWidth()*im.getHeight()];im.getRGB(0,0,im.getWidth(),im.getHeight(),px,0,im.getWidth());return px;
        });
        BrewRuntime.Result before=session.pumpTimers(6),after=session.snapshot();
        if(!after.sessionLive||before.steps!=after.steps||before.displayUpdates!=after.displayUpdates||before.timerCallbacksExecuted!=after.timerCallbacksExecuted||before.lastAudioSerial!=after.lastAudioSerial)throw new AssertionError("snapshot altered original guest");
        BrewInputRouter input=new BrewInputRouter(new BrewInputRouter.Sink(){public void press(int code){session.pressKey(code);}public void release(int code){session.releaseKey(code);}public void tap(int code){session.tapKey(code,1);}});
        input.setKey(1000,BrewRuntime.AVK_RIGHT);input.setKey(-23,BrewRuntime.AVK_RIGHT);input.remove(1000);
        BrewRuntime.Result held=session.snapshot();
        if(held.keyPresses!=1||held.keyReleases!=0)throw new AssertionError("overlapping original guest input released early");
        input.clear();BrewRuntime.Result released=session.snapshot();
        if(released.keyPresses!=1||released.keyReleases!=1||released.keyEvents!=2)throw new AssertionError("original guest did not receive balanced clear");
        String trace=released.report;BrewRuntime.Result again=session.snapshot();
        if(!trace.equals(again.report)||again.steps!=released.steps)throw new AssertionError("repeated capture mutated retained evidence");
        System.out.println("GUEST_SNAPSHOT_NO_RESET=PASS\nGUEST_COALESCED_NATIVE_INPUT=PASS\nGUEST_REPEATED_TRACE_SNAPSHOT=PASS");
    }
}
