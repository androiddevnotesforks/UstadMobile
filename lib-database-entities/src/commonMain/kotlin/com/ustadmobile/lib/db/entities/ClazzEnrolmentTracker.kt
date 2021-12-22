package com.ustadmobile.lib.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.ustadmobile.door.`annotation`.ReplicationDestinationNodeId
import com.ustadmobile.door.`annotation`.ReplicationEntityForeignKey
import com.ustadmobile.door.`annotation`.ReplicationTrackerProcessed
import com.ustadmobile.door.`annotation`.ReplicationVersionId
import kotlin.Boolean
import kotlin.Long
import kotlinx.serialization.Serializable

@Entity(
  primaryKeys = arrayOf("ceFk", "ceDestination"),
  indices = arrayOf(Index(value = arrayOf("ceDestination", "ceProcessed", "ceFk")))
)
@Serializable
public class ClazzEnrolmentTracker {
  @ReplicationEntityForeignKey
  public var ceFk: Long = 0

  @ReplicationVersionId
  public var ceVersionId: Long = 0

  @ReplicationDestinationNodeId
  public var ceDestination: Long = 0

  @ColumnInfo(defaultValue = "0")
  @ReplicationTrackerProcessed
  public var ceProcessed: Boolean = false
}
