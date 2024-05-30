package com.espoir.bumblebeecode.code.internal

import com.espoir.bumblebeecode.code.internal.connection.Connection
import com.espoir.bumblebeecode.code.internal.servicemethod.ServiceMethodExecutor
import java.lang.reflect.Method

class Service(
    private val connection: Connection,
    private val serviceMethodExecutor: ServiceMethodExecutor,
) {
    fun startForever() = connection.startForever()

    fun execute(method: Method, args: Array<Any>) = serviceMethodExecutor.execute(method, args)

    class Factory(
        private val connectionFactory: Connection.Factory,
        private val serviceMethodExecutorFactory: ServiceMethodExecutor.Factory,
    ) {

        fun create(serviceInterface: Class<*>): Service {
            validateService(serviceInterface)
            val connection = connectionFactory.create()
            val serviceMethodExecutor = serviceMethodExecutorFactory.create(serviceInterface, connection)
            return Service(connection, serviceMethodExecutor)
        }

        private fun validateService(service: Class<*>) {
            require(service.isInterface) { "Service declarations must be interfaces." }
            require(service.interfaces.isEmpty()) { "Service interfaces must not extend other interfaces." }
        }
    }
}