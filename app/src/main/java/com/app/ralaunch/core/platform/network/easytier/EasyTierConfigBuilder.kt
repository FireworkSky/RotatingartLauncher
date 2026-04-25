package com.app.ralaunch.core.platform.network.easytier

import com.app.ralaunch.core.logging.AppLog

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

        AppLog.d(TAG, "buildConfig: isHost=$isHost, withPortForward=$withPortForward, hostname=$hostname")

        val sb = StringBuilder()

        // 基本配置
        sb.appendLine("instance_name = \"$instanceName\"")
        sb.appendLine("hostname = \"$hostname\"")

        // IP 配置 - 房主和加入者都需要虚拟 IP
        if (isHost) {
            sb.appendLine("ipv4 = \"${EasyTierManager.HOST_IP_CIDR}\"")
            sb.appendLine("dhcp = false")

            // tcp/udp whitelist - 允许哪些端口被代理
            val tcpWhitelist = gamePorts.joinToString(", ") { "\"$it\"" }
            val udpWhitelist = gamePorts.joinToString(", ") { "\"$it\"" }
            sb.appendLine("tcp_whitelist = [$tcpWhitelist]")
            sb.appendLine("udp_whitelist = [$udpWhitelist]")
        } else {
            // 加入者也需要虚拟 IP，port_forward 需要源地址来建立虚拟网络连接
            // 使用随机后缀 (2-254) 避免与房主 (.1) 冲突
            val guestIpSuffix = (System.currentTimeMillis() % 253 + 2).toInt()
            sb.appendLine("ipv4 = \"10.126.126.$guestIpSuffix/24\"")
            sb.appendLine("dhcp = false")
        }

        sb.appendLine()

        // 网络身份
        sb.appendLine("[network_identity]")
        sb.appendLine("network_name = \"$networkName\"")
        sb.appendLine("network_secret = \"$networkSecret\"")
        sb.appendLine()

        // 房主：proxy_network 配置（使用标准 [[proxy_network]] 格式 + allow 字段）
        if (isHost) {
            sb.appendLine("[[proxy_network]]")
            sb.appendLine("cidr = \"10.126.126.0/24\"")
            sb.appendLine("allow = [\"tcp\", \"udp\", \"icmp\"]")
            sb.appendLine()
        }

        // 加入者：端口转发配置
        if (!isHost && withPortForward) {
            for (port in gamePorts) {
                sb.appendLine("[[port_forward]]")
                sb.appendLine("bind_addr = \"127.0.0.1:$port\"")
                sb.appendLine("dst_addr = \"${EasyTierManager.HOST_IP}:$port\"")
                sb.appendLine("proto = \"tcp\"")
                sb.appendLine()

                sb.appendLine("[[port_forward]]")
                sb.appendLine("bind_addr = \"127.0.0.1:$port\"")
                sb.appendLine("dst_addr = \"${EasyTierManager.HOST_IP}:$port\"")
                sb.appendLine("proto = \"udp\"")
                sb.appendLine()
            }
        }

        // 监听器
        sb.appendLine("listeners = [\"tcp://0.0.0.0:0\", \"udp://0.0.0.0:0\"]")
        sb.appendLine()

        // 公共服务器
        for (server in publicServers) {
            sb.appendLine("[[peer]]")
            sb.appendLine("uri = \"$server\"")
            sb.appendLine()
        }

        // Flags
        sb.appendLine("[flags]")
        sb.appendLine("no_tun = true")
        sb.appendLine("enable_encryption = true")
        sb.appendLine("enable_kcp_proxy = true")
        sb.appendLine("latency_first = true")
        sb.appendLine("multi_thread = true")
        sb.appendLine("data_compress_algo = 2")
        sb.appendLine("mtu = 1380")

        val config = sb.toString().trim()
        AppLog.d(TAG, "Generated config (${config.length} chars):\n$config")
        return config
    }
}
