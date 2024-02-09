package com.ustadmobile.libuicompose.view.contententry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ustadmobile.core.viewmodel.contententry.contentTypeStringResource
import com.ustadmobile.lib.db.composites.ContentEntryAndListDetail
import com.ustadmobile.libuicompose.components.UstadSelectableListItem
import com.ustadmobile.libuicompose.components.UstadSelectedIcon
import dev.icerock.moko.resources.compose.stringResource

@Composable
fun UstadContentEntryListItem(
    modifier: Modifier = Modifier,
    entry: ContentEntryAndListDetail?,
    onClick: () -> Unit = { },
    isSelected: Boolean = false,
    onSetSelected: (contentEntry: ContentEntryAndListDetail, selected: Boolean) -> Unit = { _, _ -> },
) {

    UstadSelectableListItem(
        modifier = modifier,
        isSelected = isSelected,
        onClick = onClick,
        onSetSelected = {
            entry?.also { onSetSelected(it, !isSelected) }
        },
        headlineContent = {
            Text(
                text = entry?.contentEntry?.title ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            if(isSelected) {
                UstadSelectedIcon()
            }else {
                val thumbnail: ImageVector = if (entry?.contentEntry?.leaf == true)
                    Icons.Outlined.Book
                else
                    Icons.Default.Folder
                Icon(
                    thumbnail,
                    contentDescription = "",
                    modifier = Modifier.size(40.dp).padding(4.dp),
                )
            }

        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if(!entry?.contentEntry?.description.isNullOrBlank()) {
                    Text((entry?.contentEntry?.description ?: ""), maxLines = 2)
                }

                Row {
                    entry?.contentEntry?.also { contentEntry ->
                        Icon(contentEntry.contentTypeImageVector,
                            contentDescription = "",
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        entry.contentEntry?.contentTypeStringResource?.also {
                            Text(stringResource(it))
                        }
                    }
                }
            }
        },
    )
}

