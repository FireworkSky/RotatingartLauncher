package com.app.ralaunch.core.platform.runtime.dotnet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotNetRuntimeCompatibilityTest {
    @Test
    fun acceptsSameFrameworkWithNewerPatch() {
        assertTrue(DotNetLauncher.isRuntimePatchCompatible("10.0.4", "10.0.0"))
        assertTrue(DotNetLauncher.isRuntimePatchCompatible("10.0.9", "10.0.4"))
    }

    @Test
    fun rejectsOlderPatchAndDifferentFramework() {
        assertFalse(DotNetLauncher.isRuntimePatchCompatible("10.0.3", "10.0.4"))
        assertFalse(DotNetLauncher.isRuntimePatchCompatible("9.0.12", "10.0.4"))
        assertFalse(DotNetLauncher.isRuntimePatchCompatible("10.1.0", "10.0.4"))
    }

    @Test
    fun requiresExactMatchForNonStandardVersionStrings() {
        assertTrue(DotNetLauncher.isRuntimePatchCompatible("10.0.4-preview.1", "10.0.4-preview.1"))
        assertFalse(DotNetLauncher.isRuntimePatchCompatible("10.0.5-preview.1", "10.0.4-preview.1"))
    }
}
