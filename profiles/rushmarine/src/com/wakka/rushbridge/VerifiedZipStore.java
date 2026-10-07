package com.wakka.rushbridge;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Bounded, hash-verified ZIP import. Only verified files are published to the active directory. */
public final class VerifiedZipStore {
    public static final long MAX_ZIP_BYTES=16L*1024*1024, MAX_EXPANDED_BYTES=16L*1024*1024;
    private static final int MAX_ENTRIES=512;
    public static final class RequiredFile {
        public final String path,sha256;
        public final long bytes;
        public RequiredFile(String path,long bytes,String sha256) {
            if(!safePath(path)||bytes<0||!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid file specification");
            this.path=path;this.bytes=bytes;this.sha256=sha256;
        }
    }
    private final File root,active;
    private final RequiredFile[] required;
    public VerifiedZipStore(File root,RequiredFile[] required) {
        if(required.length==0)throw new IllegalArgumentException("No required files");
        // Android's app files directory may use an OS-owned /data/user/0 alias.
        // Resolve only this trusted root; payload paths must still be regular descendants.
        try{this.root=root.getCanonicalFile();}
        catch(IOException e){throw new IllegalArgumentException("Cannot resolve private game-data storage",e);}
        this.active=new File(this.root,"verified-v1");this.required=required.clone();
        Set<String> names=new HashSet<>();
        for(RequiredFile f:this.required)if(!names.add(f.path))throw new IllegalArgumentException("Duplicate required path");
    }
    public File directory(){return active;}
    public synchronized String problem() {
        try{verifyDirectory(active);return null;}
        catch(IOException e){return e.getMessage();}
    }
    public synchronized byte[] read(String path)throws IOException {
        RequiredFile spec=null;for(RequiredFile f:required)if(f.path.equals(path))spec=f;
        if(spec==null)throw new IOException("Unknown game-data file");
        File file=checkedFile(active,spec.path);verifyFile(file,spec);
        try(InputStream in=new FileInputStream(file)) {
            ByteArrayOutputStream out=new ByteArrayOutputStream((int)spec.bytes);copy(in,out,spec.bytes);
            return out.toByteArray();
        }
    }
    public synchronized void verify()throws IOException {verifyDirectory(active);}
    public synchronized void importZip(InputStream source)throws IOException {
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create private game-data storage");
        File staging=new File(root,"import-"+UUID.randomUUID());
        if(!staging.mkdir())throw new IOException("Cannot stage the selected ZIP");
        try {
            Set<String> names=new HashSet<>(),found=new HashSet<>();long expanded=0;int count=0;
            try(ZipInputStream zip=new ZipInputStream(new BoundedInput(source,MAX_ZIP_BYTES))) {
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    if(++count>MAX_ENTRIES)throw new IOException("ZIP has too many entries (maximum 512)");
                    String path=entry.getName();
                    if(!safePath(path))throw new IOException("ZIP contains an unsafe path");
                    if(!names.add(path))throw new IOException("ZIP contains duplicate entries");
                    RequiredFile selected=null;
                    for(RequiredFile f:required)if(path.equals(f.path)||path.endsWith("/"+f.path)){selected=f;break;}
                    if(selected!=null&&!found.add(selected.path))throw new IOException("ZIP contains multiple copies of "+selected.path);
                    if(selected!=null&&entry.getSize()>=0&&entry.getSize()!=selected.bytes)throw new IOException("Wrong size for "+selected.path);
                    File target=selected==null?null:checkedFile(staging,selected.path);
                    if(target!=null&&!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Cannot stage game data");
                    OutputStream out=target==null?null:new FileOutputStream(target);
                    long bytes=0;
                    try {
                        byte[] buffer=new byte[8192];int n;
                        while((n=zip.read(buffer))!=-1) {
                            expanded+=n;bytes+=n;
                            if(expanded>MAX_EXPANDED_BYTES)throw new IOException("Expanded ZIP exceeds 16 MiB");
                            if(selected!=null&&bytes>selected.bytes)throw new IOException("Wrong size for "+selected.path);
                            if(out!=null)out.write(buffer,0,n);
                        }
                    }finally{if(out!=null)out.close();}
                    if(selected!=null)verifyFile(target,selected);
                    zip.closeEntry();
                }
            }
            for(RequiredFile f:required)if(!found.contains(f.path))throw new IOException("Missing "+f.path+". Select the compatible Rush Marine BREW ZIP.");
            verifyDirectory(staging);
            // A failed import never changes the active dataset. Re-import of the same data is harmless.
            if(problem()==null)return;
            File backup=new File(root,"previous-"+UUID.randomUUID());boolean movedOld=false;
            if(active.exists()) {
                if(!active.renameTo(backup))throw new IOException("Cannot replace invalid game data");
                movedOld=true;
            }
            if(!staging.renameTo(active)) {
                if(movedOld&&!backup.renameTo(active))throw new IOException("Activation failed; re-import the supported game ZIP");
                throw new IOException("Cannot activate the verified game data");
            }
            if(movedOld)deleteTree(backup);
        }finally{deleteTree(staging);}
    }
    private void verifyDirectory(File directory)throws IOException {
        if(!directory.isDirectory())throw new IOException("No verified Rush Marine game data imported");
        for(RequiredFile f:required)verifyFile(checkedFile(directory,f.path),f);
    }
    private static void verifyFile(File file,RequiredFile f)throws IOException {
        if(!file.isFile()||file.length()!=f.bytes)throw new IOException("Missing or wrong-sized "+f.path);
        try(InputStream in=new FileInputStream(file)) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int n;long bytes=0;
            while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>f.bytes)throw new IOException("File changed during verification");digest.update(buffer,0,n);}
            if(bytes!=f.bytes||!hex(digest.digest()).equals(f.sha256))throw new IOException("Unsupported or modified "+f.path+". This release supports English BREW 1.1.11, CDM2030 128×160.");
        }catch(java.security.NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}
    }
    public static String hex(byte[] bytes) {
        StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format(Locale.US,"%02x",b&255));return result.toString();
    }
    private static File checkedFile(File directory,String path)throws IOException {
        File file=new File(directory,path);
        if(!file.getCanonicalPath().equals(file.getAbsolutePath())||!file.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator))throw new IOException("Unsafe game-data storage path");
        return file;
    }
    private static boolean safePath(String path) {
        if(path==null||path.isEmpty()||path.startsWith("/")||path.indexOf('\\')>=0||path.indexOf(':')>=0||path.indexOf('\0')>=0)return false;
        String p=path.endsWith("/")?path.substring(0,path.length()-1):path;
        for(String part:p.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))return false;
        return true;
    }
    private static long copy(InputStream in,OutputStream out,long limit)throws IOException {
        byte[] buffer=new byte[8192];long bytes=0;int n;
        while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>limit)throw new IOException("File exceeds expected size");out.write(buffer,0,n);}return bytes;
    }
    private static void deleteTree(File file) {
        // Remove a redirected entry itself, never its target during repair/cleanup.
        boolean regular=false;
        try{regular=file.getCanonicalFile().equals(file.getAbsoluteFile());}catch(IOException ignored){}
        if(regular&&file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);}
        file.delete();
    }
    private static final class BoundedInput extends FilterInputStream {
        private long remaining;
        BoundedInput(InputStream source,long limit){super(source);remaining=limit;}
        @Override public int read()throws IOException {int b=in.read();if(b!=-1&&--remaining<0)throw new IOException("ZIP exceeds 16 MiB");return b;}
        @Override public int read(byte[] b,int off,int len)throws IOException {
            int n=in.read(b,off,(int)Math.min(len,remaining+1));if(n>0&&(remaining-=n)<0)throw new IOException("ZIP exceeds 16 MiB");return n;
        }
    }
}
