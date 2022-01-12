package com.ustadmobile.lib.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import com.ustadmobile.lib.db.entities.ClazzAssignment.Companion.TABLE_ID
import kotlinx.serialization.Serializable

@Entity
@ReplicateEntity(tableId = TABLE_ID, tracker = ClazzAssignmentReplicate::class)
@Triggers(arrayOf(
 Trigger(
     name = "clazzassignment_remote_insert",
     order = Trigger.Order.INSTEAD_OF,
     on = Trigger.On.RECEIVEVIEW,
     events = [Trigger.Event.INSERT],
     sqlStatements = [
         """REPLACE INTO ClazzAssignment(caUid, caTitle, caDescription, caDeadlineDate, caStartDate, caLateSubmissionType, caLateSubmissionPenalty, caGracePeriodDate, caActive, caClassCommentEnabled, caPrivateCommentsEnabled, caClazzUid, caLocalChangeSeqNum, caMasterChangeSeqNum, caLastChangedBy, caLct) 
         VALUES (NEW.caUid, NEW.caTitle, NEW.caDescription, NEW.caDeadlineDate, NEW.caStartDate, NEW.caLateSubmissionType, NEW.caLateSubmissionPenalty, NEW.caGracePeriodDate, NEW.caActive, NEW.caClassCommentEnabled, NEW.caPrivateCommentsEnabled, NEW.caClazzUid, NEW.caLocalChangeSeqNum, NEW.caMasterChangeSeqNum, NEW.caLastChangedBy, NEW.caLct) 
         /*psql ON CONFLICT (caUid) DO UPDATE 
         SET caTitle = EXCLUDED.caTitle, caDescription = EXCLUDED.caDescription, caDeadlineDate = EXCLUDED.caDeadlineDate, caStartDate = EXCLUDED.caStartDate, caLateSubmissionType = EXCLUDED.caLateSubmissionType, caLateSubmissionPenalty = EXCLUDED.caLateSubmissionPenalty, caGracePeriodDate = EXCLUDED.caGracePeriodDate, caActive = EXCLUDED.caActive, caClassCommentEnabled = EXCLUDED.caClassCommentEnabled, caPrivateCommentsEnabled = EXCLUDED.caPrivateCommentsEnabled, caClazzUid = EXCLUDED.caClazzUid, caLocalChangeSeqNum = EXCLUDED.caLocalChangeSeqNum, caMasterChangeSeqNum = EXCLUDED.caMasterChangeSeqNum, caLastChangedBy = EXCLUDED.caLastChangedBy, caLct = EXCLUDED.caLct
         */"""
     ]
 )
))
@Serializable
open class ClazzAssignment {

    @PrimaryKey(autoGenerate = true)
    var caUid: Long = 0

    var caTitle: String? = null

    var caDescription: String? = null

    var caAssignmentType: Int = 0

    var caDeadlineDate: Long = Long.MAX_VALUE

    var caStartDate: Long = 0

    var caLateSubmissionType: Int = 0

    var caLateSubmissionPenalty: Int = 0

    var caGracePeriodDate: Long = 0

    var caActive: Boolean = true

    var caClassCommentEnabled: Boolean = true

    var caPrivateCommentsEnabled: Boolean = false

    var caRequireFileSubmission: Boolean = true

    var caFileSubmissionWeight: Int = 0

    var caFileType: Int = 0

    var caSizeLimit: Int = 50

    var caNumberOfFiles: Int = 0

    var caEditAfterSubmissionType: Int = 0

    var caMarkingType: Int = 0

    var caMaxScore: Int = 0

    @ColumnInfo(index = true)
    var caClazzUid: Long = 0

    @LocalChangeSeqNum
    var caLocalChangeSeqNum: Long = 0

    @MasterChangeSeqNum
    var caMasterChangeSeqNum: Long = 0

    @LastChangedBy
    var caLastChangedBy: Int = 0

    @LastChangedTime
    @ReplicationVersionId
    var caLct: Long = 0

    companion object {

        const val TABLE_ID = 520

        const val ASSIGNMENT_LATE_SUBMISSION_REJECT = 1
        const val ASSIGNMENT_LATE_SUBMISSION_PENALTY = 2
        const val ASSIGNMENT_LATE_SUBMISSION_ACCEPT = 3

        const val ASSIGNMENT_TYPE_INDIVIDUAL = 1
        const val ASSIGNMENT_TYPE_GROUP = 2

        const val EDIT_AFTER_SUBMISSION_TYPE_ALLOWED_DEADLINE = 1
        const val EDIT_AFTER_SUBMISSION_TYPE_ALLOWED_GRACE = 2
        const val EDIT_AFTER_SUBMISSION_TYPE_NOT_ALLOWED = 3

        const val MARKING_TYPE_TEACHER = 1
        const val MARKING_TYPE_PEERS = 2

        const val FILE_TYPE_ANY = 0
        const val FILE_TYPE_DOC = 1
        const val FILE_TYPE_IMAGE = 2
        const val FILE_TYPE_VIDEO = 3
        const val FILE_TYPE_AUDIO = 4

    }


}