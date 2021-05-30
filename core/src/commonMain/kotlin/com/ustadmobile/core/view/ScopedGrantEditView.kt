package com.ustadmobile.core.view

import com.ustadmobile.core.model.BitmaskFlag
import com.ustadmobile.door.DoorLiveData
import com.ustadmobile.lib.db.entities.ScopedGrant


interface ScopedGrantEditView: UstadEditView<ScopedGrant> {

    var bitmaskList: DoorLiveData<List<BitmaskFlag>>?

    companion object {

        const val VIEW_NAME = "ScopedGrantEdit"

        const val ARG_GRANT_TO_GROUPUID = "grantToGroup"

        const val ARG_GRANT_TO_NAME = "grantToName"

        //Table id (int) for the permission list to display. Which permissions are shown
        // (e.g. whether or not school related permissions are shown) is based on this.
        const val ARG_PERMISSION_LIST = "permissionList"


    }

}