package com.app.ralaunch.easytier

import android.util.Log

/**
 * EasyTier TOML 配置构建器
 * 负责生成连接配置
 */
internal object EasyTierConfigBuilder {

    private const val TAG = "EasyTierConfig"

    /**
     * 构建 EasyTier 配置
     * @param instanceName 实例名称
     * @param networkName 网络名称
     * @param networkSecret 网络密钥
     * @param isHost 是否是房主
     * @param withPortForward 是否启用端口转发（仅加入者有效）
     * @param gamePorts 需要暴露/转发的游戏端口列表
     * @param publicServers 公共服务器列表
     */
    fun buildConfig(
        instanceName: String,
        networkName: String,
        networkSecret: String,
        isHost: Boolean,
        withPortForward: Boolean = true,
        gamePorts: List<Int> = listOf(EasyTierManager.TERRARIA_PORT, EasyTierManager.STARDEW_VALLEY_PORT),
        publicServers: List<String>
    ): String {
        val hostname = if (isHost) "host" else "guest_${System.currentTimeMillis() % 1000}"

        val peersConfig = publicServers.joinToString("\n") { server ->
            """
[[peer]]
uri = "$server"
            """.trim()
        }

        val portConfig = if (isHost) {
            "# 房主配置：ipv4=${EasyTierManager.HOST_IP_CIDR} (顶层字段)"
        } else if (withPortForward) {
            "# 加入者配置：端口转发到 ${EasyTierManager.HOST_IP}"
        } else {
            "# 加入者配置：等待发现房主"
        }

        Log.w("DEBUG_MULTIPLAYER", "=== buildConfig: isHost=$isHost, withPortForward=$withPortForward ===")
        Log.w("DEBUG_MULTIPLAYER", "portConfig:\n$portConfig")

        val topLevelConfig = if (isHost) {
            val tcpWhitelist = gamePorts.joinToString(", ") { "\"$it\"" }
            val udpWhitelist = gamePorts.joinToString(", ") { "\"$it\"" }
            """
ipv4 = "${EasyTierManager.HOST_IP_CIDR}"
dhcp = false
proxy_network = [{ cidr = "10.126.126.0/24" }]
tcp_whitelist = [$tcpWhitelist]
udp_whitelist = [$udpWhitelist]
            """.trim()
        } else {
            ""
        }

        val whitelistConfig = ""

        val portForwardConfig = if (!isHost) {
            val portForwards = gamePorts.joinToString("\n") { port ->
                """
[[port_forward]]
bind_addr = "127.0.0.1:$port"
dst_addr = "${EasyTierManager.HOST_IP}:$port"
proto = "tcp"

[[port_forward]]
bind_addr = "127.0.0.1:$port"
dst_addr = "${EasyTierManager.HOST_IP}:$port"
proto = "udp"
                """.trim()
            }
            portForwards
        } else {
            ""
        }

        return """
instance_name = "$instanceName"
hostname = "$hostname"
$topLevelConfig

[network_identity]
network_name = "$networkName"
network_secret = "$networkSecret"

$whitelistConfig

$portForwardConfig

# TCP/UDP listener - 允许其他玩家直接连接（参考 Terracotta）
listeners = ["tcp://0.0.0.0:0", "udp://0.0.0.0:0"]

$peersConfig

[flags]
no_tun = true
enable_encryption = true
enable_kcp_proxy = true
latency_first = true
multi_thread = true
data_compress_algo = 2
mtu = 1380
        """.trim()
    }
}
