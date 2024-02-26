package com.espoir.bumblebeecode.code.retry

/**
 * 线性策略
 * 断开后一秒重连1次，6次后休息10秒再重复
 */
class LinearBackoffStrategy(private val durationMillis: Long) : BackoffStrategy {
    override fun backoffDurationMillisAt(retryCount: Int): Long {
        if (retryCount > 0 && retryCount % 6 == 0) {
            return 10000
        }
        return durationMillis
    }
}