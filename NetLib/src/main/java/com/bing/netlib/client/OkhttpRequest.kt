package com.bing.netlib.client

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.nio.file.Files
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


    /**
     * 发起 SSE multipart/form-data请求
     * @param url 请求的 URL
     * @param textBody 请求体
     * @param imgFilePath 图片路径
     * @param onFailure 失败时的处理函数
     * @param onResponse 成功接收到每一条 SSE 数据时的处理函数
     */
    inline fun doPostSSEMultiformRequest(
        url: String,
        textBody: String,
        imgFilePath: String,
        headers: Map<String, String> = emptyMap(),
        contentType: String? = null,
        crossinline onFailure: (e: IOException) -> Unit,
        crossinline onResponse: (response: Response) -> Unit
    ) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }
        val imgFile = File(imgFilePath)
        val mediaType = "image/jpeg".toMediaType()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("data", textBody)
            .addFormDataPart("image", imgFile.name, imgFile.asRequestBody(mediaType))
            .build()
        requestBuilder.post(requestBody)

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
                Log.i(TAG,"SSE request success!")
                onResponse(response)
            }
        })
    }
}