package com.wakka.omnirushmarine;

import android.content.Intent;
import com.wakka.bridge.BridgeBackend;
import com.wakka.bridge.BridgeGameActivity;

public final class GameActivity extends BridgeGameActivity {
    @Override protected BridgeBackend backend(){return RushCatalog.backend(this);}
    @Override protected Intent toolsIntent(){return new Intent(this,ToolsActivity.class);}
    @Override protected int controlsHeightDp(boolean keypad){return keypad?284:216;}
    @Override protected int gameplayAccent(){return 0xff82be5c;}
    @Override protected void openReport(){startActivity(toolsIntent());}
}
