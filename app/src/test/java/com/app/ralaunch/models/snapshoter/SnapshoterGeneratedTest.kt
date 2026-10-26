package com.app.ralaunch.models.snapshoter

import com.app.ralaunch.core.model.GameItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 `@Snapshoter` 生成的深拷贝快照（object 成员与扩展函数两种入口）：
 * - GameItem：`gameEnvVars: Map<String, String?>` → 容器为全新实例，
 *   原容器后续变更不影响快照，null 值条目被保留。
 */
class SnapshoterGeneratedTest {

    @Test
    fun gameItemSnapshotCopiesAllFields() {
        val original = GameItem(
            id = "celeste_a1b2c3d4",
            displayedName = "Celeste",
            iconPathRelative = "icon.png",
            rendererOverride = "gl",
            gameEnvVars = mapOf("LANG" to "zh_CN.UTF-8", "TERM" to null),
        )

        // 扩展调用与非绑定函数引用两种用法等价
        val snapshot = original.snapshot()
        val ref: (GameItem) -> GameItem = GameItem::snapshot

        assertEquals(original, snapshot)
        assertEquals(snapshot, ref(original))
        assertNotSame(original, snapshot)
        assertEquals("celeste_a1b2c3d4", snapshot.id)
        assertEquals("Celeste", snapshot.displayedName)
        assertEquals("icon.png", snapshot.iconPathRelative)
        assertEquals("gl", snapshot.rendererOverride)
    }

    @Test
    fun gameItemSnapshotDeepCopiesEnvVarsMap() {
        val envVars = mutableMapOf("LANG" to "zh_CN.UTF-8", "TERM" to null)
        val original = GameItem(gameEnvVars = envVars)

        val snapshot = original.snapshot()

        // 容器为全新实例，内容一致
        assertNotSame(original.gameEnvVars, snapshot.gameEnvVars)
        assertEquals(2, snapshot.gameEnvVars.size)

        // 原容器后续变更不影响快照；null 值条目被保留
        envVars["LANG"] = "en_US.UTF-8"
        envVars.remove("TERM")
        assertEquals("zh_CN.UTF-8", snapshot.gameEnvVars["LANG"])
        assertTrue(snapshot.gameEnvVars.containsKey("TERM"))
        assertNull(snapshot.gameEnvVars["TERM"])
    }

    @Test
    fun gameItemSnapshotReplacesEmptyDefaultMapWithFreshInstance() {
        val original = GameItem()
        val snapshot = original.snapshot()

        assertEquals(original, snapshot)
        assertNotSame(original.gameEnvVars, snapshot.gameEnvVars)
        assertTrue(snapshot.gameEnvVars.isEmpty())
    }
}
