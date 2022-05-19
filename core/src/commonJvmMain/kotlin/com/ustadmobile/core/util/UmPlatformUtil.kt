package com.ustadmobile.core.util

import kotlinx.coroutines.*

actual object UmPlatformUtil {

    actual var isWeb: Boolean = false

    actual fun run(block:() ->Unit){
        block()
    }

    actual fun debug(){}

    actual fun log(content: Any) {}

    /**
     * Asynchronously run certain block on a specific platform
     */
    actual suspend fun runAsync(block: suspend CoroutineScope.() -> Unit) {}

    actual suspend fun runIfNotJs(block:suspend () ->Unit){
        block()
    }

}