package com.espoir.bumblebeecode.code.retry

/**
 * 控制重试频率
 */
interface BackoffStrategy {
    fun backoffDurationMillisAt(retryCount: Int): Long
}