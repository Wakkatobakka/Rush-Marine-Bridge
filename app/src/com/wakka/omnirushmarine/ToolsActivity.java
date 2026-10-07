package com.wakka.omnirushmarine;

import com.wakka.bridge.BridgeBackend;
import com.wakka.bridge.BridgeToolsActivity;

public final class ToolsActivity extends BridgeToolsActivity {
    @Override protected BridgeBackend backend(){return RushCatalog.backend(this);}
    @Override protected boolean compactReportLayout(){return true;}
    @Override protected void onResume(){super.onResume();RushCatalog.backend(this).captureReport(()->{if(!isFinishing()&&!isDestroyed())refreshReport();});}
}
