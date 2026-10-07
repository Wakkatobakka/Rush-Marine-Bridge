package com.wakka.omnirushmarine;

import android.content.Context;
import com.wakka.bridge.ReportStore;
import com.wakka.rushbridge.BrewBackend;

final class RushCatalog {
    private static BrewBackend backend;
    static synchronized BrewBackend backend(Context context) {
        if(backend==null){
            final Context app=context.getApplicationContext();backend=new BrewBackend(app);
            final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
                try{java.io.StringWriter text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));
                    ReportStore.preserveLatest(app,ReportStore.compose(app,backend)+"\nUNCAUGHT HOST ERROR on "+thread.getName()+"\n"+text);}
                catch(Throwable ignored){}
                if(previous!=null)previous.uncaughtException(thread,error);
            });
        }
        return backend;
    }
}
