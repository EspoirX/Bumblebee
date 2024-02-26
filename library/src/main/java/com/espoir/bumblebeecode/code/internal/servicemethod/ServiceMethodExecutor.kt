package com.espoir.bumblebeecode.code.internal.servicemethod

import android.os.Build
import com.espoir.bumblebeecode.code.internal.ConnectionService
import com.espoir.bumblebeecode.code.internal.ConnectionShutdown
import com.espoir.bumblebeecode.code.internal.Receive
import com.espoir.bumblebeecode.code.internal.Send
import com.espoir.bumblebeecode.code.internal.connection.Connection
import java.lang.reflect.Method

class ServiceMethodExecutor(private val serviceMethods: Map<Method, ServiceMethod>) {

    fun execute(method: Method, args: Array<Any>): Any {
        return when (val serviceMethod = checkNotNull(serviceMethods[method]) { "Service method not found" }) {
            is ServiceMethod.ConnectionService -> serviceMethod.execute()
            is ServiceMethod.ConnectionShutdown -> serviceMethod.execute()
            is ServiceMethod.Send -> serviceMethod.execute(args[0])
            is ServiceMethod.Receive -> serviceMethod.execute()
        }
    }

    class Factory(
        private val connectionMethodFactory: ServiceMethod.ConnectionService.Factory,
        private val shutdownMethodFactory: ServiceMethod.ConnectionShutdown.Factory,
        private val sendServiceMethodFactory: ServiceMethod.Send.Factory,
        private val receiveServiceMethodFactory: ServiceMethod.Receive.Factory,
    ) {
        fun create(serviceInterface: Class<*>, connection: Connection): ServiceMethodExecutor {
            val serviceMethods = serviceInterface.findServiceMethods(connection)
            return ServiceMethodExecutor(serviceMethods)
        }

        private fun Class<*>.findServiceMethods(connection: Connection): Map<Method, ServiceMethod> {
            val methods = declaredMethods.filterNot {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    it.isDefault
                } else {
                    false
                }
            }
            val serviceMethods = methods.map { it.toServiceMethod(connection) }
            return methods.zip(serviceMethods).toMap()
        }

        private fun Method.toServiceMethod(connection: Connection): ServiceMethod {
            val serviceMethodFactories = annotations.mapNotNull { it.findServiceMethodFactory() }
            require(serviceMethodFactories.size == 1) {
                "一个方法必须有且唯一一个方法注解: $this"
            }
            return serviceMethodFactories.first().create(connection, this)
        }

        private fun Annotation.findServiceMethodFactory(): ServiceMethod.Factory? =
            when (this) {
                is ConnectionService -> connectionMethodFactory
                is ConnectionShutdown -> shutdownMethodFactory
                is Send -> sendServiceMethodFactory
                is Receive -> receiveServiceMethodFactory
                else -> null
            }
    }
}