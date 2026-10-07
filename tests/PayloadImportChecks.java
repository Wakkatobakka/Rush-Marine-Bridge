import com.wakka.rushbridge.VerifiedZipStore;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Synthetic files exercise the real import/activation policy without redistributing game bytes. */
public final class PayloadImportChecks {
    private static int checks;
    private static final Map<String,byte[]> files=new LinkedHashMap<>();
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] zip(Map<String,byte[]> data)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> e:data.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}
        return out.toByteArray();
    }
    private static void reject(VerifiedZipStore store,Map<String,byte[]> data,String why)throws Exception {
        boolean rejected=false;try{store.importZip(new ByteArrayInputStream(zip(data)));}catch(IOException e){rejected=true;}
        check(rejected,why);check(store.problem()==null,"invalid import must retain previously verified data: "+why);
    }
    public static void main(String[] args)throws Exception {
        files.put("277700.mif",new byte[]{1,2,3});files.put("277700/mmassault.mod",new byte[]{4,5,6,7});
        files.put("277700/mmassault.sig",new byte[]{8});files.put("277700/mmassaultbacksmall.bar",new byte[]{9,10});files.put("277700/progress.bin",new byte[]{11});
        VerifiedZipStore.RequiredFile[] expected=new VerifiedZipStore.RequiredFile[files.size()];int i=0;
        for(Map.Entry<String,byte[]> e:files.entrySet())expected[i++]=new VerifiedZipStore.RequiredFile(e.getKey(),e.getValue().length,VerifiedZipStore.hex(MessageDigest.getInstance("SHA-256").digest(e.getValue())));
        File root=Files.createTempDirectory("rush-import-tests-").toFile();
        try {
            VerifiedZipStore store=new VerifiedZipStore(new File(root,"data"),expected);
            check(store.problem()!=null,"fresh install must have no playable payload");
            try{store.read("277700/mmassault.mod");throw new AssertionError("read before import");}catch(IOException good){checks++;}
            store.importZip(new ByteArrayInputStream(zip(files)));check(store.problem()==null,"raw BREW ZIP import");
            check(Arrays.equals(store.read("277700/mmassault.mod"),files.get("277700/mmassault.mod")),"read imported module bytes");
            check(new VerifiedZipStore(new File(root,"data"),expected).problem()==null,"verified data persists across process/store reconstruction");
            Map<String,byte[]> nested=new LinkedHashMap<>();for(Map.Entry<String,byte[]> e:files.entrySet())nested.put("v.1.1.11-cdm2030-128x160/"+e.getKey(),e.getValue());
            store.importZip(new ByteArrayInputStream(zip(nested)));check(store.problem()==null,"wrapper folder import");
            Map<String,byte[]> omni=new LinkedHashMap<>();for(Map.Entry<String,byte[]> e:files.entrySet())omni.put("content/brew/"+e.getKey(),e.getValue());omni.put("omni-game.properties",new byte[]{1});
            store.importZip(new ByteArrayInputStream(zip(omni)));check(store.problem()==null,"prepared Omni data ZIP import");
            Map<String,byte[]> bad=new LinkedHashMap<>(files);bad.remove("277700/progress.bin");reject(store,bad,"missing required file");
            bad=new LinkedHashMap<>(files);bad.put("277700/mmassault.mod",new byte[]{4,5,6,99});reject(store,bad,"same-sized modified module");
            bad=new LinkedHashMap<>(files);bad.put("277700/mmassault.mod",new byte[]{4});reject(store,bad,"wrong file size");
            bad=new LinkedHashMap<>(files);bad.put("277700/mmassault.mod",new byte[100000]);reject(store,bad,"compressed oversized module");
            bad=new LinkedHashMap<>(files);bad.put("other/277700/mmassault.mod",files.get("277700/mmassault.mod"));reject(store,bad,"ambiguous duplicate game file");
            for(String path:new String[]{"../escape","/absolute","a/../escape","C:/escape","bad\\escape","bad//escape","./escape"}) {
                bad=new LinkedHashMap<>(files);bad.put(path,new byte[]{1});reject(store,bad,"unsafe path "+path);
            }
            bad=new LinkedHashMap<>(files);bad.put("ignored.bin",new byte[17*1024*1024]);reject(store,bad,"ZIP expansion bound includes ignored entries");
            bad=new LinkedHashMap<>(files);bad.put("ignored-directory/",new byte[17*1024*1024]);reject(store,bad,"ZIP expansion bound includes directory payloads");
            bad=new LinkedHashMap<>(files);for(int n=0;n<513;n++)bad.put("extra/"+n,new byte[0]);reject(store,bad,"ZIP entry count bound");
            boolean rejected=false;try{store.importZip(new ByteArrayInputStream(new byte[]{1,2,3}));}catch(IOException good){rejected=true;}
            check(rejected&&store.problem()==null,"non-ZIP rejected without replacing active data");
            File module=new File(store.directory(),"277700/mmassault.mod");Files.write(module.toPath(),new byte[]{4,5,6,99});
            check(store.problem()!=null,"launch verification catches post-import tampering");
            try{store.read("277700/mmassault.mod");throw new AssertionError("tampered read");}catch(IOException good){checks++;}
            store.importZip(new ByteArrayInputStream(zip(files)));check(store.problem()==null,"valid re-import repairs corrupted private data");
            File[] leftovers=new File(root,"data").listFiles();check(leftovers!=null&&leftovers.length==1,"no staged partial data left after imports");
            check(!new File(root,"escape").exists(),"ZIP never writes outside private data root");
            // Android app storage may be reached through an OS-owned directory alias.
            File realApp=new File(root,"data-data/package");check(realApp.mkdirs(),"create physical app storage");
            File aliasApp=new File(root,"data-user-0-package");Files.createSymbolicLink(aliasApp.toPath(),realApp.toPath());
            File aliasRoot=new File(aliasApp,"files/rushmarine-data");
            VerifiedZipStore aliased=new VerifiedZipStore(aliasRoot,expected);
            aliased.importZip(new ByteArrayInputStream(zip(files)));
            check(aliased.problem()==null,"trusted Android-style storage alias imports successfully");
            check(Arrays.equals(aliased.read("277700/mmassault.mod"),files.get("277700/mmassault.mod")),"aliased storage reads verified bytes");
            check(new VerifiedZipStore(aliasRoot,expected).problem()==null,"aliased storage persists across reconstruction");
            File outside=new File(root,"outside.mod");Files.write(outside.toPath(),files.get("277700/mmassault.mod"));
            File aliasedModule=new File(aliased.directory(),"277700/mmassault.mod");Files.delete(aliasedModule.toPath());
            Files.createSymbolicLink(aliasedModule.toPath(),outside.toPath());
            check(aliased.problem()!=null,"payload symlink outside storage rejected even with valid hash");
            try{aliased.read("277700/mmassault.mod");throw new AssertionError("symlink read");}catch(IOException good){checks++;}
            aliased.importZip(new ByteArrayInputStream(zip(files)));
            check(aliased.problem()==null,"valid re-import repairs redirected payload");
            check(!Files.isSymbolicLink(aliasedModule.toPath()),"repaired payload is a regular file");
            check(outside.isFile(),"outside symlink target preserved");
            File outsideFolder=new File(root,"outside-folder");check(outsideFolder.mkdir(),"create outside folder fixture");
            for(Map.Entry<String,byte[]> e:files.entrySet())if(e.getKey().startsWith("277700/"))Files.write(new File(outsideFolder,e.getKey().substring(7)).toPath(),e.getValue());
            File gameFolder=new File(aliased.directory(),"277700");delete(gameFolder);
            Files.createSymbolicLink(gameFolder.toPath(),outsideFolder.toPath());
            check(aliased.problem()!=null,"redirected payload directory rejected");
            aliased.importZip(new ByteArrayInputStream(zip(files)));
            check(aliased.problem()==null,"valid re-import repairs redirected directory");
            check(new File(outsideFolder,"mmassault.mod").isFile(),"cleanup preserves outside directory contents");
            System.out.println("PAYLOAD_IMPORT_CHECKS=PASS assertions="+checks);
        }finally{delete(root);}
    }
    private static void delete(File path){File[] children=Files.isSymbolicLink(path.toPath())?null:path.listFiles();if(children!=null)for(File child:children)delete(child);path.delete();}
}
