package com.app.ralaunch.utils

import com.app.ralaunch.jsonconfig.GameItemFlowJsonConfigGenerated

/**
 * 单个游戏 game_info.json 的读写配置。
 *
 * 各游戏文件路径互不相同，不 override configPath，经显式文件参数 load()/save() 读写；
 * 每次读写各建独立实例，实例间不共享状态。
 */
class GameItemConfig : GameItemFlowJsonConfigGenerated()
