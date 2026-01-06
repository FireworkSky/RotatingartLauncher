package com.app.ralaunch.dotnet

object CoreHostTrace {

    fun initCoreHostTraceRedirect() {
        nativeInitCoreHostTraceRedirect()
    }

    private external fun nativeInitCoreHostTraceRedirect();
}