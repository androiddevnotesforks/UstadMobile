package com.ustadmobile.core.db

import com.ustadmobile.door.IncomingReplicationEvent
import com.ustadmobile.door.IncomingReplicationListener
import com.ustadmobile.door.ext.replicationNotificationDispatcher
import com.ustadmobile.lib.db.entities.Person
import com.ustadmobile.lib.db.entities.PersonGroupMember
import com.ustadmobile.lib.db.entities.UserSession
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class RepIncomingListener(private val db: UmAppDatabase) : IncomingReplicationListener {

    override suspend fun onIncomingReplicationProcessed(incomingReplicationEvent: IncomingReplicationEvent) {
        if(incomingReplicationEvent.tableId == PersonGroupMember.TABLE_ID) {
            val jsonArray: JsonArray = incomingReplicationEvent.incomingReplicationData
            val personUids = jsonArray.mapNotNull {
                it.jsonObject.get("groupMemberPersonUid")?.jsonPrimitive?.longOrNull
            }

            val affectedNodes = personUids.chunked(100).flatMap {
                db.userSessionDao.findActiveNodeIdsByPersonUids(it)
            }.toSet()

            affectedNodes.forEach {
                db.replicationNotificationDispatcher.onNewDoorNode(it, "")
            }
        }

        if(incomingReplicationEvent.tableId == UserSession.TABLE_ID) {
            val jsonArray: JsonArray = incomingReplicationEvent.incomingReplicationData
            val affectedNodes = jsonArray.mapNotNull {
                it.jsonObject.get("usClientNodeId")?.jsonPrimitive?.longOrNull
            }

            affectedNodes.forEach {
                db.replicationNotificationDispatcher.onNewDoorNode(it, "")
            }
        }
    }
}