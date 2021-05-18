package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.ClientSyncManager
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

@Entity
@SyncableEntity(tableId = PersonGroup.TABLE_ID,
    notifyOnUpdate = ["""
        SELECT DISTINCT DeviceSession.dsDeviceId AS deviceId, 
               ${PersonGroup.TABLE_ID} AS tableId 
          FROM ChangeLog
               JOIN PersonGroup 
                    ON ChangeLog.chTableId = ${PersonGroup.TABLE_ID} 
                           AND ChangeLog.chEntityPk = PersonGroup.groupUid
               JOIN PersonGroupMember 
                    ON PersonGroupMember.groupMemberGroupUid = PersonGroup.groupUid
               JOIN Person
                    ON PersonGroupMember.groupMemberPersonUid = Person.personUid
               ${Person.JOIN_FROM_PERSON_TO_DEVICESESSION_VIA_SCOPEDGRANT_PT1}
                    ${Role.PERMISSION_PERSON_SELECT}
                    ${Person.JOIN_FROM_PERSON_TO_DEVICESESSION_VIA_SCOPEDGRANT_PT2}     
        """],
    syncFindAllQuery = """
        SELECT PersonGroup.*
          FROM DeviceSession
               JOIN PersonGroupMember
                    ON DeviceSession.dsPersonUid = PersonGroupMember.groupMemberPersonUid
               ${Person.JOIN_FROM_PERSONGROUPMEMBER_TO_PERSON_VIA_SCOPEDGRANT_PT1}
                    ${Role.PERMISSION_PERSON_SELECT}
                    ${Person.JOIN_FROM_PERSONGROUPMEMBER_TO_PERSON_VIA_SCOPEDGRANT_PT2}
               JOIN PersonGroup 
                    ON PersonGroup.groupUid = PersonGroupMember.groupMemberGroupUid
         WHERE DeviceSession.dsDeviceId = :clientId
        """)
@Serializable
open class PersonGroup() {

    @PrimaryKey(autoGenerate = true)
    var groupUid: Long = 0

    @MasterChangeSeqNum
    var groupMasterCsn: Long = 0

    @LocalChangeSeqNum
    var groupLocalCsn: Long = 0

    @LastChangedBy
    var groupLastChangedBy: Int = 0

    @LastChangedTime
    var groupLct: Long = 0

    var groupName: String? = null

    var groupActive : Boolean = true

    /**
     *
     */
    var personGroupFlag: Int = 0

    constructor(name: String) : this() {
        this.groupName = name
    }

    companion object{

        const val TABLE_ID = 43

        const val PERSONGROUP_FLAG_DEFAULT = 0

        const val PERSONGROUP_FLAG_PERSONGROUP = 1

        const val PERSONGROUP_FLAG_PARENT_GROUP = 2

    }
}
