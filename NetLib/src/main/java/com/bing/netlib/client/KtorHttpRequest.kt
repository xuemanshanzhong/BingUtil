package com.bing.netlib.client

import android.util.Log
import com.google.gson.GsonBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.content
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import java.io.EOFException
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

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
                contentType?.let { contentType(ContentType.parse(it)) }
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
        contentType: String? = null,
        crossinline onResponse: (RequestStatus, String?) -> Unit = { status, line -> }
    ) {
        runCatching {
            ktorClient.sse(
                request =  {
                    url(url)
                    method = HttpMethod.Post
                    headers {
                        header.forEach { (t, u) ->
                            append(t, u)
                        }
                    }
                    contentType?.let { contentType(ContentType.parse(it)) }
                    setBody(requestBody)
                }
            ) {
                incoming.collect { event ->
                    onResponse(SUCCESS, event.data)
                    Log.i(TAG, "Event from server, attribute: data-${event.data} event-${event.event} id-${event.id} retry-${event.retry} comments-${event.comments}")
                }
                onResponse(FINISH, "")
                Log.i(TAG, "-> SSE Post Request End")
            }
        }.onFailure {
            onResponse(ERROR, it.message.toString())
        }
    }

    /**
     * SSE multipart/form-data 类型POST请求
     * @param url 请求地址
     * @param header 请求头
     * @param textBody 文本请求体
     * @param imageFilePath 图片路径
     * @param contentType Content-Type
     * @param onResponse 响应
     */
    suspend inline fun doPostSSEMultiformRequest(
        url: String,
        header: Map<String, String> = emptyMap(),
        textBody: String,
        imageFilePath: String,
        contentType: String? = null,
        crossinline onResponse: (RequestStatus, String?) -> Unit = { status, line -> }
    ) {
        runCatching {
            val imgFile = File(imageFilePath)
            ktorClient.sse(
                request =  {
                    url(url)
                    method = HttpMethod.Post
                    headers {
                        header.forEach { (t, u) ->
                            append(t, u)
                        }
                    }
                    contentType?.let { contentType(ContentType.parse(it)) }
                    setBody(
                        MultiPartFormDataContent(
                        formData {
                            append("data", textBody, Headers.build {
                                append(HttpHeaders.ContentType, "application/json")
                            })
                            append("image", imgFile.readBytes(), Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"image\"; filename=${imgFile.name}")
                            })
                        },
//                        boundary = "WebAppBoundary"
                    )
                    )
                }
            ) {
                incoming.collect { event ->
                    if (event.data == "[DONE]"){
                        return@collect
                    }
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


    // todo 此形式的流失请求和sse请求的功能异同点待比较
    /**
     * 流式POST请求
     * @param url 请求地址
     * @param header 请求头
     * @param requestBody 请求体
     * @param contentType Content-Type
     * @param onResponse 响应
     */
    @OptIn(InternalAPI::class)
    suspend inline fun doPostStreamRequest(
        url: String,
        header: Map<String, String> = emptyMap(),
        requestBody: Any?,
        contentType: String? = null,
        crossinline onResponse: (RequestStatus, String) -> Unit = { status, line -> }
    ) {
        Log.d(TAG, "doPostStreamRequest start requestBody: $requestBody")
        runCatching {
            ktorClient.post(url) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header.forEach {
                        append(it.key, it.value)
                    }
                }
                contentType?.let { contentType(ContentType.parse(it)) }
                // 配置请求体
                setBody(requestBody)
            }.content.apply {
//                // 禁用缓冲，确保实时性
//                (this as? ByteReadChannel)?.apply {
//                    // 设置缓冲区大小为 1，减少缓冲
//                    this.discard(0)
//                }
                while (!isClosedForRead) {
                    val buffer = readUTF8Line()
                    if (buffer?.isNotEmpty() == true) {
                        Log.e(TAG, "doPostStreamRequest partial result: $buffer")
                        delay(30)
                        onResponse(SUCCESS, buffer)
                    }
                }
                onResponse(FINISH, "")
            }
        }.onFailure {
            if (it is CancellationException) {
                // 协程取消导致的错误不做处理
            } else if (it.cause is EOFException) {
                onResponse.invoke(ERROR, it.message.toString())
            } else {
                onResponse.invoke(ERROR, it.message.toString())
            }
            Log.d(TAG, "doPostStreamRequest Error: message is: ${it.message}, cause is ${it.cause}, it is $it")
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