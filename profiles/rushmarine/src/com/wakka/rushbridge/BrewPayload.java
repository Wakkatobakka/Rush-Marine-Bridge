package com.wakka.rushbridge;

import java.io.File;

/** Exact supported game-data identity. Contains hashes and sizes, never game bytes. */
public final class BrewPayload {
    public static final String VERSION="0.1.2";
    public static final VerifiedZipStore.RequiredFile[] FILES={
        new VerifiedZipStore.RequiredFile("277700.mif",10066,"3e8dbf746eba5e25a4f33791b81b71c37fbcecb04a822c3cf2f680dcc3d5de6a"),
        new VerifiedZipStore.RequiredFile("277700/mmassault.mod",320356,"e6335ec88581199227aeaba40412bd53c61ed4c80f95b07c02d575b36c156840"),
        new VerifiedZipStore.RequiredFile("277700/mmassault.sig",2748,"3f5926a30d48fd81d9551af402b58e0447cea0a73529fa4289b6fcb0fdbad5c1"),
        new VerifiedZipStore.RequiredFile("277700/mmassaultbacksmall.bar",200931,"350b5dfe8492bf27b1c7f6c2366e8df7985defd1793e4a5f6d75f6fe8f7260d9"),
        new VerifiedZipStore.RequiredFile("277700/progress.bin",29,"11e431c215c5bd334cecbd43148274edf3ffdbd6cd6479fe279577fbe5f52ce6")
    };
    private BrewPayload(){}
    public static VerifiedZipStore store(File appFiles){return new VerifiedZipStore(new File(appFiles,"rushmarine-data"),FILES);}
}
