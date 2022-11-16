package com.ustadmobile.core.viewmodel

import com.ustadmobile.core.generated.locale.MessageID
import com.ustadmobile.core.impl.UstadMobileSystemImpl
import com.ustadmobile.core.impl.nav.UstadNavController
import com.ustadmobile.core.impl.nav.UstadSavedStateHandle
import com.ustadmobile.core.util.ext.requireHttpPrefix
import com.ustadmobile.core.util.ext.requirePostfix
import com.ustadmobile.core.util.ext.verifySite
import com.ustadmobile.core.view.Login2View
import com.ustadmobile.core.view.UstadView
import com.ustadmobile.core.view.UstadView.Companion.ARG_SITE
import io.ktor.client.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.instance

data class SiteEnterLinkUiState(
    val siteLink: String = "",
    val validLink: Boolean = false,
    val progressVisible: Boolean = false,
    val linkError: String? = null,
    val fieldsEnabled: Boolean = true,
)

class SiteEnterLinkViewModel(
    di: DI,
    savedStateHandle: UstadSavedStateHandle
): UstadViewModel(di, savedStateHandle) {

    private val _uiState = MutableStateFlow(SiteEnterLinkUiState())

    val uiState: Flow<SiteEnterLinkUiState> = _uiState.asStateFlow()

    private val httpClient: HttpClient by instance()

    private val navController: UstadNavController by instance()

    private val json: Json by instance()

    private val impl: UstadMobileSystemImpl by instance()

    fun onClickNext() {
        viewModelScope.launch {
            try {
                val endpointUrl = _uiState.value.siteLink.requireHttpPrefix()
                    .requirePostfix("/")
                val site = httpClient.verifySite(endpointUrl)
                val args = mutableMapOf(
                    UstadView.ARG_SERVER_URL to endpointUrl,
                    ARG_SITE to json.encodeToString(site),
                )
                ARGS_TO_PASS_THROUGH.forEach { argName ->
                    savedStateHandle.get<String>(argName)?.also { argValue ->
                        args[argName] = argValue
                    }
                }

                _uiState.update { previous ->
                    previous.copy(validLink =  true, linkError = null)
                }

                navController.navigate(Login2View.VIEW_NAME, args)
            }catch(e: Exception) {
                _uiState.update { previous ->
                    previous.copy(
                        validLink = false,
                        linkError = impl.getString(MessageID.invalid_link)
                    )
                }
            }
        }
    }

    fun onSiteLinkUpdated(siteLink: String) {
        _uiState.update { previous ->
            previous.copy(siteLink = siteLink)
        }
    }

    companion object {

        val ARGS_TO_PASS_THROUGH = listOf(UstadView.ARG_NEXT, UstadView.ARG_INTENT_MESSAGE)

    }

}
