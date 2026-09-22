package com.app.ralaunch.feature.installer

import android.app.Application
import com.app.ralaunch.core.extractor.SafUriSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * 游戏文件检测回归测试：检测必须基于文件内部特征（zip 条目 / GOG gameinfo），
 * 与外层文件名无关，且本地路径与 SAF content URI 行为一致。
 *
 * 单元测试工作目录是 app/ 模块目录，仓库根目录 testcases/ 下提供真实样例。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GameFileDetectionTest {

    // ==================== GOG .sh 安装器（gameinfo 特征） ====================

    @Test
    fun detectsTerrariaGogShByGameinfoRegardlessOfFileName() {
        val sh = createGogSh(
            fileName = "download_20260919_2134.bin",
            gameName = "Terraria",
            version = "v1.4.5.6"
        )

        val result = InstallPluginRegistry.detectGame(GameFile.of(sh))!!.second

        assertEquals("Terraria", result.definition.displayName)
        assertEquals("v1.4.5.6", result.version)
    }

    @Test
    fun detectsStardewValleyGogShByGameinfo() {
        val sh = createGogSh(
            fileName = "gog_offline_installer.sh",
            gameName = "Stardew Valley",
            version = "v1.6.15"
        )

        val result = InstallPluginRegistry.detectGame(GameFile.of(sh))!!.second

        assertEquals("Stardew Valley", result.definition.displayName)
        assertEquals("v1.6.15", result.version)
    }

    @Test
    fun detectsGogShFromSafUri() {
        val sh = createGogSh(
            fileName = "saf-terraria.sh",
            gameName = "Terraria",
            version = "v1.4.5.6"
        )
        val context = RuntimeEnvironment.getApplication()
        val uri = SafUriSources.register(context, sh.toFile())

        val result = InstallPluginRegistry.detectGame(GameFile.of(context, uri))!!.second

        assertEquals("Terraria", result.definition.displayName)
        assertEquals("v1.4.5.6", result.version)
    }

    @Test
    fun localTerrariaGogSampleIsDetected() {
        val sample = Path.of("..", "testcases", "gogsh", "terraria_v1_4_5_6_89299.sh")
        assumeTrue("Local GOG sample is not available", Files.isRegularFile(sample))

        val result = InstallPluginRegistry.detectGame(GameFile.of(sample))!!.second

        assertEquals("Terraria", result.definition.displayName)
        assertEquals("v1.4.5.6", result.version)
    }

    @Test
    fun unrelatedGogShIsNotDetectedAsGame() {
        val sh = createGogSh(
            fileName = "some_other_gog_game.sh",
            gameName = "Baldur's Gate",
            version = "2.0"
        )

        assertNull(InstallPluginRegistry.detectGame(GameFile.of(sh)))
    }

    // ==================== ZIP（内部条目特征） ====================

    @Test
    fun detectsTerrariaZipByExecutableEntry() {
        val zip = createZip("no_name_match_at_all.zip", "Terraria.exe", "Content/Fonts/Death_Text.xnb")

        val result = InstallPluginRegistry.detectGame(GameFile.of(zip))!!.second

        assertEquals("Terraria", result.definition.displayName)
    }

    @Test
    fun detectsStardewValleyZipByExecutableEntry() {
        val zip = createZip("backup_2026.zip", "Stardew Valley.exe", "Content/XACT/Waves.xwb")

        val result = InstallPluginRegistry.detectGame(GameFile.of(zip))!!.second

        assertEquals("Stardew Valley", result.definition.displayName)
    }

    @Test
    fun detectsCelesteZipByExecutableEntry() {
        val zip = createZip("my_game_backup.zip", "Celeste.exe", "Content/Fonts/dialog.xtt")

        val result = InstallPluginRegistry.detectGame(GameFile.of(zip))!!.second

        assertEquals("Celeste", result.definition.displayName)
    }

    @Test
    fun detectsCelesteItchIoZipFromSample() {
        // itch.io 发行包为扁平结构：Celeste.exe 与 Content/ 均在压缩包根目录
        val zip = Path.of("..", "testcases", "itchio", "celeste-linux.zip")
        assumeTrue("Local itch.io Celeste sample is not available", Files.isRegularFile(zip))

        val result = InstallPluginRegistry.detectGame(GameFile.of(zip))!!.second

        assertEquals("Celeste", result.definition.displayName)
    }

    @Test
    fun detectsTModLoaderByInternalEntriesOnly() {
        // 文件名不含 "tmodloader"：仅凭 tModLoader.dll + tModLoader.deps.json 识别
        val zip = createZip("unknown_release_42.zip", "tModLoader.dll", "tModLoader.deps.json", "serverconfig.txt")

        val result = InstallPluginRegistry.detectModLoader(GameFile.of(zip))!!.second

        assertEquals("Terraria (tModLoader)", result.definition.displayName)
    }

    @Test
    fun detectsEverestZipFromSample() {
        val zip = Path.of("..", "testcases", "everest.zip")
        assumeTrue("Local everest sample is not available", Files.isRegularFile(zip))

        val result = InstallPluginRegistry.detectModLoader(GameFile.of(zip))!!.second

        assertEquals("Celeste (Everest)", result.definition.displayName)
    }

    @Test
    fun detectsSMAPIInstallerZipFromSampleWithVersion() {
        val zip = Path.of("..", "testcases", "SMAPI-4.5.2-installer.zip")
        assumeTrue("Local SMAPI sample is not available", Files.isRegularFile(zip))

        val result = InstallPluginRegistry.detectModLoader(GameFile.of(zip))!!.second

        assertEquals("Stardew Valley (SMAPI)", result.definition.displayName)
        assertEquals("4.5.2", result.version)
    }

    @Test
    fun detectsSMAPIInstalledFormatByAssemblyEntry() {
        val zip = createZip("mod-pack.zip", "StardewModdingAPI.dll", "smapi-internal/SMAPI.Toolkit.dll")

        val result = InstallPluginRegistry.detectModLoader(GameFile.of(zip))!!.second

        assertEquals("Stardew Valley (SMAPI)", result.definition.displayName)
    }

    @Test
    fun modLoaderArchivesAreNotDetectedAsGames() {
        listOf(
            Path.of("..", "testcases", "tModLoader.zip"),
            Path.of("..", "testcases", "everest.zip"),
            Path.of("..", "testcases", "SMAPI-4.5.2-installer.zip")
        ).forEach { zip ->
            assumeTrue("Local sample is not available: $zip", Files.isRegularFile(zip))
            assertNull(InstallPluginRegistry.detectGame(GameFile.of(zip)))
        }
    }

    // ==================== 拒绝无关文件 ====================

    @Test
    fun rejectsUnrelatedZipContent() {
        val zip = createZip("celeste_like.zip", "readme.txt", "setup/other.dll")

        assertNull(InstallPluginRegistry.detectGame(GameFile.of(zip)))
        assertNull(InstallPluginRegistry.detectModLoader(GameFile.of(zip)))
    }

    @Test
    fun rejectsNonArchiveAsContainer() {
        val text = Files.createTempFile("not-an-archive", ".zip")
        text.writeText("plain text content")

        assertNull(GameFile.of(text).container)
        assertNull(InstallPluginRegistry.detectGame(GameFile.of(text)))
        assertNull(InstallPluginRegistry.detectModLoader(GameFile.of(text)))
    }

    // ==================== 样例构造 ====================

    private fun createZip(fileName: String, vararg entries: String): Path {
        val dir = Files.createTempDirectory("detect-zip")
        val zip = dir / fileName
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            entries.forEach { name ->
                out.putNextEntry(ZipEntry(name))
                out.write(ByteArray(16))
                out.closeEntry()
            }
        }
        return zip
    }

    /** 构造 makeself 头 + 内嵌 game_data.zip 的最小 GOG .sh 样例 */
    private fun createGogSh(fileName: String, gameName: String, version: String): Path {
        val dir = Files.createTempDirectory("detect-gog")
        val gameData = dir / "game_data.zip"
        ZipOutputStream(Files.newOutputStream(gameData)).use { zip ->
            zip.putNextEntry(ZipEntry("data/noarch/gameinfo"))
            zip.write("$gameName\n$version\nbuild\nen-US".toByteArray())
            zip.closeEntry()
        }
        val gogSh = dir / fileName
        Files.newOutputStream(gogSh).use { output ->
            // 与真实 makeself 一致：#! 脚本头，SKIP=3 恰好跳过 3 行头（25 字节），
            // SIZE=0 表示无 mojosetup.tar.gz 载荷，zip 数据直接跟随
            output.write("#!/bin/sh\nSKIP=3\nSIZE=0\n".toByteArray())
            output.write(Files.readAllBytes(gameData))
        }
        return gogSh
    }
}
