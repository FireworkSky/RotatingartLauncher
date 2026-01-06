package com.app.ralaunch.utils;

public class NativeMethods {
    private NativeMethods() {}

    public static int chdir(String path) {
        return nativeChdir(path);
    }

    private static native int nativeChdir(String path);
}
