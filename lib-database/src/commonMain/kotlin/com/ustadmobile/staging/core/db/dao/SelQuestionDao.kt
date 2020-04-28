package com.ustadmobile.core.db.dao

import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.DoorLiveData
import com.ustadmobile.lib.database.annotation.UmDao
import com.ustadmobile.lib.database.annotation.UmRepository
import com.ustadmobile.lib.db.entities.SelQuestion
import com.ustadmobile.lib.db.entities.SelQuestionAndOptionRow

@UmDao(selectPermissionCondition = "(:accountPersonUid = :accountPersonUid)")
@UmRepository
@Dao
abstract class SelQuestionDao : BaseDao<SelQuestion>, OneToManyJoinDao<SelQuestion> {

    @Insert
    abstract override fun insert(entity: SelQuestion): Long

    @Update
    abstract override fun update(entity: SelQuestion)

    @Update
    abstract suspend fun updateAsync(entity: SelQuestion):Int

    @Query("SELECT * FROM SelQuestion WHERE selQuestionUid = :uid")
    abstract fun findByUid(uid: Long): SelQuestion?

    @Query("SELECT * FROM SelQuestion WHERE selQuestionUid = :uid")
    abstract suspend fun findByUidAsync(uid: Long) : SelQuestion?

    @Query("SELECT * FROM SelQuestion WHERE selQuestionUid = :uid")
    abstract fun findByUidLive(uid: Long): DoorLiveData<SelQuestion?>

    @Query("SELECT MAX(questionIndex) FROM SelQuestion")
    abstract suspend fun getMaxIndexAsync() : Int

    @Query("SELECT coalesce(MAX(questionIndex), 0) FROM SelQuestion WHERE " +
            "selQuestionSelQuestionSetUid = :questionSetUid " +
            " AND questionActive = 1")
    abstract suspend fun getMaxIndexByQuestionSetAsync(questionSetUid: Long): Int

    @Query("SELECT * FROM SelQuestion WHERE " +
            "selQuestionSelQuestionSetUid = :questionSetUid AND " +
            " CAST(questionActive AS INTEGER) = 1")
    abstract fun findAllActiveQuestionsInSetAsList(questionSetUid: Long): List<SelQuestion>


    @Query("""
        SELECT SelQuestion.* , SelQuestionOption.* FROM SelQuestion 
        LEFT JOIN SelQuestionOption ON 
            SelQuestionOption.selQuestionOptionQuestionUid = SelQuestion.selQuestionUid 
        WHERE 
        selQuestionSelQuestionSetUid = :questionSetUid AND 
        CAST(questionActive AS INTEGER) = 1
        """)
    abstract fun findAllActiveQuestionsWithOpensInSetAsListAsc(questionSetUid: Long): List<SelQuestionAndOptionRow>


    @Query("SELECT * FROM SelQuestion WHERE " +
            " selQuestionSelQuestionSetUid = :questionSetUid " +
            " AND questionIndex > :previousIndex ORDER BY questionIndex ASC LIMIT 1    ")
    abstract suspend fun findNextQuestionByQuestionSetUidAsync(questionSetUid: Long,
                                                       previousIndex: Int) : SelQuestion?

    @Query("SELECT COUNT(*) FROM SelQuestion WHERE" +
            " selQuestionSelQuestionSetUid = :questionSetUid AND " +
            " questionActive = 1")
    abstract fun findTotalNumberOfActiveQuestionsInAQuestionSet(questionSetUid: Long): Int

    @Query("UPDATE SelQuestion SET questionActive = :active WHERE selQuestionUid = :selQuestionUid ")
    abstract suspend fun updateActiveBySelQuestionUid(selQuestionUid: Long, active : Boolean)

    companion object {

        val SEL_QUESTION_TYPE_NOMINATION = 0
        val SEL_QUESTION_TYPE_MULTI_CHOICE = 1
        val SEL_QUESTION_TYPE_FREE_TEXT = 2
    }

    override suspend fun deactivateByUids(uidList: List<Long>) {
        uidList.forEach {
            updateActiveBySelQuestionUid(it, false)
        }
    }


}
