package app.mystery0.nodeflow.domain.account

import kotlinx.coroutines.flow.Flow

/** 当前登录账号最近一次有效页面响应中的今日活跃度（0..100），未知时为 null。 */
interface AccountActivityRepository {
    val activityPercent: Flow<Float?>
}
