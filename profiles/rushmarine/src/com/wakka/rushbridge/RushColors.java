package com.wakka.rushbridge;

final class RushColors {
    private RushColors() {}
    static int darken(int c) { return android.graphics.Color.rgb((int)(android.graphics.Color.red(c)*.18f),
        (int)(android.graphics.Color.green(c)*.18f),(int)(android.graphics.Color.blue(c)*.18f)); }
}
