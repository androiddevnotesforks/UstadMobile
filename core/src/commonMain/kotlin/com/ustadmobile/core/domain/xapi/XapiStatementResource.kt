package com.ustadmobile.core.domain.xapi

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuidFrom
import com.ustadmobile.core.account.Endpoint
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.core.domain.xapi.model.Actor
import com.ustadmobile.core.domain.xapi.model.Statement
import com.ustadmobile.core.domain.xapi.model.toEntities
import com.ustadmobile.core.domain.xxhash.XXHasher
import com.ustadmobile.core.util.uuid.randomUuidAsString
import com.ustadmobile.door.ext.withDoorTransactionAsync
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 *
 */
class XapiStatementResource(
    db: UmAppDatabase,
    repo: UmAppDatabase?,
    private val xxHasher: XXHasher,
    private val endpoint: Endpoint,
    private val json: Json,
) {

    private val repoOrDb = repo ?: db

    enum class Format {
        EXACT, IDS, CANONICAL
    }

    data class StatementStoreResult(
        val statementUuids: List<Uuid>,
    )

    private suspend fun storeStatements(
        statements: List<Statement>,
        xapiSession: XapiSession,
    ): StatementStoreResult {
        val statementEntities = statements.map {stmt ->
            val timeNowStr = Clock.System.now().toString()

            //Set properties to be set by LRS as per
            // https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Data.md#24-statement-properties
            // https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Data.md#231-statement-immutability
            val exactStatement = stmt.copy(
                stored = timeNowStr,
                timestamp = stmt.timestamp ?: timeNowStr,
                id = xapiRequireValidUuidOrNullAsString(stmt.id) ?: randomUuidAsString(),
                authority = Actor(
                    account = Actor.Account(
                        name = xapiSession.accountUsername,
                        homePage = xapiSession.endpoint.url,
                    )
                )
            )

            exactStatement.toEntities(
                xxHasher = xxHasher,
                xapiSession = xapiSession,
                exactJson = json.encodeToString(Statement.serializer(), exactStatement)
            )
        }

        repoOrDb.withDoorTransactionAsync {
            repoOrDb.statementDao.insertOrIgnoreListAsync(statementEntities.map { it.statementEntity } )
            statementEntities.mapNotNull { it.agentEntity }.takeIf { it.isNotEmpty() }?.also {
                repoOrDb.agentDao.insertOrIgnoreListAsync(it)
            }
            repoOrDb.verbDao.insertOrIgnoreAsync(
                statementEntities.map { it.verbEntities.verbEntity }
            )
            repoOrDb.verbLangMapEntryDao.upsertList(
                statementEntities.flatMap { it.verbEntities.verbLangMapEntries }
            )
        }

        return StatementStoreResult(
            statementUuids = statementEntities.map {
                Uuid(it.statementEntity.statementIdHi, it.statementEntity.statementIdLo)
            }
        )
    }


    /**
     * Put Statement API roughly as per the xAPI spec here:
     *
     */
    suspend fun put(
        statement: Statement,
        statementIdParam: String,
        xapiSession: XapiSession,
    ): String {
        val storeResult = storeStatements(
            listOf(statement.copy(id = statementIdParam)), xapiSession
        )

        return storeResult.statementUuids.first().toString()
    }

    /**
     * Get Statement API roughly as per the xAPI spec here:
     * https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#213-get-statements
     *
     * NOTE: X-Experience-API-Consistent-Through header needs considered.
     */
    suspend fun get(
        xapiSession: XapiSession,
        statementId: String?,
        format: Format = Format.EXACT
    ): List<JsonObject> {
        val (statementIdHi, statementIdLo) = if(statementId != null){
            val uuid = uuidFrom(statementId)
            Pair(uuid.mostSignificantBits, uuid.leastSignificantBits)
        }else {
            Pair(0L, 0L)
        }

        val statements = repoOrDb.statementDao.getStatements(
            statementIdHi = statementIdHi,
            statementIdLo = statementIdLo,
        )

        return statements.mapNotNull {
            it.fullStatement?.let {
                json.decodeFromString(JsonObject.serializer(), it)
            }
        }
    }
}