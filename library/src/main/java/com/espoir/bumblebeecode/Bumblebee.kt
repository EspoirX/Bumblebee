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
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class Bumblebee internal constructor(private val serviceFactory: Service.Factory) {

    companion object {
        const val TAG = "Bumblebee"
        var log: BumblebeeLog = object : BumblebeeLog {
            override fun log(tag: String, msg: String) {
                Log.i(tag, msg)
            }
        }
    }

    fun <T> create(service: Class<T>): T? = implementService(service)

    inline fun <reified T : Any> create(): T? = create(T::class.java)

    private fun <T> implementService(serviceInterface: Class<T>): T? {
        val serviceInstance = serviceFactory.create(serviceInterface)
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
        private var backoffStrategy: BackoffStrategy = DEFAULT_RETRY_STRATEGY

        fun webSocketFactory(factory: WebSocket.Factory): Builder = apply { webSocketFactory = factory }

        fun backoffStrategy(backoffStrategy: BackoffStrategy): Builder =
            apply { this.backoffStrategy = backoffStrategy }

        fun log(bumblebeeLog: BumblebeeLog) = apply {
            log = bumblebeeLog
        }

        fun build(): Bumblebee = Bumblebee(createServiceFactory())

        private fun createServiceFactory(): Service.Factory = Service.Factory(
            createConnectionFactory(),
            createServiceMethodExecutorFactory()
        )

        private fun createConnectionFactory(): Connection.Factory =
            Connection.Factory(checkNotNull(webSocketFactory), backoffStrategy)

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
            private const val RETRY_BASE_DURATION = 5000L
            private val DEFAULT_RETRY_STRATEGY = LinearBackoffStrategy(RETRY_BASE_DURATION)
        }
    }
}