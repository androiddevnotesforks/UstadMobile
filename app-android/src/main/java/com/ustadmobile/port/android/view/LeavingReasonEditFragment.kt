package com.ustadmobile.port.android.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ustadmobile.core.viewmodel.LeavingReasonEditUiState
import com.ustadmobile.core.viewmodel.LeavingReasonEditViewModel
import com.ustadmobile.lib.db.entities.LeavingReason
import com.ustadmobile.lib.db.entities.ext.shallowCopy
import com.ustadmobile.libuicompose.components.UstadInputFieldLayout
import com.ustadmobile.port.android.util.ext.defaultItemPadding
import com.ustadmobile.core.R as CR


@Composable
fun LeavingReasonEditScreen(
    uiState: LeavingReasonEditUiState,
    onLeavingReasonChanged: (LeavingReason?) -> Unit = {},
){
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UstadInputFieldLayout(
            modifier = Modifier.defaultItemPadding(),
            errorText = uiState.reasonTitleError,
        ) {
            OutlinedTextField(
                label = { Text(stringResource(CR.string.description)) },
                value = uiState.leavingReason?.leavingReasonTitle ?: "",
                onValueChange = {
                    onLeavingReasonChanged(uiState.leavingReason?.shallowCopy{
                        leavingReasonTitle = it
                    })
                }
            )
        }

    }
}

@Composable
fun LeavingReasonEditScreen(
    viewModel: LeavingReasonEditViewModel
) {
    val uiState: LeavingReasonEditUiState by viewModel.uiState.collectAsState(
        initial = LeavingReasonEditUiState()
    )

    LeavingReasonEditScreen(
        uiState = uiState,
        onLeavingReasonChanged = viewModel::onEntityChanged,
    )
}

@Composable
@Preview
fun LeavingReasonEditPreview(){
    LeavingReasonEditScreen(
        uiState = LeavingReasonEditUiState(
            leavingReason = LeavingReason().apply {
                leavingReasonTitle = "Leaving because of something..."
            }
        )
    )
}