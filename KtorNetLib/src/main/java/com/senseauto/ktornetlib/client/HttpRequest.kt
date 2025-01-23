package com.senseauto.ktornetlib.client

import android.util.Log
import com.google.gson.GsonBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

object HttpRequest {
    const val TAG = "HttpRequest"

    val ktorClient = HttpClient(CIO) {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.i(TAG, message)
                }
            }
            // LogLevel.ALL会影响sse请求
            level = LogLevel.HEADERS
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20000 // 请求超时时间
            connectTimeoutMillis = 20000 // 连接超时时间
            socketTimeoutMillis = 20000 // 读写超时时间
        }
        install(SSE) {
            showCommentEvents()
            showRetryEvents()
        }
    }

    /**
     * 通用GET请求
     * @param url 请求地址
     * @param header 请求头
     * @return T? 期待返回的响应体类型
     * @param onError 自定义错误处理逻辑
     */
    suspend inline fun <reified T> doGetRequest(
        url: String,
        header: Map<String, String> = mutableMapOf(),
        gsonBuilder: GsonBuilder = GsonBuilder().setLenient(),
        onError: (Throwable) -> T? = { null }
    ) : T? {
        return runCatching {
            val responseBody = ktorClient.get(url) {
                headers {
                    header.forEach { (t, u) ->
                        append(t, u)
                    }
                }
            }.bodyAsText()
            Log.d(TAG, "doGetRequest Success: $responseBody")

            if (T::class == String::class)
                return responseBody as T

            val gson = gsonBuilder.create()
            val response = gson.fromJson(responseBody, T::class.java)
            response
        }.onFailure {
            Log.d(TAG, "doGetRequest Fail: ${it.message}")
            onError(it)
        }.getOrNull()
    }

    /**
     * 通用POST请求
     * @param url 请求地址
     * @param header 请求头
     * @param requestBody 请求体
     * @param contentType Content-Type
     * @param gsonBuilder 自定义GsonBuilder
     * @param onError 自定义错误处理逻辑
     * @return T? 期待返回的响应体类型
     */
    suspend inline fun <reified T> doPostRequest(
        url: String,
        header: Map<String, String> = mutableMapOf(),
        contentType: String? = null,
        requestBody: String,
        gsonBuilder: GsonBuilder = GsonBuilder().setLenient(),
        onError: (Throwable) -> T? = { null }
    ) : T? {
        return runCatching {
            val responseBody = ktorClient.post(url){
                headers {
                    header.forEach { (t, u) ->
                        append(t, u)
                    }
                }
                setBody(requestBody)
            }.bodyAsText()
            Log.d(TAG, "doPostRequest Success: $responseBody")

            if (T::class == String::class)
                return responseBody as T

            val gson = gsonBuilder.create()
            val response = gson.fromJson(responseBody, T::class.java)
            response
        }.onFailure {
            Log.d(TAG, "doPostRequest Fail: ${it.message}")
            onError(it)
        }.getOrNull()
    }


    /**
     * 通用POST SSE请求
     * @param url 请求地址
     * @param header 请求头
     * @param requestBody 请求体
     * @param contentType Content-Type
     * @param onResponse 自定义响应处理逻辑
     */
    suspend inline fun doPostSSERequest(
        url: String,
        header: Map<String, String> = emptyMap(),
        requestBody: Any?,
        crossinline onResponse: (RequestStatus, String?) -> Unit = { status, line -> }
    ) {
        runCatching {
            ktorClient.sse(
                request =  {
                    url(url)
                    method = HttpMethod.Post // 指定为 POST 方法
                    headers {
                        header.forEach { (t, u) ->
                            append(t, u)
                        }
                    }
                    contentType(ContentType.Application.Json) // 设置请求体格式
                    setBody(requestBody) // 设置请求体
                }
            ) {
                incoming.collect { event ->
                    onResponse(SUCCESS, event.data)
                    Log.i(TAG, "Event from server, attribute: data-${event.data} event-${event.event} id-${event.id} retry-${event.retry} comments-${event.comments}")
                }
                onResponse(FINISH, "")
                Log.e(TAG, "-> SSE Post Request End")
            }
        }.onFailure {
            onResponse(ERROR, it.message.toString())
        }
    }
}


sealed class RequestStatus {
    fun isSuccess(): Boolean {
        return this is SUCCESS
    }

    fun isError(): Boolean {
        return this is ERROR
    }

    fun isFinish(): Boolean {
        return this is FINISH
    }
}

object FINISH : RequestStatus()
object SUCCESS : RequestStatus()
object ERROR : RequestStatus()