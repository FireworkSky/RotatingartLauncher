package com.app.ralaunch.feature.installer

import android.content.Context
import android.net.Uri
import com.app.ralaunch.core.extractor.ArchiveSource
import java.nio.file.Path

/**
 * 待安装的游戏/模组加载器文件：本地路径与 SAF content URI 的统一抽象。
 *
 * [container] 首次访问时基于文件内容解析一次并缓存（解析失败为 null），
 * 后续的插件检测、安装分派都复用该结果；文件名不参与检测。
 */
class GameFile private constructor(
    val source: ArchiveSource
) {

    /** 文件内部结构识别结果；null 表示不是支持的可识别容器 */
    val container: GameFileInspector.Container? by lazy {
        GameFileInspector.inspect(source)
    }

    override fun toString(): String = source.toString()

    companion object {

        /** 从本地文件路径构造 */
        fun of(path: Path): GameFile = GameFile(ArchiveSource.of(path))

        /** 从 SAF content URI 构造（依赖可寻址的 openFileDescriptor） */
        fun of(context: Context, uri: Uri): GameFile = GameFile(ArchiveSource.of(context, uri))
    }
}
