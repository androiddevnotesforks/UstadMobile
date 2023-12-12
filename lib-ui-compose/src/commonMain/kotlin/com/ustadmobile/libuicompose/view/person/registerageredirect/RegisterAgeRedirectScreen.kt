package com.ustadmobile.libuicompose.view.person.registerageredirect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustadmobile.core.viewmodel.RegisterAgeRedirectUiState
import dev.icerock.moko.resources.compose.stringResource
import com.ustadmobile.core.MR
import com.ustadmobile.core.viewmodel.RegisterAgeRedirectViewModel
import com.ustadmobile.libuicompose.util.ext.defaultItemPadding
import kotlinx.coroutines.Dispatchers
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle

@Composable
fun RegisterAgeRedirectScreen(
    viewModel: RegisterAgeRedirectViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(
        RegisterAgeRedirectUiState(), Dispatchers.Main.immediate)

    RegisterAgeRedirectScreen(
        uiState = uiState,
        onSetDate = viewModel::onSetDate,
        onClickNext = viewModel::onClickNext,
    )
}


@Composable
fun RegisterAgeRedirectScreen(
    uiState: RegisterAgeRedirectUiState = RegisterAgeRedirectUiState(),
    onSetDate: (Long) -> Unit = {},
    onClickNext: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    )  {

        Text(stringResource(MR.strings.what_is_your_date_of_birth))

        Spacer(modifier = Modifier.height(8.dp))

        RegisterAgeRedirectDatePicker(
            date = uiState.dateOfBirth,
            onSetDate = onSetDate,
            supportingText = {
                Text(uiState.dateOfBirthError ?: stringResource(MR.strings.required))
            },
            isError = uiState.dateOfBirthError != null,
            maxDate = uiState.maxDate,
            onDone = onClickNext,
        )

        Button(
            onClick = onClickNext,
            modifier = Modifier.fillMaxWidth().defaultItemPadding().testTag("next_button"),
        ) {
            Text(stringResource(MR.strings.next),)
        }
    }
}
