package com.app.ralaunch.feature.terraria.conversion

import android.content.Context
import android.os.Looper
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

internal object MobileTerrariaConverter {
    private val assetPath = Regex("""(?:Images|Sounds|Fonts)/(?:[\w. -]+/)*[\w. -]+\.xnb""")

    private external fun convertNative(
        workspace: String,
        output: String,
        progress: MobileTerrariaPackage.Progress,
    ): String

    private external fun extractControlsNative(workspace: String, output: String): Int

    @Synchronized
    fun prepareControlArt(context: Context, terrariaRoot: File): File? {
        val converted = File(terrariaRoot, MobileTerrariaPackage.CONTROL_ART_DIRECTORY)
        val revision = File(converted, "revision")
        if (revision.isFile && revision.readText() == MobileTerrariaPackage.CONTROL_ART_REVISION) return converted

        val info = MobileTerrariaPackage.installed(context) ?: return null
        val cache = File(context.cacheDir, "mobile-control-art")
        check(cache.isDirectory || cache.mkdirs())
        val fingerprint = MobileTerrariaPackage.fingerprint(info).replace(':', '-')
        val destination = File(cache, fingerprint)
        if (destination.isDirectory) return destination

        val workspace = Files.createTempDirectory(cache.toPath(), ".extract-").toFile()
        try {
            extractUnity(MobileTerrariaPackage.archives(info), workspace, listOf("data.unity3d")) { _, _, _ -> true }
            val count = prepareControlRecipes(context, workspace)
            val staged = File(workspace, "result")
            System.loadLibrary("terraria_converter")
            check(extractControlsNative(workspace.absolutePath, staged.absolutePath) == count)
            try {
                Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (failure: IOException) {
                if (!destination.isDirectory) throw failure
            }
            return destination
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Synchronized
    fun convertInstalled(context: Context, destination: File, progress: MobileTerrariaPackage.Progress): JSONObject {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Run conversion on a worker thread" }
        val info = checkNotNull(MobileTerrariaPackage.installed(context)) { "Mobile Terraria is not installed" }
        val archives = MobileTerrariaPackage.archives(info)
        require(MobileTerrariaPackage.inspectArchives(archives)) { "不支持的 Terraria PE 安装包结构" }
        val parent = checkNotNull(destination.parentFile)
        require(parent.canonicalFile.toPath().startsWith(context.filesDir.canonicalFile.toPath())) {
            "Conversion output must be in app-private storage"
        }
        check(parent.isDirectory || parent.mkdirs()) { "Could not create the conversion directory" }
        require(!destination.exists()) { "Conversion output already exists" }

        val workspace = Files.createTempDirectory(parent.toPath(), ".conversion-").toFile()
        try {
            check(workspace.usableSpace >= 2L * 1024 * 1024 * 1024) { "本机转换需要至少 2 GiB 可用空间" }
            val profile = File(workspace, "profile.zip")
            context.assets.open(MobileTerrariaPackage.PROFILE).use { input ->
                profile.outputStream().use { input.copyTo(it) }
            }
            extractUnity(archives, workspace, listOf("data.unity3d", "resources.resource"), progress)
            prepareProfile(profile, workspace)
            val controlCount = prepareControlRecipes(context, workspace)
            val staged = File(workspace, "result")
            System.loadLibrary("terraria_converter")
            val report = JSONObject(convertNative(workspace.absolutePath, staged.absolutePath, progress))
            check(report.getBoolean("complete"))
            check(report.getInt("control_sprites") == controlCount)
            File(staged, "${MobileTerrariaPackage.CONTROL_ART_DIRECTORY}/revision")
                .writeText(MobileTerrariaPackage.CONTROL_ART_REVISION)
            check(progress.update("verify", 1, 1)) { "Mobile Terraria conversion cancelled" }
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return report
        } finally {
            workspace.deleteRecursively()
        }
    }

    private fun extractUnity(
        archives: List<File>,
        workspace: File,
        names: List<String>,
        progress: MobileTerrariaPackage.Progress,
    ) {
        val pending = names.toMutableSet()
        for (archive in archives) {
            ZipFile(archive).use { zip ->
                for (name in names) {
                    if (name !in pending) continue
                    val entry = zip.getEntry(MobileTerrariaPackage.DATA + name) ?: continue
                    require(!entry.isDirectory && entry.size in 1..MAX_UNITY_ENTRY) { "无效的 Unity 资源大小" }
                    zip.getInputStream(entry).use { input ->
                        copyBounded(input, File(workspace, name), entry.size) {
                            if (!progress.update("extract", names.indexOf(name), names.size)) {
                                throw CancellationException("Android 资源转换已取消")
                            }
                        }
                    }
                    pending.remove(name)
                }
            }
            if (pending.isEmpty()) break
        }
        check(pending.isEmpty()) { "安装包缺少 Unity 资源文件" }
    }

    private fun prepareControlRecipes(context: Context, workspace: File): Int {
        val recipes = context.assets.open("mobile-terraria/control-sprites.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("assets")
        }
        DataOutputStream(File(workspace, "control-recipes").outputStream().buffered()).use { output ->
            fun number(value: Int) = output.writeInt(Integer.reverseBytes(value))
            fun coordinates(objectValue: JSONObject, key: String, size: Int, absent: Int = 0) {
                val array = objectValue.optJSONArray(key)
                check(array == null || array.length() == size)
                repeat(size) { number(array?.getInt(it) ?: absent) }
            }

            number(1)
            number(recipes.length())
            for (name in recipes.keys()) {
                val recipe = recipes.getJSONObject(name)
                val layers = recipe.optJSONArray("layers") ?: JSONArray().put(recipe)
                val palette = recipe.optJSONArray("palette")
                output.write(name.toByteArray(Charsets.US_ASCII))
                output.writeByte(0)
                coordinates(recipe, "size", 2)
                val flags = (if (recipe.optBoolean("flip_y")) 1 else 0) or
                    (if (palette != null) 2 else 0) or
                    (if (recipe.optBoolean("gold_contour")) 4 else 0) or
                    (if (recipe.optBoolean("iris")) 8 else 0)
                output.writeByte(flags)
                output.writeByte(layers.length())
                repeat(layers.length()) {
                    val layer = layers.getJSONObject(it)
                    number(layer.getString("sprite").toUInt(16).toInt())
                    coordinates(layer, "crop", 4)
                    coordinates(layer, "frame", 2)
                    coordinates(layer, "at", 2, -1)
                }
                if (palette != null) {
                    check(palette.length() == 2)
                    repeat(2) { index ->
                        val color = palette.getJSONArray(index)
                        check(color.length() == 4)
                        repeat(4) { output.writeByte(color.getInt(it)) }
                    }
                }
            }
        }
        return recipes.length()
    }

    private fun prepareProfile(profile: File, workspace: File) {
        val metadata = listOf("PixelShader.xnb", "ScreenShader.xnb", "TileShader.xnb",
            "TerrariaMusic.xgs", "Sound Bank.xsb")
        val inventory = sortedMapOf<String, Pair<Int, Int>>()
        ZipFile(profile).use { zip ->
            for (name in metadata) {
                zip.getInputStream(zip.getEntry(name)).use { copyBounded(it, File(workspace, name), MAX_PROFILE_ENTRY) }
            }
            zip.getInputStream(zip.getEntry("images.tsv")).buffered().use { input ->
                input.mark(2)
                val first = input.read()
                val second = input.read()
                input.reset()
                val encoding = if (first == 255 && second == 254 || first == 254 && second == 255)
                    Charsets.UTF_16 else Charsets.UTF_8
                input.reader(encoding).buffered().useLines { lines ->
                    val rows = lines.iterator()
                    val header = rows.next().removePrefix("\uFEFF").split('\t')
                    val path = header.indexOf("Path")
                    val width = header.indexOf("Width")
                    val height = header.indexOf("Height")
                    require(minOf(path, width, height) >= 0) { "无效的贴图资源清单" }
                    for (line in rows) {
                        if (line.isBlank()) continue
                        val fields = line.split('\t')
                        val name = fields[path] + ".xnb"
                        checkPath(name, "Images/")
                        val size = fields[width].toInt() to fields[height].toInt()
                        require(size.first > 0 && size.second > 0)
                        inventory[name] = size
                    }
                }
            }
            val assets = zip.getInputStream(zip.getEntry("assets.json")).bufferedReader().use { JSONArray(it.readText()) }
            repeat(assets.length()) { index ->
                val name = assets.getString(index)
                checkPath(name, if (name.startsWith("Sounds/")) "Sounds/" else "Fonts/")
                inventory[name] = 0 to 0
            }
            ZipInputStream(zip.getInputStream(zip.getEntry("texture-fallback.zip"))).use { fallback ->
                while (true) {
                    val entry = fallback.nextEntry ?: break
                    checkPath(entry.name, "Images/")
                    require(!entry.isDirectory)
                    copyBounded(fallback, File(workspace, "fallback/${entry.name}"), MAX_PROFILE_ENTRY)
                }
            }
        }
        require(inventory.isNotEmpty()) { "资源清单为空" }
        File(workspace, "inventory.tsv").bufferedWriter().use { writer ->
            for ((name, size) in inventory) writer.write("$name\t${size.first}\t${size.second}\n")
        }
    }

    private fun checkPath(name: String, prefix: String) {
        require(name.startsWith(prefix) && assetPath.matches(name) &&
            name.split('/').none { it == "." || it == ".." }) { "无效的资源路径：$name" }
    }

    private fun copyBounded(input: InputStream, file: File, limit: Long, progress: () -> Unit = {}) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            val buffer = ByteArray(256 * 1024)
            var size = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                check(size <= limit) { "资源文件超出大小限制" }
                progress()
                output.write(buffer, 0, count)
            }
        }
    }

    private const val MAX_UNITY_ENTRY = 1024L * 1024 * 1024
    private const val MAX_PROFILE_ENTRY = 64L * 1024 * 1024
}
