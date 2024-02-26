package com.espoir.bumblebeecode.code

sealed class Message {
    data class Text(val value: String) : Message()
    class Bytes(val value: ByteArray) : Message() {
        operator fun component1(): ByteArray = value
    }
}