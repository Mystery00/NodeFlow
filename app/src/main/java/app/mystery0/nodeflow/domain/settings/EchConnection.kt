package app.mystery0.nodeflow.domain.settings

import kotlinx.coroutines.flow.StateFlow

/** 设置页展示当前网络的探测结果。业务请求与手动重试共用探测状态。 */
interface EchConnection {
    val status: StateFlow<EchStatus>
    suspend fun check(force: Boolean = false)
}

enum class EchStatus { Idle, Checking, Ready, Unavailable }
