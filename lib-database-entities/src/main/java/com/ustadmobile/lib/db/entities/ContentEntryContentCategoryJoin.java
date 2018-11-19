package com.ustadmobile.lib.db.entities;

import com.ustadmobile.lib.database.annotation.UmEntity;
import com.ustadmobile.lib.database.annotation.UmPrimaryKey;
import com.ustadmobile.lib.database.annotation.UmSyncLocalChangeSeqNum;
import com.ustadmobile.lib.database.annotation.UmSyncMasterChangeSeqNum;

import static com.ustadmobile.lib.db.entities.ContentEntry.TABLE_ID;

/**
 * Join entity to link ContentEntry many:many with ContentCategory
 */
@UmEntity(tableId = TABLE_ID)
public class ContentEntryContentCategoryJoin {

    public static final int TABLE_ID = 3;

    @UmPrimaryKey(autoGenerateSyncable = true)
    private long ceccjUid;

    private long ceccjContentEntryUid;

    private long ceccjContentCategoryUid;

    @UmSyncLocalChangeSeqNum
    private long ceccjLocalChangeSeqNum;

    @UmSyncMasterChangeSeqNum
    private long ceccjMasterChangeSeqNum;

    public long getCeccjUid() {
        return ceccjUid;
    }

    public void setCeccjUid(long ceccjUid) {
        this.ceccjUid = ceccjUid;
    }

    public long getCeccjContentEntryUid() {
        return ceccjContentEntryUid;
    }

    public void setCeccjContentEntryUid(long ceccjContentEntryUid) {
        this.ceccjContentEntryUid = ceccjContentEntryUid;
    }

    public long getCeccjContentCategoryUid() {
        return ceccjContentCategoryUid;
    }

    public void setCeccjContentCategoryUid(long ceccjContentCategoryUid) {
        this.ceccjContentCategoryUid = ceccjContentCategoryUid;
    }

    public long getCeccjLocalChangeSeqNum() {
        return ceccjLocalChangeSeqNum;
    }

    public void setCeccjLocalChangeSeqNum(long ceccjLocalChangeSeqNum) {
        this.ceccjLocalChangeSeqNum = ceccjLocalChangeSeqNum;
    }

    public long getCeccjMasterChangeSeqNum() {
        return ceccjMasterChangeSeqNum;
    }

    public void setCeccjMasterChangeSeqNum(long ceccjMasterChangeSeqNum) {
        this.ceccjMasterChangeSeqNum = ceccjMasterChangeSeqNum;
    }
}
