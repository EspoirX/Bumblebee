package com.espoir.bumblebeecode

import android.util.Log
import com.espoir.bumblebeecode.code.WebSocket
import com.espoir.bumblebeecode.code.internal.Service
import com.espoir.bumblebeecode.code.internal.connection.Connection
import com.espoir.bumblebeecode.code.internal.servicemethod.EventMapper
import com.espoir.bumblebeecode.code.internal.servicemethod.ServiceMethod
import com.espoir.bumblebeecode.code.internal.servicemethod.ServiceMethodExecutor
import com.espoir.bumblebeecode.code.retry.BackoffStrategy
import com.espoir.bumblebeecode.code.retry.LinearBackoffStrategy
import kotlinx.coroutines.CoroutineScope
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class Bumblebee internal constructor(private val serviceFactory: Service.Factory, ) {

    companion object{
        const val TAG = "Bumblebee"
    }

    fun <T> create(scope: CoroutineScope, service: Class<T>): T? = implementService(scope, service)

    inline fun <reified T : Any> create(scope: CoroutineScope): T? = create(scope, T::class.java)

    private fun <T> implementService(scope: CoroutineScope, serviceInterface: Class<T>): T? {
        val serviceInstance = serviceFactory.create(scope, serviceInterface)
        serviceInstance.startForever()
        val proxy = Proxy.newProxyInstance(
            serviceInterface.classLoader,
            arrayOf(serviceInterface),
            createInvocationHandler(serviceInterface, serviceInstance)
        )
        return serviceInterface.cast(proxy)
    }

    private fun createInvocationHandler(serviceInterface: Class<*>, serviceInstance: Service): InvocationHandler =
        InvocationHandler { _, method, nullableArgs ->
            val args = nullableArgs ?: arrayOf()
            serviceInstance.execute(method, args)
        }


    class Builder {
        private var webSocketFactory: WebSocket.Factory? = null
        private var backoffStrategy: BackoffStrategy =
            com.espoir.bumblebeecode.Bumblebee.Builder.Companion.DEFAULT_RETRY_STRATEGY
        private var bumblebeeLog: com.espoir.bumblebeecode.BumblebeeLog =
            com.espoir.bumblebeecode.Bumblebee.Builder.Companion.DEFAULT_LOG

        fun webSocketFactory(factory: WebSocket.Factory): com.espoir.bumblebeecode.Bumblebee.Builder = apply { webSocketFactory = factory }

        fun backoffStrategy(backoffStrategy: BackoffStrategy): com.espoir.bumblebeecode.Bumblebee.Builder =
            apply { this.backoffStrategy = backoffStrategy }

        fun log(log: com.espoir.bumblebeecode.BumblebeeLog) = apply {
            bumblebeeLog = log
        }

        fun build(): com.espoir.bumblebeecode.Bumblebee = com.espoir.bumblebeecode.Bumblebee(createServiceFactory())

        private fun createServiceFactory(): Service.Factory = Service.Factory(
            createConnectionFactory(),
            createServiceMethodExecutorFactory()
        )

        private fun createConnectionFactory(): Connection.Factory =
            Connection.Factory(bumblebeeLog, checkNotNull(webSocketFactory), backoffStrategy)

        private fun createServiceMethodExecutorFactory(): ServiceMethodExecutor.Factory {
            val connectionMethodFactory = ServiceMethod.ConnectionService.Factory()
            val shutdownMethodFactory = ServiceMethod.ConnectionShutdown.Factory()

            val eventMapperFactory = EventMapper.Factory()

            val sendServiceMethodFactory = ServiceMethod.Send.Factory()
            val receiveServiceMethodFactory = ServiceMethod.Receive.Factory(eventMapperFactory)
            return ServiceMethodExecutor.Factory(
                connectionMethodFactory,
                shutdownMethodFactory,
                sendServiceMethodFactory,
                receiveServiceMethodFactory
            )
        }

        private companion object {
            private const val RETRY_BASE_DURATION = 1000L
            private val DEFAULT_RETRY_STRATEGY = LinearBackoffStrategy(com.espoir.bumblebeecode.Bumblebee.Builder.Companion.RETRY_BASE_DURATION)
            private val DEFAULT_LOG = object : com.espoir.bumblebeecode.BumblebeeLog {
                override fun log(tag: String, msg: String) {
                    Log.i(tag, msg)
                }
            }
        }
    }
}