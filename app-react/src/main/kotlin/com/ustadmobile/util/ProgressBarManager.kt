package com.ustadmobile.util

import kotlinx.browser.document
import org.w3c.dom.Element

/**
 * Manages progressbar visibility status
 */
class  ProgressBarManager {

    private var progressView: Element? = document.getElementById("um-progress")

    var progressBarVisibility: Boolean = false
    set(value) {
        val style = progressView?.asDynamic().style
        if(style != null){
            style.display = if(value) "block" else "none"
        }
       field = value
    }

    fun onDestroy(){
        progressView = null
    }
}