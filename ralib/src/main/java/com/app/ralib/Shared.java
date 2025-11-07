package com.app.ralib;

import android.content.Context;

public class Shared {
    private Shared() {}

    private static Context context;

    public static void init(Context ctx) {
        context = ctx;
    }

    public static Context getContext() {
        return context;
    }
}
