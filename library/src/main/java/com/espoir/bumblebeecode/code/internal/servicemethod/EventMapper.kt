package com.espoir.bumblebeecode.code.internal.servicemethod

import com.espoir.bumblebeecode.code.internal.connection.MachineState
import com.espoir.bumblebeecode.code.Message
import com.espoir.bumblebeecode.code.WebSocket
import com.espoir.bumblebeecode.code.internal.connection.MachineEvent
import com.espoir.bumblebeecode.code.utils.getParameterUpperBound
import com.espoir.bumblebeecode.code.utils.getRawType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 用来区分类型返回不同的类型数据
 */
sealed class EventMapper<T : Any> {

    abstract fun mapToData(event: MachineEvent): Flow<T>


    object NoOp : EventMapper<Any>() {
        override fun mapToData(event: MachineEvent): Flow<Any> = flow { emit(event) }
    }

    class FilterEventType<E : MachineEvent>(private val clazz: Class<E>) : EventMapper<E>() {
        override fun mapToData(event: MachineEvent): Flow<E> {
            if (clazz == Message::class.java || clazz.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                return flow { emit(event as E) }
            } else {
                return emptyFlow()
            }
        }
    }

    object ToWebSocketEvent : EventMapper<WebSocket.Event>() {
        private val filterEventType = FilterEventType(MachineEvent.OnWebSocket.Event::class.java)

        override fun mapToData(event: MachineEvent): Flow<WebSocket.Event> =
            filterEventType.mapToData(event).map { it.event }
    }

    object ToMachineState : EventMapper<MachineState>() {
        private val filterEventType = FilterEventType(MachineEvent.OnStateChange::class.java)

        override fun mapToData(event: MachineEvent): Flow<MachineState> {
            return filterEventType.mapToData(event).map { it.state }
        }
    }

    object ToRealMessage : EventMapper<Message>() {

        private val toWebSocketEvent = ToWebSocketEvent

        override fun mapToData(event: MachineEvent): Flow<Message> {
            return toWebSocketEvent.mapToData(event).filter {
                it is WebSocket.Event.OnMessageReceived
            }.map {
                return@map it as WebSocket.Event.OnMessageReceived
            }.map {
                return@map it.message
            }
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