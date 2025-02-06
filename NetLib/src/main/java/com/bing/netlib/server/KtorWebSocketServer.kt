package com.bing.netlib.server

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

class KtorWebSocketServer private constructor() : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    companion object {
        val INSTANCE by lazy { KtorWebSocketServer() }
        private const val TAG = "KtorWebSocketServer"
    }
    private var server: NettyApplicationEngine? = null
    val messageFlow: MutableSharedFlow<String> = MutableSharedFlow()
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()


    @RequiresApi(Build.VERSION_CODES.O)
    fun startServer() {
        launch {
            if (server != null){
                stopServer()
            }
            server = embeddedServer(Netty, port = 28230, host = "127.0.0.1") {
                install(WebSockets) {
                    pingPeriod = 10.seconds // 10s ping一次
                    timeout = 20.seconds   // 20s 未收到pong超时
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }
                routing {
                    webSocket("/ws") {
                        sessions.add(this)
                        Log.d(TAG, "客户端已连接")
                        send(Frame.Text("欢迎连接到 WebSocket 服务器！"))
                        try {
                            // 处理客户端发送的消息
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val receivedText = frame.readText()
                                        Log.d(TAG, "收到客户端消息：$receivedText")
                                        // todo 改成messageFlow向client发送
                                        send(Frame.Text("echo: $receivedText"))
                                        messageFlow.emit(receivedText) // 发送消息到 Flow
                                    }
                                    else -> {
                                        Log.d(TAG, "Unsupported frame type")
                                    }
                                }
                            }
                        } catch (e: ClosedReceiveChannelException) {
                            // 客户端主动断开连接
                            Log.d(TAG, "客户端主动断开连接：${e}")
                        } catch (e: Exception) {
                            // 处理其他异常
                            Log.e(TAG, "处理 WebSocket 消息时发生异常: ${e.message}")
                        } finally {
                            sessions.remove(this)
                            Log.d(TAG, "客户端已断开连接")
                        }
                    }
                }
            }.start(wait = true) as NettyApplicationEngine
        }
    }

    /**
     * 停止服务器
     */
    fun stopServer() {
//        sendJob?.cancel()
        server?.stop(1000, 2000)
        Log.d(TAG, "服务器已停止")
    }
}