package com.bing.netlib.client

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 更多用例：https://square.github.io/okhttp/recipes/
 */
class OkhttpRequest {

    companion object{
        const val TAG = "OkhttpRequest"
    }

    val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 发起 SSE 请求
     * @param url 请求的 URL
     * @param requestBody 请求体（可为 null）
     * @param onFailure 失败时的处理函数
     * @param onResponse 成功接收到每一条 SSE 数据时的处理函数
     */
    inline fun doPostSSERequest(
        url: String,
        requestBody: String? = null,
        headers: Map<String, String> = emptyMap(),
        contentType: String? = null,
        crossinline onFailure: (e: IOException) -> Unit,
        crossinline onResponse: (response: Response) -> Unit
    ) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }
        // 设置请求体及 Content-Type
        if (requestBody != null) {
            val finalBody = if (contentType != null) {
                requestBody.toRequestBody(contentType.toMediaType())
            } else {
                requestBody.toRequestBody()
            }
            requestBuilder.post(finalBody)
        }

        val request = requestBuilder.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "SSE 响应失败, e:$e")
                onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.i(TAG,"SSE 响应失败, code=${response.code}")
                    onFailure(IOException("Request failed with code: ${response.code}"))
                    return
                }
                Log.i(TAG,"SSE 响应成功!")
                onResponse(response)
            }
        })
    }
}