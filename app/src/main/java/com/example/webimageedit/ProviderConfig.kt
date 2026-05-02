package com.example.webimageedit

import android.net.Uri

data class ProviderConfig(
    val id: String,
    val title: String,
    val shortLabel: String,
    val url: String,
    val allowedHosts: Set<String>
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
                allowedHosts = setOf("modelscope.cn")
            ),
            ProviderConfig(
                id = "gitee",
                title = "Gitee AI",
                shortLabel = "Gitee",
                url = "https://ai.gitee.com/serverless-api?model=FLUX.1-Kontext-dev",
                allowedHosts = setOf("gitee.com")
            ),
            ProviderConfig(
                id = "volcengine",
                title = "Volcengine",
                shortLabel = "火山",
                url = "https://www.volcengine.com/experience/ark?mode=vision&modelId=doubao-seedream-5-0-260128&tab=GenImage",
                allowedHosts = setOf("volcengine.com")
            ),
            ProviderConfig(
                id = "stepfun",
                title = "StepFun",
                shortLabel = "阶跃",
                url = "https://platform.stepfun.com/console-tools",
                allowedHosts = setOf("stepfun.com")
            ),
            ProviderConfig(
                id = "tencent",
                title = "Tencent Hunyuan",
                shortLabel = "混元",
                url = "https://aistudio.tencent.com/chat/HunyuanDefault?modelId=hunyuan-image-v3.0-v1.0.5&from=modelSquare&showAllTextModel=",
                allowedHosts = setOf("tencent.com", "qq.com", "weixin.qq.com")
            )
        )

        val default = all.first()

        fun byId(id: String?): ProviderConfig {
            return all.firstOrNull { it.id == id } ?: default
        }
    }
}
