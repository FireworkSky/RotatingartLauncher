package com.app.ralaunch.easytier

import android.util.Log
import org.json.JSONObject

/**
 * EasyTier 网络信息 JSON 解析器
 */
internal object EasyTierJsonParser {

    private const val TAG = "EasyTierParser"

    /**
     * 解析网络信息 JSON
     * 优先从 peer_route_pairs 获取，其次从 routes 获取
     */
    fun parseNetworkInfo(jsonString: String): Map<String, NetworkInstanceInfo>? {
        return try {
            val result = mutableMapOf<String, NetworkInstanceInfo>()
            val json = JSONObject(jsonString)
            val mapJson = json.optJSONObject("map") ?: return null

            for (key in mapJson.keys()) {
                val instanceJson = mapJson.getJSONObject(key)
                val running = instanceJson.optBoolean("running", false)
                val errorMsg = instanceJson.optString("error_msg", null)

                val myNodeInfo = instanceJson.optJSONObject("my_node_info")
                val virtualIpv4 = myNodeInfo?.optJSONObject("virtual_ipv4")
                val virtualIp = parseIpv4(virtualIpv4)
                val ipv4AddrStr = myNodeInfo?.optString("ipv4_addr", null)
                val myPeerId = myNodeInfo?.optLong("peer_id", 0L) ?: 0L

                Log.w("DEBUG_MULTIPLAYER", "RawMyNodeInfo: ${myNodeInfo?.toString()?.take(500)}")
                Log.w("DEBUG_MULTIPLAYER", "ParseInfo: virtualIp=$virtualIp, ipv4_addr=$ipv4AddrStr, peerId=$myPeerId")

                val peersList = mutableListOf<NetworkPeerInfo>()

                // 方案1: 优先从 peer_route_pairs 获取
                val peerRoutePairs = instanceJson.optJSONArray("peer_route_pairs")
                if (peerRoutePairs != null && peerRoutePairs.length() > 0) {
                    for (i in 0 until peerRoutePairs.length()) {
                        val pair = peerRoutePairs.getJSONObject(i)
                        val route = pair.optJSONObject("route") ?: continue
                        val peer = pair.optJSONObject("peer")

                        val peerId = route.optLong("peer_id", 0L)
                        if (peerId == myPeerId || peerId == 0L) continue

                        val hostname = route.optString("hostname", "")
                        val peerIpv4 = route.optJSONObject("ipv4_addr")
                        val peerIp = parseIpv4Addr(peerIpv4)

                        if (hostname.lowercase().contains("host")) {
                            Log.w("DEBUG_MULTIPLAYER", "HostRoute: ${route.toString().take(800)}")
                        }
                        val stunInfo = route.optJSONObject("stun_info")
                        val udpNatType = stunInfo?.optInt("udp_nat_type", 0) ?: 0
                        val natType = natTypeToString(udpNatType)

                        val featureFlag = route.optJSONObject("feature_flag")
                        val isPublicServer = (featureFlag?.optBoolean("is_public_server", false) ?: false) ||
                                hostname.startsWith("PublicServer_")

                        if (isPublicServer) {
                            Log.d(TAG, "Skipping public server: $hostname (peer_id=$peerId)")
                            continue
                        }

                        var tunnelProto = "unknown"
                        var latencyUs = 0L
                        var lossRate = 0f
                        if (peer != null) {
                            val connsArray = peer.optJSONArray("conns")
                            if (connsArray != null && connsArray.length() > 0) {
                                val conn = connsArray.getJSONObject(0)
                                val tunnel = conn.optJSONObject("tunnel")
                                tunnelProto = tunnel?.optString("tunnel_type", "unknown") ?: "unknown"
                                val stats = conn.optJSONObject("stats")
                                latencyUs = stats?.optLong("latency_us", 0L) ?: 0L
                                lossRate = conn.optDouble("loss_rate", 0.0).toFloat()
                            }
                        }

                        peersList.add(NetworkPeerInfo(
                            id = peerId.toString(),
                            virtualIp = peerIp ?: "N/A",
                            hostname = hostname.ifEmpty { "Peer-$peerId" },
                            latency = if (latencyUs > 0) (latencyUs / 1000).toInt() else null,
                            tunnelProto = tunnelProto,
                            natType = natType,
                            lossRate = lossRate
                        ))
                    }
                }

                // 方案2: 如果 peer_route_pairs 为空，从 routes 获取
                if (peersList.isEmpty()) {
                    val routes = instanceJson.optJSONArray("routes")
                    if (routes != null) {
                        for (i in 0 until routes.length()) {
                            val route = routes.getJSONObject(i)
                            val peerId = route.optLong("peer_id", 0L)

                            if (peerId == myPeerId || peerId == 0L) continue

                            val hostname = route.optString("hostname", "")
                            val peerIpv4 = route.optJSONObject("ipv4_addr")
                            val peerIp = parseIpv4Addr(peerIpv4)
                            val stunInfo = route.optJSONObject("stun_info")
                            val udpNatType = stunInfo?.optInt("udp_nat_type", 0) ?: 0
                            val natType = natTypeToString(udpNatType)

                            val featureFlag = route.optJSONObject("feature_flag")
                            val isPublicServer = (featureFlag?.optBoolean("is_public_server", false) ?: false) ||
                                    hostname.startsWith("PublicServer_")

                            if (isPublicServer) {
                                Log.d(TAG, "Skipping public server: $hostname (peer_id=$peerId)")
                                continue
                            }

                            peersList.add(NetworkPeerInfo(
                                id = peerId.toString(),
                                virtualIp = peerIp ?: "N/A",
                                hostname = hostname.ifEmpty { "Peer-$peerId" },
                                latency = null,
                                tunnelProto = "unknown",
                                natType = natType,
                                lossRate = 0f
                            ))
                        }
                    }
                }

                Log.d(TAG, "Parsed ${peersList.size} peers from network info")

                result[key] = NetworkInstanceInfo(
                    instanceName = key,
                    running = running,
                    virtualIp = virtualIp,
                    peers = peersList,
                    errorMsg = errorMsg
                )
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse network info JSON", e)
            null
        }
    }

    /**
     * NAT 类型转字符串
     */
    fun natTypeToString(natType: Int): String {
        return when (natType) {
            0 -> "Unknown"
            1 -> "OpenInternet"
            2 -> "NoPAT"
            3 -> "FullCone"
            4 -> "Restricted"
            5 -> "PortRestricted"
            6 -> "Symmetric"
            7 -> "SymUdpFirewall"
            else -> "Unknown"
        }
    }

    /**
     * 解析 IPv4 地址 (Ipv4Inet 格式)
     */
    fun parseIpv4(ipv4Json: JSONObject?): String? {
        if (ipv4Json == null) return null
        val addressJson = ipv4Json.optJSONObject("address") ?: return null
        val addr = addressJson.optLong("addr", 0L)
        if (addr == 0L) return null

        val networkLength = ipv4Json.optInt("network_length", 24)
        val ip = String.format(
            "%d.%d.%d.%d",
            (addr shr 24) and 0xFF,
            (addr shr 16) and 0xFF,
            (addr shr 8) and 0xFF,
            addr and 0xFF
        )
        return "$ip/$networkLength"
    }

    /**
     * 解析 IPv4 地址 (Ipv4Inet 格式: {"address": {"addr": int}})
     */
    fun parseIpv4Addr(ipv4Json: JSONObject?): String? {
        if (ipv4Json == null) return null
        val addressObj = ipv4Json.optJSONObject("address") ?: return null
        val addr = addressObj.optLong("addr", 0L)
        if (addr == 0L) return null

        return String.format(
            "%d.%d.%d.%d",
            (addr shr 24) and 0xFF,
            (addr shr 16) and 0xFF,
            (addr shr 8) and 0xFF,
            addr and 0xFF
        )
    }
}
