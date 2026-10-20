package io.void.core.contract

data class ModelRequestMeta(val taskId: String, val purpose: String, val privacyLabel: String?, val latencyBudgetMs: Long, val costBudgetCents: Long)
data class ModelSelection(val providerId: String, val modelId: String, val mode: String, val reason: String, val fallbackChain: List<String> = emptyList())

interface ModelRouter {
  fun selectModel(requestMeta: ModelRequestMeta): ModelSelection
}
