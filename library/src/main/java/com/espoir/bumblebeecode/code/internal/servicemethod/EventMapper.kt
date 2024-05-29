package com.espoir.bumblebeecode.code.internal.servicemethod

import com.espoir.bumblebeecode.Bumblebee.Companion.TAG
import com.espoir.bumblebeecode.Bumblebee.Companion.log
import com.espoir.bumblebeecode.code.Message
import com.espoir.bumblebeecode.code.WebSocket
import com.espoir.bumblebeecode.code.internal.connection.MachineEvent
import com.espoir.bumblebeecode.code.internal.connection.MachineState
import com.espoir.bumblebeecode.code.utils.getParameterUpperBound
import com.espoir.bumblebeecode.code.utils.getRawType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 用来区分类型返回不同的类型数据
 */
sealed class EventMapper<T : Any> {

    abstract fun mapToData(event: MachineEvent): T?


    object NoOp : EventMapper<Any>() {
        override fun mapToData(event: MachineEvent): Any = event
    }

    class FilterEventType<E : MachineEvent>(private val clazz: Class<E>) : EventMapper<E>() {
        override fun mapToData(event: MachineEvent): E? {
            if (clazz == Message::class.java || clazz.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                return event as E
            } else {
                return null
            }
        }
    }

    object ToWebSocketEvent : EventMapper<WebSocket.Event>() {
        private val filterEventType = FilterEventType(MachineEvent.OnWebSocket.Event::class.java)

        override fun mapToData(event: MachineEvent): WebSocket.Event? =
            filterEventType.mapToData(event)?.event
    }

    object ToMachineState : EventMapper<MachineState>() {
        private val filterEventType = FilterEventType(MachineEvent.OnStateChange::class.java)

        override fun mapToData(event: MachineEvent): MachineState? {
            return filterEventType.mapToData(event)?.let {
                it.state
            }
        }
    }

    object ToRealMessage : EventMapper<Message>() {

        private val toWebSocketEvent = ToWebSocketEvent

        override fun mapToData(event: MachineEvent): Message {
            val event = toWebSocketEvent.mapToData(event)
            if (event is WebSocket.Event.OnMessageReceived) {
                return event.message
            }
            return Message.NoOp
        }
    }

    class Factory {
        fun create(returnType: ParameterizedType, annotations: Array<Annotation>): EventMapper<*> {
            val receivingClazz = returnType.getFirstTypeArgument().getRawType()
            if (receivingClazz == MachineEvent::class.java) {
                return NoOp
            }
            if (MachineState::class.java == receivingClazz) {
                return ToMachineState
            }
            if (WebSocket.Event::class.java == receivingClazz) {
                return ToWebSocketEvent
            }
            if (Message::class.java == receivingClazz) {
                return ToRealMessage
            } else {
                throw IllegalArgumentException("type $receivingClazz is not supported")
            }
        }
    }

    companion object {
        private fun ParameterizedType.getFirstTypeArgument(): Type = getParameterUpperBound(0)
    }
}