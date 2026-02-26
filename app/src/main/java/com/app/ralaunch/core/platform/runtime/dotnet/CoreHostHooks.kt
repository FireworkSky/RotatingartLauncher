package com.app.ralaunch.core.platform.runtime.dotnet

object CoreHostHooks {

    fun initTraceHooks() {
        nativeInitCoreHostTraceHooks()
    }

    fun initCompatHooks() {
        nativeInitCoreHostCompatHooks()
    }

    private external fun nativeInitCoreHostTraceHooks()
    private external fun nativeInitCoreHostCompatHooks()
}
