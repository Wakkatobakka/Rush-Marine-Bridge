import com.wakka.rushbridge.BrewPayload;
import com.wakka.rushbridge.VerifiedZipStore;
import java.io.*;
import java.nio.file.*;

/** Private/user-supplied-data test: same import/store classes compiled into the Android APK. */
public final class RealPayloadImportProbe {
    public static void main(String[] args)throws Exception {
        File appFiles=new File(args[1]);appFiles.mkdirs();
        VerifiedZipStore store=BrewPayload.store(appFiles);
        try(InputStream in=new FileInputStream(args[0])){store.importZip(in);}store.verify();
        for(VerifiedZipStore.RequiredFile spec:BrewPayload.FILES) {
            byte[] bytes=store.read(spec.path);
            if(bytes.length!=spec.bytes)throw new AssertionError(spec.path);
            System.out.println("VERIFIED "+spec.path+" bytes="+bytes.length);
        }
        System.out.println("REAL_PAYLOAD_IMPORT=PASS\nIMPORTED_DIRECTORY="+store.directory().getAbsolutePath());
    }
}
