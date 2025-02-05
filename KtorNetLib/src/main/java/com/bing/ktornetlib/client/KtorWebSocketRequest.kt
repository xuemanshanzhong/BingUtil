package com.bing.ktornetlib.client

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class KtorWebSocketRequest: CoroutineScope by CoroutineScope(Dispatchers.IO + SupervisorJob()) {
    // todo 是否需要单例模式
    private val TAG = "KtorWebSocketRequest"
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    private var webSocketJobs = mutableMapOf<String, Job>()
    private var webSocketSessions = mutableMapOf<String, DefaultClientWebSocketSession>()


    fun buildNewConnection(url: String, onResponse: (String) -> Unit = {}): String {
        val sessionID = UUID.randomUUID().toString()
        val job = launch {
            // todo client.webSocket与client.webSocketSession的区别
            val session = client.webSocketSession(urlString = url) {
                println("Connected to $sessionID")
            }
            webSocketSessions[sessionID] = session

            try {
                // 发送一条消息
                session.send(Frame.Text("""{"session_id":"$sessionID"}"""))
                
                for (message in session.incoming) {
                    when (message) {
                        is Frame.Text -> {
                            onResponse(message.readText())
                            Log.d(TAG, "Received Text from $sessionID: ${message.readText()}")
                        }

                        else -> {
                            Log.d(TAG, "Received from $sessionID: $message")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error on $sessionID message: ${e.message}")
            }
        }
        webSocketJobs[sessionID] = job
        return sessionID
    }
    
    /**
     * 关闭指定链接
     */
    fun disconnectSession(sessionID: String) {
        launch {
            webSocketSessions[sessionID]?.run {
                close(CloseReason(CloseReason.Codes.NORMAL, "Disconnect $sessionID"))
                Log.d(TAG, "Disconnected from $sessionID")
                webSocketSessions.remove(sessionID)
            } ?: {
                Log.e(TAG, "No active WebSocket connection found for $sessionID")
            }
            webSocketJobs[sessionID]?.cancel()
        }
    }
    
    /**
     * 关闭所有连接
     */
    fun closeAllSessions() {
        webSocketSessions.clear()
        webSocketJobs.values.forEach{ it.cancel() }
        client.close()
    }
}