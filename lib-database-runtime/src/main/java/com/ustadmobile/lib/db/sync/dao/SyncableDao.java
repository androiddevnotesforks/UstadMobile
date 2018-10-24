package com.ustadmobile.lib.db.sync.dao;

import com.ustadmobile.lib.database.annotation.UmInsert;
import com.ustadmobile.lib.database.annotation.UmOnConflictStrategy;
import com.ustadmobile.lib.database.annotation.UmSyncIncoming;
import com.ustadmobile.lib.database.annotation.UmSyncOutgoing;
import com.ustadmobile.lib.db.sync.SyncResponse;

import java.util.List;

/**
 * A base interface for DAOs which support synchronization.
 *
 * @param <T> The Entity Type
 * @param <D> The DAO Type (generally the DAO class that is implementing SyncableDao)
 */
public interface SyncableDao<T, D> extends BaseDao<T> {

    /**
     * Sync with the other DAO. THe other DAO should be the same class
     *
     * @param otherDao
     * @param accountPersonUid
     */
    @UmSyncOutgoing
    void syncWith(D otherDao, long accountPersonUid);

    @UmSyncIncoming
    SyncResponse<T> handlingIncomingSync(List<T> incomingChanges, long fromLocalChangeSeqNum,
                                         long fromMasterChangeSeqNum, long userId);

    @UmInsert(onConflict = UmOnConflictStrategy.REPLACE)
    void replaceList(List<T> entities);

}
