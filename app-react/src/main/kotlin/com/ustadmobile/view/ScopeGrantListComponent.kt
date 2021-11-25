package com.ustadmobile.view

import com.ustadmobile.core.util.ScopedGrantOneToManyHelper
import com.ustadmobile.lib.db.entities.ScopedGrantAndName
import react.RBuilder


class ScopeGrantListComponent(mProps: ListProps<ScopedGrantAndName>): UstadSimpleList<ListProps<ScopedGrantAndName>>(mProps){

    override fun RBuilder.renderListItem(item: ScopedGrantAndName) {
    /*    val showDelete = item.scopedGrant?.sgFlags?.hasFlag(ScopedGrant.FLAG_NO_DELETE) == false
        val permissionList = permissionListText(systemImpl,Clazz.TABLE_ID,
            item.scopedGrant?.sgPermissions ?: 0)
        if(showDelete){
            createItemWithIconTitleDescriptionAndIconBtn("admin_panel_settings",
                "delete",item.name, permissionList){
                props.listener.onClickDelete(item)
            }
        }else{
            createItemWithIconTitleAndDescription("admin_panel_settings",
                item.name, permissionList)
        }*/
    }

}


fun RBuilder.renderScopedGrants(listener: ScopedGrantOneToManyHelper,
                                scopes: List<ScopedGrantAndName>,
                                createNewItem: CreateNewItem = CreateNewItem(),
                                onEntryClicked: ((ScopedGrantAndName) -> Unit)? = null) = child(ScopeGrantListComponent::class) {
    attrs.entries = scopes
    attrs.onEntryClicked = onEntryClicked
    attrs.createNewItem = createNewItem
    attrs.listener = listener
}