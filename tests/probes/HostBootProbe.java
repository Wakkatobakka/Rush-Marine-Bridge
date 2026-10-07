import com.wakka.rushbridge.runtime.BrewRuntime;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.io.File;

public final class HostBootProbe {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            System.err.println("usage: HostBootProbe <mmassault.mod> [progress.bin] [mmassaultbacksmall.bar]");
            System.exit(2);
        }
        byte[] mod = Files.readAllBytes(Path.of(args[0]));
        byte[] progress = args.length >= 2 ? Files.readAllBytes(Path.of(args[1])) : new byte[0];
        byte[] bar = args.length >= 3 ? Files.readAllBytes(Path.of(args[2])) : new byte[0];
        BrewRuntime.PixelDecoder decoder = (png, width, height) -> {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return null;
            int[] px = new int[img.getWidth() * img.getHeight()];
            img.getRGB(0, 0, img.getWidth(), img.getHeight(), px, 0, img.getWidth());
            return px;
        };
        BrewRuntime.Result r = BrewRuntime.runBootProbe(mod, progress, bar, decoder);
        System.out.print(r.report);
        String frameOut=System.getProperty("rush.frame");
        if(frameOut!=null && r.framebufferArgb!=null){
            BufferedImage frame=new BufferedImage(r.framebufferWidth,r.framebufferHeight,BufferedImage.TYPE_INT_ARGB);
            frame.setRGB(0,0,r.framebufferWidth,r.framebufferHeight,r.framebufferArgb,0,r.framebufferWidth);
            ImageIO.write(frame,"png",new File(frameOut));
            System.out.println("FRAME_DUMP "+frameOut);
        }
        boolean ok = r.moduleLoaded && r.appletCreated && r.startEventAccepted && r.progressFileRead
                && r.timerCallbackExecuted && r.pixelBackedDisplay && r.drawRectCalls > 0
                && r.displayUpdates > 0 && r.failure == null;
        System.out.printf(
                "RESULT module=%s applet=%s start=%s progress=%s timer=%s callbacks=%d rects=%d updates=%d framebuffer=%s nonblack=%d reads=%d steps=%d deep=%s failure=%s%n",
                r.moduleLoaded, r.appletCreated, r.startEventAccepted, r.progressFileRead,
                r.timerCallbackExecuted, r.timerCallbacksExecuted, r.drawRectCalls, r.displayUpdates,
                r.pixelBackedDisplay, r.framebufferNonBlackPixels, r.fileReadBytes, r.steps,
                String.valueOf(r.extendedTimerFailure), String.valueOf(r.failure));
        if (!ok) System.exit(1);
    }
}
