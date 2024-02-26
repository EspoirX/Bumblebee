package com.espoir.bumblebeecode.code

data class ShutdownReason(val code: Int, val reason: String) {
    companion object {
        private const val NORMAL_CLOSURE_STATUS_CODE = 1000
        private const val NORMAL_CLOSURE_REASON = "正常关闭"

        private const val ACTIVELY_CLOSURE_STATUS_CODE = 1001
        private const val ACTIVELY_CLOSURE_REASON = "主动关闭"

        @JvmField
        val DEFAULT = ShutdownReason(NORMAL_CLOSURE_STATUS_CODE, NORMAL_CLOSURE_REASON)

        @JvmField
        val ACTIVELY = ShutdownReason(ACTIVELY_CLOSURE_STATUS_CODE, ACTIVELY_CLOSURE_REASON)
    }
}