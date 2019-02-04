package com.ustadmobile.core.db.dao;

import com.ustadmobile.core.impl.UmCallback;
import com.ustadmobile.lib.database.annotation.UmDao;
import com.ustadmobile.lib.database.annotation.UmQuery;
import com.ustadmobile.lib.database.annotation.UmRepository;
import com.ustadmobile.lib.db.entities.ContentEntryFile;
import com.ustadmobile.lib.db.entities.ContentEntryFileWithFilePath;
import com.ustadmobile.lib.db.sync.dao.SyncableDao;

import java.util.List;

@UmDao(selectPermissionCondition = "(:accountPersonUid = :accountPersonUid)")
@UmRepository
public abstract class ContentEntryFileDao implements SyncableDao<ContentEntryFile, ContentEntryFileDao> {

    @UmQuery("SELECT ContentEntryFile.* FROM ContentEntryFile LEFT JOIN ContentEntryContentEntryFileJoin " +
            "ON ContentEntryFile.contentEntryFileUid = ContentEntryContentEntryFileJoin.cecefjContentEntryFileUid " +
            "WHERE ContentEntryContentEntryFileJoin.cecefjContentEntryUid = :contentEntryUid")
    public abstract void findFilesByContentEntryUid(long contentEntryUid, UmCallback<List<ContentEntryFile>> callback);

    @UmQuery("SELECT ContentEntryFile.* FROM ContentEntryFile LEFT JOIN ContentEntryContentEntryFileJoin " +
            "ON ContentEntryFile.contentEntryFileUid = ContentEntryContentEntryFileJoin.cecefjContentEntryFileUid " +
            "WHERE ContentEntryContentEntryFileJoin.cecefjContentEntryUid = :contentEntryUid")
    public abstract List<ContentEntryFile> findFilesByContentEntryUid(long contentEntryUid);

    @UmQuery("SELECT ContentEntryFile.* FROM ContentEntryFile " +
            "LEFT JOIN ContentEntryContentEntryFileJoin ON ContentEntryContentEntryFileJoin.cecefjContentEntryFileUid = ContentEntryFile.contentEntryFileUid " +
            "LEFT JOIN ContentEntry ON ContentEntryContentEntryFileJoin.cecefjContentEntryUid = ContentEntry.contentEntryUid " +
            "WHERE ContentEntry.publik")
    public abstract List<ContentEntryFile> getPublicContentEntryFiles();

    @UmQuery("SELECT ContentEntryFile.*, ContentEntryFileStatus.filePath  from ContentEntryFile " +
            "LEFT JOIN ContentEntryFileStatus ON ContentEntryFile.contentEntryFileUid = ContentEntryFileStatus.cefsContentEntryFileUid " +
            "WHERE ContentEntryFileStatus.filePath LIKE '%/khan/en/%'")
    public abstract List<ContentEntryFileWithFilePath> findKhanFiles();

    @UmQuery("UPDATE ContentEntryFile SET fileSize = :length, md5sum = :md5, mimeType = :mimeType  WHERE contentEntryFileUid = :cefsUid")
    public abstract void updateFiles(long length, String md5, String mimeType, long cefsUid);

    @UmQuery("UPDATE ContentEntryFile SET mimeType = :mimeType  WHERE contentEntryFileUid = :cefsUid")
    public abstract void updateMimeType(String mimeType, long cefsUid);
}
