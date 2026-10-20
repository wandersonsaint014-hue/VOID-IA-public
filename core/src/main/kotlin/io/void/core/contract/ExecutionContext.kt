package io.void.core.contract

interface ExecutionContext {
  val userId: String
  val taskId: String?
}
