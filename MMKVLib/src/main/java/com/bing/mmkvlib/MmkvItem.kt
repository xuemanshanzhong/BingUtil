package com.bing.mmkvlib

import android.util.Log
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 基于MMKV的数据存储对象，委托实现数据的getter和setter
 * ``` kotlin
 *    //新建对象
 *    var item  by  MmkvItem("key", 0)
 *    //setter
 *    item = 1
 *    //getter
 *    item
 * ```
 */
class MmkvItem<T>(private val key: String, private val def: T) : ReadWriteProperty<Any?, T> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return MMKVUtils.INSTANCE.decode(key, def).also {
            Log.v("MmkvItem", "getValue: key= $key value= $it")
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        MMKVUtils.INSTANCE.encode(key, value).also {
            Log.v("MmkvItem", "setValue: key= $key value= $value")
        }
    }
}