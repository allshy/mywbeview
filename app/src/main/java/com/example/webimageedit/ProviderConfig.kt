package com.example.webimageedit

import android.net.Uri

private const val DEFAULT_INJECTED_CSS = """
    input, textarea, select, button { font-size: 16px !important; }
    img, video, canvas { max-width: 100%; }
"""

data class ProviderConfig(
    val id: String,
    val title: String,
    val shortLabel: String,
    val url: String,
    val allowedHosts: Set<String>,
    val userAgentMode: UserAgentMode = UserAgentMode.MOBILE,
    val initialScale: Int = 100,
    val textZoom: Int = 100,
    val loadWithOverviewMode: Boolean = true,
    val injectedCss: String = DEFAULT_INJECTED_CSS
) {
    fun canOpenInside(targetUrl: String): Boolean {
        val host = Uri.parse(targetUrl).host?.lowercase() ?: return false
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    companion object {
        val all = listOf(
            ProviderConfig(
                id = "modelscope",
                title = "ModelScope",
                shortLabel = "魔搭",
                url = "https://www.modelscope.cn/aigc/imageGeneration?tab=advanced",
                allowedHosts = setOf("modelscope.cn"),
                userAgentMode = UserAgentMode.DESKTOP,
                initialScale = 82,
                textZoom = 115,
                injectedCss = DEFAULT_INJECTED_CSS + """
                    html, body, #root { min-height: 100vh !important; }
                    body { min-width: 960px !important; overflow-x: auto !important; }
                    .ant-modal-mask:empty,
                    .semi-modal-mask:empty,
                    .arco-modal-mask:empty {
                        display: none !important;
                    }
                """
            ),
            ProviderConfig(
                id = "gitee",
                title = "Gitee AI",
                shortLabel = "Gitee",
                url = "https://ai.gitee.com/serverless-api?model=FLUX.1-Kontext-dev",
                allowedHosts = setOf("gitee.com"),
                userAgentMode = UserAgentMode.MOBILE,
                initialScale = 100,
                textZoom = 110,
                injectedCss = DEFAULT_INJECTED_CSS + """
                    body { min-width: 0 !important; overflow-x: auto !important; }
                    input, textarea { max-width: 100% !important; }
                """
            ),
            ProviderConfig(
                id = "volcengine",
                title = "Volcengine",
                shortLabel = "火山",
                url = "https://www.volcengine.com/experience/ark?mode=vision&modelId=doubao-seedream-5-0-260128&tab=GenImage",
                allowedHosts = setOf("volcengine.com"),
                userAgentMode = UserAgentMode.MOBILE,
                initialScale = 100
            ),
            ProviderConfig(
                id = "stepfun",
                title = "StepFun",
                shortLabel = "阶跃",
                url = "https://platform.stepfun.com/console-tools",
                allowedHosts = setOf("stepfun.com"),
                userAgentMode = UserAgentMode.DESKTOP,
                initialScale = 75,
                injectedCss = DEFAULT_INJECTED_CSS + """
                    body { min-width: 1100px; }
                """
            ),
            ProviderConfig(
                id = "tencent",
                title = "Tencent Hunyuan",
                shortLabel = "混元",
                url = "https://aistudio.tencent.com/chat/HunyuanDefault?modelId=hunyuan-image-v3.0-v1.0.5&from=modelSquare&showAllTextModel=",
                allowedHosts = setOf("tencent.com", "qq.com", "weixin.qq.com"),
                userAgentMode = UserAgentMode.DESKTOP,
                initialScale = 78,
                textZoom = 110,
                injectedCss = DEFAULT_INJECTED_CSS + """
                    body { min-width: 1000px !important; overflow-x: auto !important; }
                """
            )
        )

        val default = all.first()

        fun byId(id: String?): ProviderConfig {
            return all.firstOrNull { it.id == id } ?: default
        }
    }
}

enum class UserAgentMode {
    MOBILE,
    DESKTOP
}
