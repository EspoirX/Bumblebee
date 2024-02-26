package com.espoir.bumblebeecode.code.internal.servicemethod

import com.espoir.bumblebeecode.code.Message
import com.espoir.bumblebeecode.code.internal.connection.Connection
import kotlinx.coroutines.flow.flatMapConcat
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

sealed class ServiceMethod {
    interface Factory {
        fun create(connection: Connection, method: Method): ServiceMethod
    }

    class ConnectionService(private val connection: Connection) : ServiceMethod() {

        fun execute(): Any {
            connection.startForever()
            return true
        }

        class Factory : ServiceMethod.Factory {

            override fun create(connection: Connection, method: Method): ServiceMethod {
                return ConnectionService(connection)
            }
        }
    }

    class ConnectionShutdown(private val connection: Connection) : ServiceMethod() {

        fun execute(): Any {
            connection.shutdown()
            return true
        }

        class Factory : ServiceMethod.Factory {

            override fun create(connection: Connection, method: Method): ServiceMethod {
                return ConnectionShutdown(connection)
            }
        }
    }

    class Send(private val connection: Connection) : ServiceMethod() {

        /**
         * 发消息
         */
        fun execute(data: Any): Any {
            val message = Message.Text(data.toString())
            return connection.send(message)
        }

        class Factory : ServiceMethod.Factory {

            override fun create(connection: Connection, method: Method): ServiceMethod {
                return Send(connection)
            }
        }
    }

    class Receive(
        private val eventMapper: EventMapper<*>,
        private val connection: Connection,
    ) : ServiceMethod() {

        fun execute(): Any {
            return connection.observeEvent()
                .flatMapConcat {
                    eventMapper.mapToData(it)
                }
        }

        class Factory(
            private val eventMapperFactory: EventMapper.Factory,
        ) : ServiceMethod.Factory {
            override fun create(connection: Connection, method: Method): Receive {
                val eventMapper = createEventMapper(method)
                return Receive(eventMapper, connection)
            }

            private fun createEventMapper(method: Method): EventMapper<*> =
                eventMapperFactory.create(method.genericReturnType as ParameterizedType, method.annotations)
        }
    }
}