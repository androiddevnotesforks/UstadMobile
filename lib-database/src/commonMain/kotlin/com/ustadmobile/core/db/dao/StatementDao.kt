package com.ustadmobile.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import com.ustadmobile.door.DoorQuery
import com.ustadmobile.lib.database.annotation.UmRepository
import com.ustadmobile.lib.db.entities.ContentEntry
import com.ustadmobile.lib.db.entities.StatementEntity
import kotlin.js.JsName

@Dao
@UmRepository
abstract class StatementDao : BaseDao<StatementEntity> {

    @JsName("insertListAsync")
    @Insert
    abstract suspend fun insertListAsync(entityList: List<StatementEntity>)

    @Query("SELECT * From StatementEntity")
    abstract fun all(): List<StatementEntity>

    @Query("SELECT * FROM StatementEntity WHERE statementId = :id LIMIT 1")
    abstract fun findByStatementId(id: String): StatementEntity?

    @Query("SELECT * FROM StatementEntity WHERE statementId IN (:id)")
    abstract fun findByStatementIdList(id: List<String>): List<StatementEntity>

    @JsName("getResults")
    @RawQuery
    abstract fun getResults(query: DoorQuery): List<ReportData>

    @JsName("getListResults")
    @RawQuery
    abstract fun getListResults(query: DoorQuery): List<ReportListData>

    data class ReportData(var yAxis: Float = 0f, var xAxis: String = "", var subgroup: String = "")

    data class ReportListData(var name: String = "", var verb: String = "", var result: Byte = 0.toByte(), var whenDate: Long = 0L)
}
