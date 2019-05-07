package com.ustadmobile.lib.db.entities

import com.ustadmobile.lib.database.annotation.UmEntity
import com.ustadmobile.lib.database.annotation.UmPrimaryKey
import com.ustadmobile.lib.database.annotation.UmSyncLastChangedBy
import com.ustadmobile.lib.database.annotation.UmSyncLocalChangeSeqNum
import com.ustadmobile.lib.database.annotation.UmSyncMasterChangeSeqNum

import com.ustadmobile.lib.db.entities.PersonPicture.Companion.TABLE_ID

@UmEntity(tableId = TABLE_ID)
class PersonPicture {

    @UmPrimaryKey(autoGenerateSyncable = true)
    var personPictureUid: Long = 0

    var personPicturePersonUid: Long = 0

    @UmSyncMasterChangeSeqNum
    var personPictureMasterCsn: Long = 0

    @UmSyncLocalChangeSeqNum
    var personPictureLocalCsn: Long = 0

    @UmSyncLastChangedBy
    var personPictureLastChangedBy: Int = 0

    var fileSize: Int = 0

    var picTimestamp: Int = 0

    var mimeType: String? = null

    companion object {

        const val TABLE_ID = 50
    }
}
