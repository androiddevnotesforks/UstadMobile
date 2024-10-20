package com.ustadmobile.libuicompose.view.xapicontent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ustadmobile.core.viewmodel.xapicontent.XapiContentUiState
import com.ustadmobile.core.webview.UstadAbstractWebViewClient
import com.ustadmobile.libuicompose.components.webview.UstadWebView
import com.ustadmobile.libuicompose.components.webview.UstadWebViewNavigatorAndroid

@Composable
actual fun XapiContentScreen(
    uiState: XapiContentUiState
) {
    val webViewNavigator = remember {
        UstadWebViewNavigatorAndroid(UstadAbstractWebViewClient())
    }


    UstadWebView(
        navigator = webViewNavigator,
        modifier = Modifier.fillMaxSize()
            .testTag("xapi_webview")
    )

    LaunchedEffect(uiState.url) {
        uiState.url?.also {
            webViewNavigator.loadUrl(it)
        }
    }
}