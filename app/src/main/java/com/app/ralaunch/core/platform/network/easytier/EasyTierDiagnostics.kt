package com.app.ralaunch.core.platform.network.easytier

import android.content.Context
import android.util.Log
import com.app.ralaunch.R
import kotlinx.coroutines.*
import org.koin.java.KoinJavaComponent
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 诊断步骤状态
 */
enum class DiagStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}

/**
 * 单个诊断步骤结果
 */
data class DiagStepResult(
    val name: String,
    val status: DiagStepStatus = DiagStepStatus.PENDING,
    val message: String = "",
    val detail: String? = null
)

/**
 * EasyTier 诊断工具
 * 逐步测试整个联机链路，帮助定位问题
 */
object EasyTierDiagnostics {

    private const val TAG = "EasyTierDiag"
    private const val TEST_INSTANCE_NAME = "diag_test"
    private const val TEST_NETWORK_NAME = "diag_net_${Long.MAX_VALUE}"
    private const val TEST_NETWORK_SECRET = "diag_secret_test"

    /**
     * 运行完整诊断
     * @param onStepUpdate 每步结果更新回调（步骤索引, 结果）
     * @return 所有步骤的结果列表
     */
    suspend fun runFullDiagnostics(
        onStepUpdate: (Int, DiagStepResult) -> Unit
    ): List<DiagStepResult> = withContext(Dispatchers.IO) {
        val appContext: Context = KoinJavaComponent.get(Context::class.java)

        val stepNames = listOf(
            appContext.getString(R.string.control_editor_diag_jni_load),
            appContext.getString(R.string.control_editor_diag_config_generate),
            appContext.getString(R.string.control_editor_diag_config_parse),
            appContext.getString(R.string.control_editor_diag_instance_start),
            appContext.getString(R.string.control_editor_diag_network_collect),
            appContext.getString(R.string.control_editor_diag_port_check),
            appContext.getString(R.string.control_editor_diag_cleanup)
        )
        val results = stepNames.map { DiagStepResult(it) }.toMutableList()
        val unknownError = appContext.getString(R.string.common_unknown_error)

        fun exceptionText(error: Throwable): String {
            return appContext.getString(R.string.easytier_diag_exception, error.message ?: unknownError)
        }

        fun update(index: Int, result: DiagStepResult) {
            results[index] = result
            onStepUpdate(index, result)
        }

        // Step 0: JNI 库加载检查
        update(
            0,
            DiagStepResult(stepNames[0], DiagStepStatus.RUNNING, appContext.getString(R.string.easytier_diag_checking))
        )
        try {
            if (EasyTierJNI.isAvailable()) {
                val lastError = EasyTierJNI.getLastError()
                update(
                    0,
                    DiagStepResult(
                        stepNames[0],
                        DiagStepStatus.SUCCESS,
                        appContext.getString(R.string.easytier_diag_jni_loaded),
                        appContext.getString(R.string.easytier_diag_detail_last_error, lastError ?: unknownError)
                    )
                )
            } else {
                val error = EasyTierJNI.getLoadError() ?: unknownError
                update(
                    0,
                    DiagStepResult(
                        stepNames[0],
                        DiagStepStatus.FAILED,
                        appContext.getString(R.string.easytier_diag_jni_load_failed),
                        error
                    )
                )
                // 后续步骤全部跳过
                for (i in 1 until results.size) {
                    update(
                        i,
                        DiagStepResult(
                            results[i].name,
                            DiagStepStatus.SKIPPED,
                            appContext.getString(R.string.easytier_diag_skipped_jni_unavailable)
                        )
                    )
                }
                return@withContext results
            }
        } catch (e: Exception) {
            update(0, DiagStepResult(stepNames[0], DiagStepStatus.FAILED, exceptionText(e)))
            for (i in 1 until results.size) {
                update(
                    i,
                    DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, appContext.getString(R.string.easytier_diag_skipped))
                )
            }
            return@withContext results
        }

        // Step 1: 配置生成
        update(
            1,
            DiagStepResult(
                stepNames[1],
                DiagStepStatus.RUNNING,
                appContext.getString(R.string.easytier_diag_generating_configs)
            )
        )
        val hostConfig: String
        val guestConfig: String
        try {
            hostConfig = EasyTierConfigBuilder.buildConfig(
                instanceName = TEST_INSTANCE_NAME,
                networkName = TEST_NETWORK_NAME,
                networkSecret = TEST_NETWORK_SECRET,
                isHost = true,
                withPortForward = true,
                gamePorts = listOf(EasyTierManager.TERRARIA_PORT),
                publicServers = listOf("tcp://public.easytier.cn:11010")
            )
            guestConfig = EasyTierConfigBuilder.buildConfig(
                instanceName = "${TEST_INSTANCE_NAME}_guest",
                networkName = TEST_NETWORK_NAME,
                networkSecret = TEST_NETWORK_SECRET,
                isHost = false,
                withPortForward = true,
                gamePorts = listOf(EasyTierManager.TERRARIA_PORT),
                publicServers = listOf("tcp://public.easytier.cn:11010")
            )
            val configPreview = appContext.getString(
                R.string.easytier_diag_detail_config_preview,
                hostConfig.length,
                hostConfig.take(500),
                guestConfig.length,
                guestConfig.take(500)
            )
            update(
                1,
                DiagStepResult(
                    stepNames[1],
                    DiagStepStatus.SUCCESS,
                    appContext.getString(R.string.easytier_diag_config_generated),
                    configPreview
                )
            )
        } catch (e: Exception) {
            update(
                1,
                DiagStepResult(
                    stepNames[1],
                    DiagStepStatus.FAILED,
                    appContext.getString(R.string.easytier_diag_config_generate_failed, e.message ?: unknownError)
                )
            )
            for (i in 2 until results.size) {
                update(
                    i,
                    DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, appContext.getString(R.string.easytier_diag_skipped))
                )
            }
            return@withContext results
        }

        // Step 2: 配置解析测试
        update(
            2,
            DiagStepResult(
                stepNames[2],
                DiagStepStatus.RUNNING,
                appContext.getString(R.string.easytier_diag_parsing_host_config)
            )
        )
        try {
            val hostParseResult = EasyTierJNI.parseConfig(hostConfig)
            val hostParseError = EasyTierJNI.getLastError()

            val guestParseResult = EasyTierJNI.parseConfig(guestConfig)
            val guestParseError = EasyTierJNI.getLastError()

            if (hostParseResult == 0 && guestParseResult == 0) {
                update(
                    2,
                    DiagStepResult(
                        stepNames[2],
                        DiagStepStatus.SUCCESS,
                        appContext.getString(R.string.easytier_diag_config_parse_success),
                        appContext.getString(
                            R.string.easytier_diag_detail_parse_result,
                            hostParseResult,
                            guestParseResult
                        )
                    )
                )
            } else {
                val hostErrorSuffix = hostParseError?.let {
                    appContext.getString(R.string.easytier_diag_detail_error_suffix, it)
                } ?: ""
                val guestErrorSuffix = guestParseError?.let {
                    appContext.getString(R.string.easytier_diag_detail_error_suffix, it)
                } ?: ""
                val detail = appContext.getString(
                    R.string.easytier_diag_detail_parse_failure,
                    hostParseResult,
                    hostErrorSuffix,
                    guestParseResult,
                    guestErrorSuffix
                )
                update(
                    2,
                    DiagStepResult(
                        stepNames[2],
                        DiagStepStatus.FAILED,
                        appContext.getString(R.string.easytier_diag_config_parse_failed),
                        detail
                    )
                )
                for (i in 3 until results.size) {
                    update(
                        i,
                        DiagStepResult(
                            results[i].name,
                            DiagStepStatus.SKIPPED,
                            appContext.getString(R.string.easytier_diag_skipped_config_parse_failed)
                        )
                    )
                }
                return@withContext results
            }
        } catch (e: Exception) {
            update(2, DiagStepResult(stepNames[2], DiagStepStatus.FAILED, exceptionText(e)))
            for (i in 3 until results.size) {
                update(
                    i,
                    DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, appContext.getString(R.string.easytier_diag_skipped))
                )
            }
            return@withContext results
        }

        // Step 3: 网络实例启动（使用 guest config 来测试端口转发）
        update(
            3,
            DiagStepResult(
                stepNames[3],
                DiagStepStatus.RUNNING,
                appContext.getString(R.string.easytier_diag_starting_guest_instance)
            )
        )
        try {
            // 先确保清理旧实例
            EasyTierJNI.stopAllInstances()
            delay(500)

            val runResult = EasyTierJNI.runNetworkInstance(guestConfig)
            val runError = EasyTierJNI.getLastError()

            if (runResult == 0) {
                update(
                    3,
                    DiagStepResult(
                        stepNames[3],
                        DiagStepStatus.SUCCESS,
                        appContext.getString(R.string.easytier_diag_instance_start_success),
                        appContext.getString(R.string.easytier_diag_detail_run_result, runResult)
                    )
                )
            } else {
                update(
                    3,
                    DiagStepResult(
                        stepNames[3],
                        DiagStepStatus.FAILED,
                        appContext.getString(R.string.easytier_diag_instance_start_failed),
                        appContext.getString(
                            R.string.easytier_diag_detail_run_failure,
                            runResult,
                            runError ?: unknownError
                        )
                    )
                )
                for (i in 4 until results.size) {
                    update(
                        i,
                        DiagStepResult(
                            results[i].name,
                            DiagStepStatus.SKIPPED,
                            appContext.getString(R.string.easytier_diag_skipped_instance_not_started)
                        )
                    )
                }
                return@withContext results
            }
        } catch (e: Exception) {
            update(3, DiagStepResult(stepNames[3], DiagStepStatus.FAILED, exceptionText(e)))
            for (i in 4 until results.size) {
                update(
                    i,
                    DiagStepResult(results[i].name, DiagStepStatus.SKIPPED, appContext.getString(R.string.easytier_diag_skipped))
                )
            }
            return@withContext results
        }

        // Step 4: 网络信息收集
        update(
            4,
            DiagStepResult(
                stepNames[4],
                DiagStepStatus.RUNNING,
                appContext.getString(R.string.easytier_diag_waiting_network_init)
            )
        )
        try {
            delay(2000) // 等待网络初始化
            val infosJson = EasyTierJNI.collectNetworkInfos()
            val infoLen = infosJson?.length ?: 0

            if (infosJson != null && infoLen > 50) {
                val parsed = EasyTierJsonParser.parseNetworkInfo(infosJson)
                val instanceCount = parsed?.size ?: 0
                val peerCount = parsed?.values?.sumOf { it.peers.size } ?: 0
                val runningCount = parsed?.values?.count { it.running } ?: 0

                update(
                    4,
                    DiagStepResult(
                        stepNames[4],
                        DiagStepStatus.SUCCESS,
                        appContext.getString(
                            R.string.easytier_diag_network_collect_success,
                            instanceCount,
                            runningCount,
                            peerCount
                        ),
                        appContext.getString(
                            R.string.easytier_diag_detail_json_preview,
                            infoLen,
                            infosJson.take(800)
                        )
                    )
                )
            } else {
                update(
                    4,
                    DiagStepResult(
                        stepNames[4],
                        DiagStepStatus.FAILED,
                        appContext.getString(R.string.easytier_diag_network_collect_empty),
                        appContext.getString(
                            R.string.easytier_diag_detail_json_empty,
                            infoLen,
                            infosJson ?: "null"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            update(4, DiagStepResult(stepNames[4], DiagStepStatus.FAILED, exceptionText(e)))
        }

        // Step 5: 端口监听检测
        update(
            5,
            DiagStepResult(
                stepNames[5],
                DiagStepStatus.RUNNING,
                appContext.getString(R.string.easytier_diag_port_checking)
            )
        )
        try {
            delay(1000) // 等待端口绑定
            val portListening = checkPortListening("127.0.0.1", EasyTierManager.TERRARIA_PORT)
            if (portListening) {
                update(
                    5,
                    DiagStepResult(
                        stepNames[5],
                        DiagStepStatus.SUCCESS,
                        appContext.getString(R.string.easytier_diag_port_listening),
                        appContext.getString(R.string.easytier_diag_port_listening_detail)
                    )
                )
            } else {
                update(
                    5,
                    DiagStepResult(
                        stepNames[5],
                        DiagStepStatus.FAILED,
                        appContext.getString(R.string.easytier_diag_port_not_listening),
                        appContext.getString(R.string.easytier_diag_port_not_listening_detail)
                    )
                )
            }
        } catch (e: Exception) {
            update(5, DiagStepResult(stepNames[5], DiagStepStatus.FAILED, exceptionText(e)))
        }

        // Step 6: 清理测试实例
        update(
            6,
            DiagStepResult(
                stepNames[6],
                DiagStepStatus.RUNNING,
                appContext.getString(R.string.easytier_diag_stopping_test_instance)
            )
        )
        try {
            EasyTierJNI.stopAllInstances()
            delay(500)
            update(
                6,
                DiagStepResult(
                    stepNames[6],
                    DiagStepStatus.SUCCESS,
                    appContext.getString(R.string.easytier_diag_cleanup_done)
                )
            )
        } catch (e: Exception) {
            update(
                6,
                DiagStepResult(
                    stepNames[6],
                    DiagStepStatus.FAILED,
                    appContext.getString(R.string.easytier_diag_cleanup_failed, e.message ?: unknownError)
                )
            )
        }

        Log.i(TAG, "Diagnostics complete. Results:")
        results.forEachIndexed { i, r ->
            Log.i(TAG, "  [$i] ${r.name}: ${r.status} - ${r.message}")
        }

        results
    }

    /**
     * 检查端口是否在监听
     */
    private fun checkPortListening(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2000)
                true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Port $host:$port not listening: ${e.message}")
            false
        }
    }
}
