package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.Chat.Companion.TABLE_ID
import kotlinx.serialization.Serializable

@Entity
@Serializable
@ReplicateEntity(tableId = TABLE_ID , tracker = ChatReplicate::class,
    priority = ReplicateEntity.HIGHEST_PRIORITY)
@Triggers(arrayOf(
    Trigger(
        name = "chat_remote_insert",
        order = Trigger.Order.INSTEAD_OF,
        on = Trigger.On.RECEIVEVIEW,
        events = [Trigger.Event.INSERT],
        sqlStatements = [
            """
                REPLACE INTO Chat(chatUid, chatStartDate, chatTitle, isChatGroup, chatLct)
                VALUES(NEW.chatUid, NEW.chatStartDate, NEW.chatTitle, NEW.isChatGroup, NEW.chatLct)
                /*psql ON CONFLICT (chatUid) DO UPDATE 
                SET chatStartDate = EXCLUDED.chatStartDate, chatTitle = EXCLUDED.chatTitle, 
                isChatGroup = EXCLUDED.isChatGroup, chatLct = EXCLUDED.chatLct 
                */
            """
        ]
    )
))
open class Chat() {

    @PrimaryKey(autoGenerate = true)
    var chatUid: Long = 0

    var chatStartDate: Long = 0

    var chatTitle: String? = null

    //TODO: Refactor to chatGroup
    var isChatGroup: Boolean = false

    constructor(title: String, isGroup: Boolean, startDate: Long):this(){
        chatTitle = title
        isChatGroup = isGroup
        chatStartDate = startDate
    }

    constructor(title: String, isGroup: Boolean):this(){
        chatTitle = title
        isChatGroup = isGroup
        chatStartDate = systemTimeInMillis()
    }

    @LastChangedTime
    @ReplicationVersionId
    var chatLct: Long = 0

    companion object{
        const val TABLE_ID = 127
    }
}