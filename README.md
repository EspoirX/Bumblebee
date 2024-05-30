# Bumblebee
## 基于 有限状态机 和 Flow 的 WebSocket 封装，Socket 部分 用 OkHttp 实现

# 特点：
1. 支持 Flow
2. Socket 的状态由状态机管理，自动处理重连等逻辑
3. Socket 部分默认由 OKHttp 实现
4. 使用简单，跟你使用 Retrofit 差不多

## 引入
```groovy
dependencies {
  implementation 'com.github.EspoirX:Bumblebee:TAG'
}
```
[![](https://jitpack.io/v/EspoirX/Bumblebee.svg)](https://jitpack.io/#EspoirX/Bumblebee)

## 使用
### 1. 首先创建一个服务接口
```kotlin

interface SocketService {
    //......
}
```

### 2. 创建服务实例
```kotlin
object SocketManager {
    private val mainScope = MainScope()
    private val url = "wss://xxx.xxx.xxx"

    private var socketService: SocketService? = null

    fun provideSocketService() {
        if (socketService != null) return
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .pingInterval(5000, TimeUnit.MILLISECONDS)
            .build()
        val header = arrayMapOf<String, String>()
        //...
        val socket = Bumblebee.Builder()
            .webSocketFactory(client.newWebSocketFactory(url, header))
            .log(object : BumblebeeLog {
                override fun log(tag: String, msg: String) {
                    Log.i(TAG, msg)
                }
            }).build()

        socketService = socket.create()
    }
}
```
通过 Bumblebee.Builder 构建实例，可配置 OkHttpClient，可以添加 Header 之类的参数。  

可以配置 BumblebeeLog 接口，自己实现 Log 打印，不配置的话默认使用 android.util.Log 打印。

然后通过 create 方法即可创建实例。

**在创建实例成功后，会自动打开连接。**

### 3. 连接，断开链接，发送，接收
Bumblebee 有四个注解，分别是：  
**@ConnectionService**   //主动打开 socket 链接  
**@ConnectionShutdown**  //主动断开 socket 链接  
**@Send**                //发送消息  
**@Receive**             //接收消息

使用也简单，在服务接口中添加对应的方法即可
```kotlin
interface SocketService {

    @ConnectionService
    fun connectionService(): Boolean

    @ConnectionShutdown
    fun connectionShutdown(): Boolean

    @Send
    fun sendMessage(message: String): Boolean

    @Receive
    fun observeText(): Flow<Message>

    @Receive
    fun observeState(): Flow<MachineState>
    
    //......
}
```
- 被 @ConnectionService 修饰的方法，不能有参数，需要有返回值 Boolean，调用即可主动打开 Socket 链接，前提是当前链接是关闭状态，不然不会重复打开。使用例子：
```kotlin
   socketService?.connectionService()
```

- 被 @ConnectionShutdown 修饰的方法，不能有参数，需要有返回值 Boolean，调用即可主动断开 Socket 链接。使用例子：
```kotlin
   socketService?.connectionShutdown()
```

- 被 @Send 修饰的方法，需要有参数，String 类型，需要有返回值 Boolean，调用即可主动给 Socket 服务端发送一条消息。
通常传一个 json 字符串。使用也是一样：
```kotlin
    socketService?.sendMessage("{...}")
```

- 被 @Receive 修饰的方法，代表接收一个消息，不能有参数，需要返回 Flow 类型，下面细说。

Bumblebee 将消息包装成了 Message ，里面包含 Text 和 Bytes 类型

```kotlin
sealed class Message {
    data class Text(val value: String) : Message()
    class Bytes(val value: ByteArray) : Message() {
        operator fun component1(): ByteArray = value
    }
}
```

当我们需要常规接收服务端推送过来的消息时，Flow 类型需要为 Message（如上例子的代码所示）。那么接收时可以根据自己需要看是
过滤 Text 类型还是 Bytes 类型。（通常都是 Text）
```kotlin
    fun observeText() = socketService?.observeText()
        ?.filter { it is Message.Text }
        ?.map { (it as Message.Text).value }
    //......
    SocketManager.observeText()?.map { message ->
       //... 
    }?.catch { it.printStackTrace() }?.launchIn(mainScope)
```
如此，就完成了消息接收。

除了 Message ，如果你想要接收 Socket 的状态信息，那么 Flow 类型可以写成 MachineState。MachineState 
是状态机的状态，也是代表着当前的链接状态：
```kotlin
sealed class MachineState {
    //重试状态
    data class WaitingToRetry internal constructor(val retryCount: Int, val retryInMillis: Long, ) : MachineState()
    //链接中状态
    data class Connecting internal constructor(internal val session: Session, val retryCount: Int, ) : MachineState()
    //已链接状态
    data class Connected internal constructor(internal val session: Session) : MachineState()
    //断开中状态
    object Disconnecting : MachineState()
    //已断开状态
    object Disconnected : MachineState()
}
```
比如我想监听当前是否已经链接成功可以这样写：
```kotlin
    fun observeState() = socketService?.observeState()
    //......
    var isConnectOpen : Boolean = false

    SocketManager.observeState()?.map {
        isConnectOpen = it is MachineState.Connected
    }?.catch { it.printStackTrace() }?.launchIn(mainScope)
```

除了 MachineState，你还可以通过 @Receive 监听 socket 当前回调了哪个方法（对应 OKHttp 的 WebSocketListener），只需要
将 Flow 类型改成 WebSocket.Event 即可：
```kotlin
    @Receive
    fun observeEvent(): Flow<WebSocket.Event>
```
WebSocket.Event 的代码就不贴了，都是对应相关的回调，自己点下看看就可以，想监听哪个就过滤哪个就行。

### 4. 重试逻辑
当 socket 关闭或者非主动关闭时，会通过状态机自动进入重试逻辑。重试频率间隔通过 BackoffStrategy 接口控制：
```kotlin
interface BackoffStrategy {
    fun backoffDurationMillisAt(retryCount: Int): Long
}
```
返回毫秒。  

**返回 -1 代表取消重连，转断开**

默认的重试逻辑是 5 秒重试一次，6 次后休息 10 秒再重复：
```kotlin
class LinearBackoffStrategy(private val durationMillis: Long) : BackoffStrategy {
    override fun backoffDurationMillisAt(retryCount: Int): Long {
        if (retryCount > 0 && retryCount % 6 == 0) {
            return 10000
        }
        return durationMillis
    }
}
```

如果你要自定义，则可以在初始化的时候通过 backoffStrategy 方法传入你的实现即可：
```kotlin
  val socket = Bumblebee.Builder()
        .webSocketFactory(client.newWebSocketFactory(url, header))
        .backoffStrategy( ... )
        .build()
```

就这些啦。
