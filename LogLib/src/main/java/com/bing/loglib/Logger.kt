package com.bing.loglib

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.min

object Logger {

    enum class Type {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    private const val DOUBLE_DIVIDER = "────────────────────────────────────────────────────────"
    private const val SINGLE_DIVIDER = "┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄"
    private const val MAX_LENGTH: Int = 3800

    // 主tag
    private var mainTag = "Logger"
    // 副tag，可为空
    private var secondTag = ""

    // 是否显示在代码中的位置
    var traceCodeEnabled = false

    // 是否添加装饰
    var decorEnabled = false

    // 是否显示当前Thread信息
    var threadInfoEnabled = false

    fun init(firstTag: String) {
        this.mainTag = firstTag
    }

    fun init(firstTag: String, secondTag: String) {
        this.mainTag = firstTag
        this.secondTag = secondTag
    }

    @JvmOverloads   // 用于兼容kotlin函数的默认参数机制
    @JvmStatic  //如果不加 @JvmStatic，Java 调用时需要通过 Companion 访问
    fun v(msg: Any?, tag: String = this.mainTag+this.secondTag, occurred: Throwable? = Exception()) {
        print(msg.toString(), tag, Type.VERBOSE, occurred)
    }

    @JvmOverloads
    @JvmStatic
    fun d(msg: Any?, tag: String = this.mainTag+this.secondTag, occurred: Throwable? = Exception()) {
        print(msg.toString(), tag, Type.DEBUG, occurred)
    }

    @JvmOverloads
    @JvmStatic
    fun i(msg: Any?, tag: String = this.mainTag+this.secondTag, occurred: Throwable? = Exception()) {
        print(msg.toString(), tag, Type.INFO, occurred)
    }

    @JvmOverloads
    @JvmStatic
    fun w(msg: Any?, tag: String = this.mainTag+this.secondTag, occurred: Throwable? = Exception()) {
        print(msg.toString(), tag, Type.WARN, occurred)
    }

    @JvmOverloads
    @JvmStatic
    fun e(msg: Any?, tag: String = this.mainTag+this.secondTag, occurred: Throwable? = Exception()) {
        print(msg.toString(), tag, Type.ERROR, occurred)
    }

    @JvmOverloads
    @JvmStatic
    fun json(jsonMsg: String?, tag: String = this.mainTag+this.secondTag, type:Type = Type.DEBUG, occurred: Throwable? = Exception()) {
        if (jsonMsg == null)    return
        val obj = JSONTokener(jsonMsg).nextValue()
        val message = when(obj) {
            is JSONObject -> obj.toString(2)    //每层嵌套增加 2 个空格
            is JSONArray -> obj.toString(2)
            else -> obj.toString()
        }
        print(message, tag, type, occurred)

    }

    /**
     * 日志打印具体实现
     *
     */
    private fun print(msg: String? = null, tag: String = this.mainTag+this.secondTag, type: Type = Type.INFO, occurred: Throwable? = Exception(), printStackLayer:Int = 1) {
        if (msg == null)
            return

        if (decorEnabled) { logTopOrBottomBorder(tag, type) }

        if (threadInfoEnabled) {
            log(tag, "Thread: ${Thread.currentThread().name}", type)
            if (decorEnabled) { logMiddleBorder(tag, type) }
        }

        if (traceCodeEnabled && occurred != null) {
            occurred.stackTrace.getOrNull(printStackLayer)?.run {
                val message = "$className.$methodName   ($fileName:$lineNumber)"
                log(tag, message, type)
            }

            // 下面两行也可以实现，到时候看哪个好
//            val realStack: Array<StackTraceElement> = occurred.stackTrace.copyOfRange(0, printStackLayer)
//            realStack.forEach { log(tag, it.toString(), type) }

            if (decorEnabled) { logMiddleBorder(tag, type) }
        }

        // 判断log内容的长度，过长则分条打印
        val length = msg.length
        if (length > MAX_LENGTH) {
            synchronized(this) {
                var startIndex = 0
                var endIndex = MAX_LENGTH
                while (startIndex < length) {
                    endIndex = min(length, endIndex)
                    val substring = msg.substring(startIndex, endIndex)
                    log(tag, substring, type)
                    startIndex += MAX_LENGTH
                    endIndex += MAX_LENGTH
                }
            }
        } else {
            log(tag, msg, type)
        }

        if (decorEnabled) { logTopOrBottomBorder(tag, type) }
    }

    private fun log(tag: String, msg: String, type: Type) {
        when (type) {
            Type.VERBOSE -> Log.v(tag, msg)
            Type.DEBUG -> Log.d(tag, msg)
            Type.INFO -> Log.i(tag, msg)
            Type.WARN -> Log.w(tag, msg)
            Type.ERROR -> Log.e(tag, msg)
        }
    }

    private fun logMiddleBorder(tag: String, type: Type) {
        log(tag, SINGLE_DIVIDER + SINGLE_DIVIDER, type)
    }

    private fun logTopOrBottomBorder(tag: String, type: Type) {
        log(tag, DOUBLE_DIVIDER + DOUBLE_DIVIDER, type)
    }

}