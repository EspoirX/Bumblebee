package com.espoir.bumblebeecode.code.internal.connection

/**
 * 状态机的状态
 */
sealed class MachineState {

    /**
     * 重试状态
     */
    data class WaitingToRetry internal constructor(
        internal val session: Session,
        val retryCount: Int,
        val retryInMillis: Long,
    ) : MachineState()

    /**
     * 链接中状态
     */
    data class Connecting internal constructor(
        internal val session: Session,
        val retryCount: Int,
    ) : MachineState()

    /**
     * 已链接状态
     */
    data class Connected internal constructor(
        internal val session: Session,
    ) : MachineState()

    /**
     * 已断开状态
     */
    data class Disconnected(internal val openConnect: Boolean = true) : MachineState()
}