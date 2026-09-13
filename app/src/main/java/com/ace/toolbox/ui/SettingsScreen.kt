package com.ace.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ace.toolbox.data.AppConfig
import com.ace.toolbox.data.SsaidGenerator
import com.ace.toolbox.ui.components.MiuiRow
import com.ace.toolbox.ui.components.MiuiSection

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config = remember { AppConfig(context) }
    val qqVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo("com.tencent.mobileqq", 0).versionName ?: "未知"
        }.getOrElse { "未安装" }
    }

    var clean by remember { mutableStateOf(config.cleanEnabled) }
    var compatibility by remember { mutableStateOf(config.compatibilityMode) }
    var ssaid by remember { mutableStateOf(config.ssaidEnabled) }
    var ssaidValue by remember { mutableStateOf(config.ssaidValue) }
    var ruleUrl by remember { mutableStateOf(config.ruleBaseUrl) }

    var showSsaid by remember { mutableStateOf(false) }
    var showRule by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp, bottom = 22.dp)
    ) {
        Column(Modifier.padding(horizontal = 26.dp, vertical = 12.dp)) {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "模块行为与 QQ 功能设置",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        MiuiSection("清理") {
            MiuiRow(
                Icons.Rounded.CleaningServices,
                "安全清理入口",
                "在 QQ / 微信设置页显示 ACE 工具箱",
                {
                    Switch(
                        clean,
                        {
                            clean = it
                            config.cleanEnabled = it
                        }
                    )
                }
            )
            HorizontalDivider(
                Modifier.padding(start = 58.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
            )
            MiuiRow(
                Icons.Rounded.Security,
                "兼容模式",
                if (compatibility) {
                    "已开启：推荐与 FunBox 等模块共存"
                } else {
                    "已关闭：尝试原生 Preference 注入"
                },
                {
                    Switch(
                        compatibility,
                        {
                            compatibility = it
                            config.compatibilityMode = it
                        }
                    )
                }
            )
        }

        MiuiSection("QQ 功能") {
            MiuiRow(
                Icons.Rounded.Fingerprint,
                "SSAID",
                if (ssaid) {
                    "已开启，仅作用于 QQ；修改后请重启 QQ"
                } else {
                    "默认关闭；关闭时不安装 ANDROID_ID Hook"
                },
                {
                    Switch(
                        ssaid,
                        {
                            ssaid = it
                            config.ssaidEnabled = it
                        }
                    )
                }
            )
            HorizontalDivider(
                Modifier.padding(start = 58.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
            )
            MiuiRow(
                Icons.Rounded.Edit,
                "SSAID 值",
                if (ssaidValue.isBlank()) "未配置" else ssaidValue,
                onClick = { showSsaid = true }
            )
            HorizontalDivider(
                Modifier.padding(start = 58.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
            )
            MiuiRow(
                Icons.Rounded.Casino,
                "随机生成 SSAID",
                "使用 SecureRandom 生成新的 16 位十六进制 ID",
                onClick = {
                    ssaidValue = SsaidGenerator.randomHex16()
                    config.ssaidValue = ssaidValue
                }
            )
        }

        MiuiSection("QQ 兼容诊断") {
            MiuiRow(
                Icons.Rounded.PhoneAndroid,
                "当前 QQ 版本",
                qqVersion
            )
            HorizontalDivider(
                Modifier.padding(start = 58.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
            )
            MiuiRow(
                Icons.Rounded.HealthAndSafety,
                "只读兼容探测",
                "QQ 启动后在 LSPosed 日志输出关键类存在性；不修改检测结果"
            )
            HorizontalDivider(
                Modifier.padding(start = 58.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
            )
            MiuiRow(
                Icons.Rounded.Rule,
                "9.3.50 参考适配",
                "参考 QQEnhancedBypass 的公开兼容目标，用于定位版本变化与安全降级"
            )
        }

        MiuiSection("兼容规则") {
            MiuiRow(
                Icons.Rounded.CloudDownload,
                "在线规则源",
                if (ruleUrl.isBlank()) "未配置，仅使用内置规则" else ruleUrl,
                onClick = { showRule = true }
            )
            HorizontalDivider(
                Modifier.padding(start = 58.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
            )
            MiuiRow(
                Icons.Rounded.Info,
                "规则格式",
                "{base}/qq/{version}.json 与 {base}/wechat/{version}.json"
            )
        }

        MiuiSection("关于") {
            MiuiRow(
                Icons.Rounded.Extension,
                "ACE 工具箱 2.1",
                "Modern LSPosed API 102 · minSdk 26"
            )
            MiuiRow(
                Icons.Rounded.Code,
                "参考",
                "FunBox：UI/交互；ssaid-qq：SSAID；QFun：QQ 清理路径与脚本架构；QQEnhancedBypass：QQ 兼容诊断结构"
            )
        }
    }

    if (showSsaid) {
        AlertDialog(
            onDismissRequest = { showSsaid = false },
            title = { Text("设置 QQ SSAID") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        ssaidValue,
                        {
                            ssaidValue = it
                                .filter { ch ->
                                    ch.isDigit() || ch.lowercaseChar() in 'a'..'f'
                                }
                                .take(16)
                        },
                        label = { Text("16 位十六进制") },
                        supportingText = {
                            Text("保存后请重启 QQ。错误格式时 Hook 自动回退系统值。")
                        }
                    )
                    OutlinedButton(
                        onClick = { ssaidValue = SsaidGenerator.randomHex16() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Casino, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("随机生成")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        config.ssaidValue = ssaidValue
                        showSsaid = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSsaid = false }) { Text("取消") }
            }
        )
    }

    if (showRule) {
        AlertDialog(
            onDismissRequest = { showRule = false },
            title = { Text("在线 Hook 规则源") },
            text = {
                OutlinedTextField(
                    ruleUrl,
                    { ruleUrl = it },
                    label = { Text("HTTPS 基础地址") },
                    supportingText = { Text("留空即禁用网络更新。") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        config.ruleBaseUrl = ruleUrl
                        showRule = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showRule = false }) { Text("取消") }
            }
        )
    }
}
