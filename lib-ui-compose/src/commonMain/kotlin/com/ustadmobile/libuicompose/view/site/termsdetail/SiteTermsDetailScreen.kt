package com.ustadmobile.libuicompose.view.site.termsdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustadmobile.core.viewmodel.site.termsdetail.SiteTermsDetailUiState
import com.ustadmobile.core.viewmodel.site.termsdetail.SiteTermsDetailViewModel
import dev.icerock.moko.resources.compose.stringResource
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import com.ustadmobile.core.MR
import com.ustadmobile.libuicompose.components.UstadErrorText
import com.ustadmobile.libuicompose.util.ext.defaultItemPadding

@Composable
fun SiteTermsDetailScreen(
    viewModel: SiteTermsDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(SiteTermsDetailUiState())

    SiteTermsDetailScreen(
        uiState,
        onClickAccept = viewModel::onClickAccept,
    )
}

@Composable
fun SiteTermsDetailScreen(
    uiState: SiteTermsDetailUiState = SiteTermsDetailUiState(),
    onClickAccept: () -> Unit = { },
) {
//    val state = rememberWebViewStateWithHTMLData("")
//
//    val navigator = rememberWebViewNavigator()

    LaunchedEffect(uiState.siteTerms?.termsHtml) {
        //navigator.loadHtmlWorkaround(uiState.siteTerms?.termsHtml ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    )  {
//        WebView(
//            state = state,
//            navigator = navigator,
//            modifier = Modifier.fillMaxSize().weight(1f)
//        )

        Spacer(Modifier.height(8.dp))

        if(uiState.acceptButtonVisible) {
            Button(
                onClick = onClickAccept,
                modifier = Modifier.defaultItemPadding().testTag("accept_button").fillMaxWidth(),
            ) {
                Text(stringResource(MR.strings.accept))
            }
        }

        uiState.error?.also { errorText ->
            UstadErrorText(error = errorText)
        }

    }
}
