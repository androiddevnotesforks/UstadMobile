package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.github.aakira.napier.Napier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseSyncRepository
import com.ustadmobile.door.SyncResult
import com.ustadmobile.door.ServerUpdateNotificationManager
import com.ustadmobile.door.annotation.EntityWithAttachment
import com.ustadmobile.door.annotation.PgOnConflict
import com.ustadmobile.door.annotation.SyncableEntity
import com.ustadmobile.door.annotation.LocalChangeSeqNum
import com.ustadmobile.door.annotation.MasterChangeSeqNum
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.door.entities.UpdateNotification
import com.ustadmobile.door.ext.DoorTag
import io.ktor.client.request.forms.InputProvider
import io.ktor.content.TextContent
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.io.File
import java.io.FileInputStream
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import com.ustadmobile.door.entities.UpdateNotificationSummary
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorJdbcKotlin.Companion.SUFFIX_JDBC_KT
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorJdbcKotlin.Companion.SUFFIX_JDBC_KT2
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER_LOCAL
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER_MASTER
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_ROUTE
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorRepository.Companion.SUFFIX_REPOSITORY2
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.SUFFIX_ENTITY_TRK
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.SUFFIX_SYNCDAO_ABSTRACT
import java.lang.IllegalArgumentException

/**
 * Generate a Tracker Entity for a Syncable Entity
 */
internal fun generateTrackerEntity(entityClass: TypeElement, processingEnv: ProcessingEnvironment) : TypeSpec {
    val pkFieldTypeName = getEntityPrimaryKey(entityClass)!!.asType().asTypeName()
    return TypeSpec.classBuilder("${entityClass.simpleName}_trk")
            .addProperties(listOf(
                    PropertySpec.builder(DbProcessorSync.TRACKER_PK_FIELDNAME, LONG)
                            .addAnnotation(AnnotationSpec.builder(PrimaryKey::class).addMember("autoGenerate = true").build())
                            .initializer(DbProcessorSync.TRACKER_PK_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME, pkFieldTypeName)
                            .initializer(DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_DESTID_FIELDNAME, INT)
                            .initializer(DbProcessorSync.TRACKER_DESTID_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME, INT)
                            .initializer(DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_RECEIVED_FIELDNAME, BOOLEAN)
                            .initializer(DbProcessorSync.TRACKER_RECEIVED_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_REQUESTID_FIELDNAME, INT)
                            .initializer(DbProcessorSync.TRACKER_REQUESTID_FIELDNAME)
                            .build(),
                    PropertySpec.builder(DbProcessorSync.TRACKER_TIMESTAMP_FIELDNAME, LONG)
                            .initializer(DbProcessorSync.TRACKER_TIMESTAMP_FIELDNAME)
                            .build()
            ))
            .addAnnotation(AnnotationSpec.builder(Entity::class)
                    .addMember("indices = [%T(value = [%S, %S, %S]),%T(value = [%S, %S], unique = true)]",
                            //Index for query speed linking the destid, entity pk, and the change seq num
                            Index::class,
                            DbProcessorSync.TRACKER_DESTID_FIELDNAME,
                            DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                            DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME,
                            //Unique index to enforce that there should be one tracker per entity pk / destination id combo
                            Index::class,
                            DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                            DbProcessorSync.TRACKER_DESTID_FIELDNAME)
                    .build())
            .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_PK_FIELDNAME, LONG)
                            .defaultValue("0L").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_ENTITY_PK_FIELDNAME,
                            pkFieldTypeName).defaultValue("0L").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_DESTID_FIELDNAME,
                            INT).defaultValue("0").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_CHANGESEQNUM_FIELDNAME,
                            INT).defaultValue("0").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_RECEIVED_FIELDNAME,
                            BOOLEAN).defaultValue("false").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_REQUESTID_FIELDNAME,
                            INT).defaultValue("0").build())
                    .addParameter(ParameterSpec.builder(DbProcessorSync.TRACKER_TIMESTAMP_FIELDNAME,
                            LONG).defaultValue("0L").build())
                    .build())
            .addModifiers(KModifier.DATA)
            .build()

}

/**
 * Where this TypeElement represents a Database, generate a TypeSpec for the SyncDao
 * with all the query functions required for syncable entities that are on the given
 * database.
 */
fun TypeElement.toSyncDaoTypeSpec(processingEnv: ProcessingEnvironment) : TypeSpec {
    val dbTypeEl = this
    return TypeSpec.classBuilder("$simpleName$SUFFIX_SYNCDAO_ABSTRACT")
            .addModifiers(KModifier.ABSTRACT)
            .addSuperinterface(ClassName(dbTypeEl.packageName, "I${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT"))
            .apply {
                dbTypeEl.allDbEntities(processingEnv).filter { it.hasAnnotation(SyncableEntity::class.java) }.forEach {entityType ->
                    addSyncDaoFunsForEntity(entityType, isOverride = true)
                }

                dbTypeEl.dbEnclosedDaos(processingEnv).filter { it.isDaoThatRequiresSyncHelper(processingEnv) }.forEach {
                    addSuperinterface(it.asClassNameWithSuffix("_SyncHelper"))
                }
            }
            .build()
}

/**
 * Where this TypeElement represents a database, generate a TypeSpec for the SyncDao
 * interface. This is really just here to ensure that all functions are overrides.
 */
fun TypeElement.toSyncDaoInterfaceTypeSpec(processingEnv: ProcessingEnvironment): TypeSpec {
    val dbTypeEl = this
    return TypeSpec.interfaceBuilder("I$simpleName$SUFFIX_SYNCDAO_ABSTRACT")
            .apply {
                dbTypeEl.allDbEntities(processingEnv).filter { it.hasAnnotation(SyncableEntity::class.java) }.forEach { entityType ->
                    addSyncDaoFunsForEntity(entityType, isOverride = false)
                }
            }
            .build()
}

/**
 * The functions required to the SyncDao for the given syncable entity. This will include queries
 * that find the remote changes, local changes, insert/replace the entity itself, and
 * insert/replace the trk entity, etc.
 */
fun TypeSpec.Builder.addSyncDaoFunsForEntity(entityType: TypeElement, isOverride: Boolean) : TypeSpec.Builder{
    val syncFindAllSql = entityType.getAnnotation(SyncableEntity::class.java)?.syncFindAllQuery
    val getAllSql = if(syncFindAllSql?.isNotEmpty() == true) {
        syncFindAllSql
    }else {
        "SELECT * FROM ${entityType.simpleName}"
    }

    addFunction(FunSpec.builder("_findMasterUnsent${entityType.simpleName}")
            .addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            .applyIf(isOverride) {
                addModifiers(KModifier.OVERRIDE)
            }.applyIf(getAllSql.contains(":clientId")) {
                addParameter("clientId", INT)
            }
            .returns(List::class.asClassName().parameterizedBy(entityType.asClassName()))
            .addAnnotation(AnnotationSpec.builder(Query::class)
                    .addMember("%S", getAllSql).build())
            .addAnnotation(AnnotationSpec.builder(Repository::class)
                    .addMember("methodType = ${Repository.METHOD_DELEGATE_TO_WEB}")
                    .build())
            .build())

    addFunction(FunSpec.builder("_replace${entityType.simpleName}")
            .addModifiers(KModifier.ABSTRACT)
            .applyIf(isOverride) {
                addModifiers(KModifier.OVERRIDE)
            }
            .addAnnotation(AnnotationSpec.builder(Insert::class)
                    //TODO: Add annotation to mark this as replace
                    .build())
            .addParameter("entities",
                    List::class.asClassName().parameterizedBy(entityType.asClassName()))
            .build())

    addFunction(FunSpec.builder("_replace${entityType.simpleName}_trk")
            .addModifiers(KModifier.ABSTRACT)
            .applyIf(isOverride) {
                addModifiers(KModifier.OVERRIDE)
            }
            .addAnnotation(AnnotationSpec.builder(Insert::class)
                    .build())
            .addParameter("trkEntities",
                    List::class.asClassName().parameterizedBy(entityType.asClassNameWithSuffix(SUFFIX_ENTITY_TRK)))
            .build())



    return this
}



class DbProcessorSync: AbstractDbProcessor() {

    /**
     * Add a JDBC implementation of the given TypeSpec to the FileSpec
     */
    fun FileSpec.Builder.addJdbcDaoImplType(daoTypeSpec: TypeSpec, daoClassName: ClassName): FileSpec.Builder{
        addType(TypeSpec.classBuilder(daoClassName.withSuffix(SUFFIX_JDBC_KT2))
                .superclass(daoClassName)
                .addAnnotation(AnnotationSpec.builder(Suppress::class)
                        .addMember("%S, %S, %S, %S", "LocalVariableName", "SpellCheckingInspection",
                                "ClassName", "PropertyName")
                        .build())
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("_db", DoorDatabase::class)
                        .build())
                .addProperty(PropertySpec.builder("_db", DoorDatabase::class)
                        .initializer("_db")
                        .build())
                .apply {
                    daoTypeSpec.funSpecs.filter { KModifier.ABSTRACT in it.modifiers }.forEach { daoFunSpec ->
                        when {
                            daoFunSpec.hasAnnotation(Query::class.java) ->
                                addJdbcQueryFun(daoFunSpec)
                            daoFunSpec.hasAnnotation(Insert::class.java) ->
                                addJdbcInsertFun(daoFunSpec)
                        }
                    }
                }
                .build())
        return this
    }

    fun TypeSpec.Builder.addJdbcQueryFun(queryFunSpec: FunSpec): TypeSpec.Builder {
        addFunction(queryFunSpec.toBuilder()
                .removeAbstractModifier()
                .addCode(generateQueryCodeBlock(queryFunSpec.returnType ?: UNIT,
                    queryFunSpec.parameters.map { it.name to it.type}.toMap(),
                        queryFunSpec.daoQuerySql(), null, null))
                .applyIf(queryFunSpec.hasReturnType) {
                    addCode("return _result\n")
                }
                .build())
        return this
    }

    fun TypeSpec.Builder.addJdbcInsertFun(insertFunSpec: FunSpec): TypeSpec.Builder {
        val entityType = insertFunSpec.parameters.first().type.unwrapListOrArrayComponentType() as ClassName
        val entityTypeSpec = entityType.asEntityTypeSpec(processingEnv)
                ?: throw IllegalArgumentException("no entity typ spec for ${insertFunSpec.name}")

        val isUpsert = insertFunSpec.getAnnotationSpec(Insert::class.java)
                ?.memberToString("onConflictStrategy")?.toInt() == OnConflictStrategy.REPLACE
        val pgOnConflict = insertFunSpec.getAnnotationSpec(PgOnConflict::class.java)?.memberToString()

        addFunction(insertFunSpec.toBuilder()
                .removeAbstractModifier()
                .addCode(generateInsertCodeBlock(insertFunSpec.parameters[0],
                    insertFunSpec.returnType ?: UNIT, entityTypeSpec, this,
                    isUpsert, insertFunSpec.hasReturnType, pgOnConflict ))
                .build())
        return this
    }


    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val dbs = roundEnv.getElementsAnnotatedWith(Database::class.java).map { it as TypeElement}

        //For all databases that are being compiled now, find those entities that require tracker entities
        // to be generated. Filter out any for which the entity was already generated.
        dbs.flatMap { entityTypesOnDb(it as TypeElement, processingEnv) }
                .filter { it.getAnnotation(SyncableEntity::class.java) != null
                        && processingEnv.elementUtils
                        .getTypeElement("${it.asClassName().packageName}.${it.simpleName}$TRACKER_SUFFIX") == null}
                .forEach {
                    val trackerFileSpec = FileSpec.builder(it.asClassName().packageName, "${it.simpleName}$TRACKER_SUFFIX")
                            .addType(generateTrackerEntity(it, processingEnv)).build()

                    writeFileSpecToOutputDirs(trackerFileSpec, AnnotationProcessorWrapper.OPTION_JVM_DIRS)
                    writeFileSpecToOutputDirs(trackerFileSpec, AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT,
                            useFilerAsDefault = false)
                }

        for(dbTypeEl in dbs) {
            val syncDaoType = dbTypeEl.toSyncDaoTypeSpec(processingEnv)
            val syncDaoClassName = dbTypeEl.asClassNameWithSuffix(SUFFIX_SYNCDAO_ABSTRACT)
            FileSpec.builder(dbTypeEl.packageName, "$dbTypeEl$SUFFIX_SYNCDAO_ABSTRACT")
                    .addType(dbTypeEl.toSyncDaoInterfaceTypeSpec(processingEnv))
                    .addType(syncDaoType)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_JVM_DIRS)

            FileSpec.builder(dbTypeEl.packageName, "$dbTypeEl$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_JDBC_KT2")
                    .addJdbcDaoImplType(syncDaoType, syncDaoClassName)
                    .addImport("com.ustadmobile.door", "DoorDbType")
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_JVM_DIRS)

            //val (abstractFileSpec, implFileSpec, repoImplSpec) = generateSyncDaoInterfaceAndImpls(dbTypeEl)

//            writeFileSpecToOutputDirs(abstractFileSpec, AnnotationProcessorWrapper.OPTION_JVM_DIRS)
//            writeFileSpecToOutputDirs(abstractFileSpec, AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT,
//                    useFilerAsDefault = false)
            //writeFileSpecToOutputDirs(implFileSpec, AnnotationProcessorWrapper.OPTION_JVM_DIRS)

//            val syncRepoFileSpec = generateSyncRepository(dbTypeEl)
//            writeFileSpecToOutputDirs(syncRepoFileSpec, AnnotationProcessorWrapper.OPTION_JVM_DIRS)
//            writeFileSpecToOutputDirs(syncRepoFileSpec, AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT,
//                    useFilerAsDefault = false)

            //Generate a repo for the SyncDao

            FileSpec.builder(dbTypeEl.packageName, "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_REPOSITORY2")
                    .addDaoRepoType(dbTypeEl.toSyncDaoTypeSpec(processingEnv),
                            syncDaoClassName,
                        processingEnv, allKnownEntityTypesMap, false,
                            false, syncHelperClassName = syncDaoClassName)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_JVM_DIRS)

            FileSpec.builder(dbTypeEl.packageName,
                    "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_KTOR_ROUTE")
                    .addDaoKtorRouteFun(syncDaoType, syncDaoClassName,
                            syncDaoClassName, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_KTOR_HELPER")
                    .addKtorHelperInterface(syncDaoType, syncDaoClassName, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                    "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_KTOR_HELPER_MASTER")
                    .addKtorAbstractDao(syncDaoType, syncDaoClassName,
                            MasterChangeSeqNum::class, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                    "${dbTypeEl.simpleName}$SUFFIX_SYNCDAO_ABSTRACT$SUFFIX_KTOR_HELPER_LOCAL")
                    .addKtorAbstractDao(syncDaoType, syncDaoClassName,
                        LocalChangeSeqNum::class, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                "${dbTypeEl.simpleName}${SUFFIX_SYNCDAO_ABSTRACT}${SUFFIX_KTOR_HELPER_MASTER}_${SUFFIX_JDBC_KT}")
                    .addKtorHelperDaoImplementation(syncDaoType, syncDaoClassName,
                        MasterChangeSeqNum::class, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

            FileSpec.builder(dbTypeEl.packageName,
                    "${dbTypeEl.simpleName}${SUFFIX_SYNCDAO_ABSTRACT}${SUFFIX_KTOR_HELPER_LOCAL}_${SUFFIX_JDBC_KT}")
                    .addKtorHelperDaoImplementation(syncDaoType, syncDaoClassName,
                            LocalChangeSeqNum::class, processingEnv)
                    .build()
                    .writeToDirsFromArg(AnnotationProcessorWrapper.OPTION_KTOR_OUTPUT)

        }

        val daos = roundEnv.getElementsAnnotatedWith(Dao::class.java)
        daos.filter { !it.simpleName.endsWith(SUFFIX_SYNCDAO_ABSTRACT) }.forEach {daoElement ->
            val daoTypeEl = daoElement as TypeElement
            val daoFileSpec = generateDaoSyncHelperInterface(daoTypeEl)
            writeFileSpecToOutputDirs(daoFileSpec, AnnotationProcessorWrapper.OPTION_JVM_DIRS)
            writeFileSpecToOutputDirs(daoFileSpec, AnnotationProcessorWrapper.OPTION_ANDROID_OUTPUT,
                    useFilerAsDefault = false)
        }


        return true
    }

    /**
     * Generates an interface that will be used for
     */
    fun generateDaoSyncHelperInterface(daoType: TypeElement): FileSpec {
        val syncableEntitiesOnDao = syncableEntitiesOnDao(daoType.asClassName(), processingEnv)
        val syncHelperInterface = TypeSpec.interfaceBuilder("${daoType.simpleName}_SyncHelper")

        syncableEntitiesOnDao.forEach {
            syncHelperInterface.addFunction(
                    FunSpec.builder("_replace${it.simpleName}")
                            .addParameter("entityList", List::class.asClassName().parameterizedBy(it))
                            .addModifiers(KModifier.ABSTRACT)
                            .build())

            val entitySyncTrackerClassName = ClassName(it.packageName,
                    "${it.simpleName}$TRACKER_SUFFIX")
            syncHelperInterface.addFunction(
                    FunSpec.builder("_replace${entitySyncTrackerClassName.simpleName}")
                            .addParameter("entityTrackerList",
                                    List::class.asClassName().parameterizedBy(entitySyncTrackerClassName))
                            .addModifiers(KModifier.ABSTRACT)
                            .build())

        }

        return FileSpec.builder(pkgNameOfElement(daoType, processingEnv),
                "${daoType.simpleName}_SyncHelper")
                .addType(syncHelperInterface.build())
                .build()
    }

    data class SyncFileSpecs(val abstractFileSpec: FileSpec, val daoImplFileSpec: FileSpec, val repoImplFileSpec: FileSpec)


    fun generateSyncRepository(dbType: TypeElement): FileSpec {
        val dbClassName = dbType.asClassName()
        val syncDaoSimpleName = "${dbClassName.simpleName}${SUFFIX_SYNCDAO_ABSTRACT}"
        val syncRepoSimpleName =
                "${syncDaoSimpleName}_${DbProcessorRepository.SUFFIX_REPOSITORY}"
        val repoFileSpec = FileSpec.builder(dbClassName.packageName,
                syncRepoSimpleName)
        val daoClassName = ClassName(dbClassName.packageName,
                "${dbClassName.simpleName}$SUFFIX_SYNCDAO_ABSTRACT")
        val repoTypeSpec = newRepositoryClassBuilder(daoClassName, false,
            extraConstructorParams = listOf(ParameterSpec.builder("_updateNotificationManager",
                ServerUpdateNotificationManager::class.asClassName().copy(nullable = true)).build()))
                .addProperty(PropertySpec.builder("clientId", INT)
                        .getter(FunSpec.getterBuilder().addCode("return _findSyncNodeClientId()\n")
                        .build()).build())
                .addProperty(PropertySpec.builder("_updateNotificationManager",
                    ServerUpdateNotificationManager::class.asClassName().copy(nullable = true))
                        .initializer("_updateNotificationManager")
                        .build())

        val syncFnCodeBlock = CodeBlock.builder()
                .add("val _allResults = mutableListOf<%T>()\n", SyncResult::class)

        repoTypeSpec.addFunction(FunSpec.builder("_findSyncNodeClientId")
                .addModifiers(KModifier.OVERRIDE)
                .returns(INT)
                .addCode("return _dao._findSyncNodeClientId()\n")
                .build())

        repoTypeSpec.addFunction(FunSpec.builder("_insertSyncResult")
                .addParameter("result", SyncResult::class)
                .addModifiers(KModifier.OVERRIDE)
                .addCode("_dao._insertSyncResult(result)\n")
                .build())


        repoTypeSpec.addFunction(FunSpec.builder("dispatchUpdateNotifications")
                .addParameter("tableId", INT)
                .addModifiers(KModifier.SUSPEND)
                .addCode(CodeBlock.builder()
                        .beginControlFlow("when(tableId)")
                        .apply {
                            syncableEntityTypesOnDb(dbType, processingEnv).forEach {syncEl ->
                                val syncableEntityInfo = SyncableEntityInfo(syncEl.asClassName(),
                                        processingEnv)
                                if(syncableEntityInfo.notifyOnUpdate.size > 0) {
                                    beginControlFlow("%L -> ", syncableEntityInfo.tableId)
                                    syncableEntityInfo.notifyOnUpdate.forEachIndexed { index, queryStr ->
                                        add("_findDevicesToNotify${syncEl.simpleName}_$index()\n")
                                    }
                                    endControlFlow()
                                }
                            }
                        }
                        .endControlFlow()
                        .add("(_repo as %T).syncHelperEntitiesDao.deleteChangeLogs(tableId)\n",
                                DoorDatabaseSyncRepository::class)
                        .build())
                .build())

        repoTypeSpec.addFunction(FunSpec.builder("_replaceUpdateNotifications")
                .addParameter("entities", List::class.parameterizedBy(UpdateNotification::class))
                .addCode("%T.v(\"SyncRepo replaceUpdateNotifications: \${entities.%M()}\", tag = %T.LOG_TAG)\n",
                        Napier::class,
                        MemberName("kotlin.collections", "joinToString"),
                        DoorTag::class)
                .addCode(CodeBlock.of("_dao._replaceUpdateNotifications(entities)\n"))
                .addModifiers(KModifier.OVERRIDE)
                .build())

        repoTypeSpec.addFunction(FunSpec.builder("_deleteChangeLogs")
                .addParameter("tableId", INT)
                .addModifiers(KModifier.OVERRIDE)
                .addCode("_dao._deleteChangeLogs(tableId)\n")
                .build())

        syncableEntityTypesOnDb(dbType, processingEnv).forEach { entityType ->
            val syncableEntityInfo = SyncableEntityInfo(entityType.asClassName(), processingEnv)
            val entityListTypeName = List::class.asClassName().parameterizedBy(entityType.asClassName())
            val entityPkEl = entityType.enclosedElements.first { it.getAnnotation(PrimaryKey::class.java) != null }

            val replaceEntitiesFn = FunSpec.builder("_replace${entityType.simpleName}")
                    .addParameter("_entities", List::class.asClassName().parameterizedBy(entityType.asClassName()))
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode("_dao._replace${entityType.simpleName}(_entities)\n")
                    .build()
            repoTypeSpec.addFunction(replaceEntitiesFn)


            repoTypeSpec.addFunction(FunSpec.builder("_replace${syncableEntityInfo.tracker.simpleName}")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("entities",
                            List::class.asClassName().parameterizedBy(syncableEntityInfo.tracker))
                    .addCode("_dao._replace${syncableEntityInfo.tracker.simpleName}(entities)\n")
                    .build())

            repoTypeSpec.addFunction(FunSpec.builder("_findLocalUnsent${entityType.simpleName}")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("destClientId", INT)
                    .addParameter("limit", INT)
                    .addCode("return _dao._findLocalUnsent${entityType.simpleName}(destClientId, limit)\n")
                    .returns(List::class.asClassName().parameterizedBy(syncableEntityInfo.syncableEntity))
                    .build())

//            repoTypeSpec.addFunction(FunSpec.builder("_update${syncableEntityInfo.tracker.simpleName}Received")
//                    .addModifiers(KModifier.OVERRIDE)
//                    .addParameter("status", BOOLEAN)
//                    .addParameter("requestId", INT)
//                    .addCode("_dao._update${syncableEntityInfo.tracker.simpleName}Received(status, requestId)\n")
//                    .build())


            val findMasterUnsentFnSpec = FunSpec.builder("_findMasterUnsent${entityType.simpleName}")
                    .returns(entityListTypeName)
                    .addModifiers(KModifier.SUSPEND)
                    .addAnnotation(AnnotationSpec.builder(Query::class)
                            .addMember(CodeBlock.of("%S", "SELECT * FROM ${entityType.simpleName}")).build())

            val entitySyncCodeBlock = CodeBlock.builder()
                    .add("var _receiveCount = 0\n")
                    .add(generateRepositoryGetSyncableEntitiesFun(findMasterUnsentFnSpec.build(),
                            syncDaoSimpleName, syncHelperDaoVarName = "_dao", addReturnDaoResult = false,
                            receiveCountVarName = "_receiveCount"))
                    .add("_loadHelper.doRequest()\n")

            syncableEntityInfo.notifyOnUpdate.forEachIndexed { index, notifyOnUpdateSql ->
                repoTypeSpec.addFunction(FunSpec.builder("_findDevicesToNotify${entityType.simpleName}_$index")
                        .returns(List::class.parameterizedBy(UpdateNotificationSummary::class))
                        .addModifiers(KModifier.OVERRIDE)
                        .addCode(CodeBlock.builder()
                                .add("return (_repo as %T).%M(${syncableEntityInfo.tableId}, _updateNotificationManager, " +
                                        "_dao::_findDevicesToNotify${entityType.simpleName}_$index, " +
                                        "::_replaceUpdateNotifications)",
                                        DoorDatabaseSyncRepository::class,
                                        MemberName("com.ustadmobile.door.ext", "sendUpdates"))
                                .build())
                        .build())
            }


            val hasAttachments = entityType.getAnnotation(EntityWithAttachment::class.java) != null

            entitySyncCodeBlock.add("val _entities = _findLocalUnsent${entityType.simpleName}(0, 100)\n")
                    .add("var _sendCount = 0\n")
                    .add("%T.v(\"SyncDao·-·${entityType.simpleName}·found·\${_entities.size}·local·changes·to·send\"," +
                            "tag = %T.LOG_TAG)\n",
                            Napier::class, DoorTag::class)
                    .beginControlFlow("if(!_entities.isEmpty())")
            var multipartPartsVarName: String? = null
            if(hasAttachments) {
                entitySyncCodeBlock.add("val _entityAttachmentsDir = %T(_attachmentsDir, %S)\n",
                        File::class, entityType.simpleName)
                val pkFieldName = entityPkEl.simpleName
                entitySyncCodeBlock
                        .add("val _multipartJsonStr = (%M().write(_entities) as %T).text\n",
                                MemberName("io.ktor.client.features.json", "defaultSerializer"),
                                TextContent::class)
                        .beginControlFlow("val·_multipartParts = %M",
                                MemberName("io.ktor.client.request.forms", "formData"))
                        .add("append(%S, _multipartJsonStr)\n", "entities")
                        .beginControlFlow("_entities.forEach")
                        .add("val _pkStr = it.$pkFieldName.toString()\n")
                        .add("val _attachFile = %T(_entityAttachmentsDir, _pkStr)\n",
                                File::class)
                        .beginControlFlow("val _mpHeaders = %T.build", Headers::class)
                        .add("append(%T.ContentLength, _attachFile.length())\n", HttpHeaders::class)
                        .add("append(%T.ContentDisposition,·\"form-data;·name=\\\"\$_pkStr\\\";·filename=\\\"\$_pkStr\\\"\")\n",
                                HttpHeaders::class)
                        .add("%M(_db)\n",
                                MemberName("com.ustadmobile.door.ext", "appendDbVersionHeader"))

                        .endControlFlow()
                        .add("append(_attachFile.name,·%T(_attachFile.length()){%T(_attachFile).%M()}, _mpHeaders)\n",
                                InputProvider::class, FileInputStream::class,
                                MemberName("io.ktor.utils.io.streams", "asInput"))

                        .endControlFlow()
                        .endControlFlow()
                        .add("\n")
                multipartPartsVarName = "_multipartParts"
            }


            entitySyncCodeBlock.add(generateKtorRequestCodeBlockForMethod(httpEndpointVarName = "_endpoint",
                            dbPathVarName = "_dbPath",
                            daoName = syncDaoSimpleName, methodName = replaceEntitiesFn.name,
                            httpResultVarName = "_sendResult", httpStatementVarName = "_sendHttpResponse",
                            httpResultType = UNIT, params = replaceEntitiesFn.parameters,
                            useMultipartPartsVarName = multipartPartsVarName))
                    .add(generateReplaceSyncableEntitiesTrackerCodeBlock("_entities",
                            entityListTypeName, syncHelperDaoVarName = "_dao", clientIdVarName = "0",
                            reqIdVarName = "0", processingEnv = processingEnv, isPrimaryDb = false))
                    .add("_sendCount += _entities.size\n")
                    .endControlFlow()

            entitySyncCodeBlock.add("""return %T(tableId = ${syncableEntityInfo.tableId},
                |status = %T.STATUS_SUCCESS, timestamp = %M(), sent = _sendCount, received = _receiveCount)
                """.trimMargin(), SyncResult::class, SyncResult::class, SYSTEMTIME_MEMBER_NAME)

            entitySyncCodeBlock.add("\n")

            val entitySyncFn = FunSpec.builder("sync${entityType.simpleName}")
                    .addModifiers(KModifier.SUSPEND, KModifier.PRIVATE)
                    .returns(SyncResult::class)
                    .addCode(entitySyncCodeBlock.build())

            syncFnCodeBlock.beginControlFlow("if(tablesToSync == null || ${syncableEntityInfo.tableId} in tablesToSync)",
                            entityType)
                    .beginControlFlow("try")
                    .add("val _syncResult = sync${entityType.simpleName}()\n")
                    .add("_allResults += _syncResult\n")
                    .add("_insertSyncResult(_syncResult)\n")
                    .nextControlFlow("catch(e: %T)", Exception::class)
                    .add("""_insertSyncResult(%T(tableId = ${syncableEntityInfo.tableId}, 
                        |status = %T.STATUS_FAILED, timestamp = %M()))
                        |""".trimMargin(),
                            SyncResult::class, SyncResult::class, SYSTEMTIME_MEMBER_NAME)
                    .endControlFlow()
                    .endControlFlow()
            repoTypeSpec.addFunction(entitySyncFn.build())
        }


        syncFnCodeBlock.beginControlFlow(
                "val _syncRunStatus = if(_allResults.all { it.status == %T.STATUS_SUCCESS })",
                SyncResult::class)
                .add("%T.STATUS_SUCCESS\n", SyncResult::class)
                .nextControlFlow("else")
                .add("%T.STATUS_FAILED\n", SyncResult::class)
                .endControlFlow()
                .add("_insertSyncResult(%T(tableId = 0, status = _syncRunStatus, timestamp = %M()))\n",
                        SyncResult::class, SYSTEMTIME_MEMBER_NAME)
                .add("return _allResults\n")

        repoTypeSpec.addFunction(FunSpec.builder("sync")
                .addModifiers(KModifier.SUSPEND)
                .returns(List::class.parameterizedBy(SyncResult::class))
                .addParameter("tablesToSync", List::class.parameterizedBy(Int::class).copy(nullable = true))
                .addCode(syncFnCodeBlock.build())
                .build())

        return repoFileSpec.addType(repoTypeSpec.build()).build()
    }

    /**
     *
     * @return Pair of FileSpecs: first = the abstract DAO filespec, the second one is the implementation
     */
    fun generateSyncDaoInterfaceAndImpls(dbType: TypeElement): SyncFileSpecs {
        val abstractDaoSimpleName = "${dbType.simpleName}$SUFFIX_SYNCDAO_ABSTRACT"
        val abstractDaoClassName = ClassName(pkgNameOfElement(dbType, processingEnv),
                abstractDaoSimpleName)
        val abstractDaoTypeSpec = TypeSpec.classBuilder(abstractDaoSimpleName)
                .addAnnotation(Dao::class.asClassName())
                .addModifiers(KModifier.ABSTRACT)
                .addSuperinterface(ClassName(pkgNameOfElement(dbType, processingEnv), "I$abstractDaoSimpleName"))

        daosOnDb(dbType.asClassName(), processingEnv, excludeDbSyncDao = true)
                .filter { syncableEntitiesOnDao(it, processingEnv).isNotEmpty()}
                .forEach {
                    abstractDaoTypeSpec.addSuperinterface(
                            ClassName(it.packageName, "${it.simpleName}_SyncHelper"))
                }

        val abstractDaoInterfaceTypeSpec = TypeSpec.interfaceBuilder("I$abstractDaoSimpleName")

        val abstractFileSpec = FileSpec.builder(pkgNameOfElement(dbType, processingEnv),
                abstractDaoSimpleName)
                .addImport("com.ustadmobile.door", "DoorDbType")


        val implDaoSimpleName = "${dbType.simpleName}$SUFFIX_SYNCDAO_IMPL"
        val implDaoClassName = ClassName(pkgNameOfElement(dbType, processingEnv),
                implDaoSimpleName)
        val implDaoTypeSpec = jdbcDaoTypeSpecBuilder(implDaoSimpleName, abstractDaoClassName)
        val implFileSpec = FileSpec.builder(pkgNameOfElement(dbType, processingEnv),
                implDaoSimpleName)
                .addImport("com.ustadmobile.door", "DoorDbType")

        //TODO: this is no longer made here - remove it from this fn
        val repoImplSimpleName = "${dbType.simpleName}${DbProcessorRepository.SUFFIX_REPOSITORY}"
        val repoImplSpec = FileSpec.builder(pkgNameOfElement(dbType, processingEnv),
                repoImplSimpleName)

//        val (abstractDaoFindSyncClientIdFn, implFindSyncClientIdFn) = generateAbstractAndImplQueryFunSpecs(
//                "SELECT nodeClientId FROM SyncNode", "_findSyncNodeClientId",
//                INT, listOf(), addReturnStmt = true)
//        abstractDaoTypeSpec.addFunction(abstractDaoFindSyncClientIdFn)
//        implDaoTypeSpec.addFunction(implFindSyncClientIdFn)

//        val syncResultTypeSpec = processingEnv.elementUtils.getTypeElement(
//                SyncResult::class.qualifiedName) as TypeElement
//        val (abstractInsertSyncResultFn, implInsertSyncResultFn, abstractInterfaceInsertSyncResultFn)
//                = generateAbstractAndImplUpsertFuns("_insertSyncResult",
//                ParameterSpec.builder("result", SyncResult::class).build(),
//                syncResultTypeSpec.asEntityTypeSpec(), implDaoTypeSpec, abstractFunIsOverride = true)
//        abstractDaoTypeSpec.addFunction(abstractInsertSyncResultFn)
//        abstractDaoInterfaceTypeSpec.addFunction(abstractInterfaceInsertSyncResultFn)
//        implDaoTypeSpec.addFunction(implInsertSyncResultFn)


        val updateNotificationClassName = UpdateNotification::class.asClassName()
        val updateNotificationTypeEl = processingEnv.elementUtils.getTypeElement(
            "${updateNotificationClassName.packageName}.${updateNotificationClassName.simpleName}")

        //Generate _replaceUpdateNotifications - used by dispatchPushNotifications to create / update UpdateNotifications
        val (abstractReplaceUpdateNotification, implReplaceUpdateNotification, abstractDaoReplaceUpdateNotification) =
                generateAbstractAndImplUpsertFuns(
                        "_replaceUpdateNotifications",
                        ParameterSpec.builder("entities", List::class.parameterizedBy(UpdateNotification::class)).build(),
                        updateNotificationTypeEl.asEntityTypeSpec(),
                        implDaoTypeSpec, abstractFunIsOverride = true,
                        pgOnConflict = "ON CONFLICT (pnDeviceId, pnTableId) DO UPDATE SET pnTimestamp = excluded.pnTimestamp")
        abstractDaoTypeSpec.addFunction(abstractReplaceUpdateNotification)
        implDaoTypeSpec.addFunction(implReplaceUpdateNotification)
        abstractDaoInterfaceTypeSpec.addFunction(abstractDaoReplaceUpdateNotification)

        val (abstractDeleteChangeLogFun, implDeleteChangeLogFun, abstractDaoDeleteChangeLogFun) =
                generateAbstractAndImplQueryFunSpecs(
                        """
                        DELETE FROM ChangeLog
                        WHERE chTableId = :tableId
                        AND chTime < (SELECT max(pnTimestamp) FROM UpdateNotification WHERE pnTableId = :tableId)
                        """.trimIndent(), "_deleteChangeLogs", UNIT,
                        listOf(ParameterSpec("tableId", INT)),
                        addReturnStmt = false, abstractFunIsOverride = true)
        abstractDaoTypeSpec.addFunction(abstractDeleteChangeLogFun)
        implDaoTypeSpec.addFunction(implDeleteChangeLogFun)
        abstractDaoInterfaceTypeSpec.addFunction(abstractDaoDeleteChangeLogFun)


        syncableEntityTypesOnDb(dbType, processingEnv).forEach {entityType ->
            val syncableEntityInfo = SyncableEntityInfo(entityType.asClassName(), processingEnv)
            val entityListClassName = List::class.asClassName().parameterizedBy(entityType.asClassName())
            val entitySyncTrackerListClassName = List::class.asClassName().parameterizedBy(syncableEntityInfo.tracker)

            //Generate the find local unsent changes function for this entity
            val findLocalUnsentSql = "SELECT * FROM " +
                    "(SELECT * FROM ${entityType.simpleName} ) AS ${entityType.simpleName} " +
                    "WHERE " +
                    "${syncableEntityInfo.entityLastChangedByField.name} = (SELECT nodeClientId FROM SyncNode) AND " +
                    "(${entityType.simpleName}.${syncableEntityInfo.entityLocalCsnField.name} > " +
                    "COALESCE((SELECT ${syncableEntityInfo.trackerCsnField.name} FROM ${syncableEntityInfo.tracker.simpleName} " +
                    "WHERE ${syncableEntityInfo.trackerPkField.name} = ${entityType.simpleName}.${syncableEntityInfo.entityPkField.name} " +
                    "AND ${syncableEntityInfo.trackerDestField.name} = :destClientId), 0)" +
                    ") LIMIT :limit"


            val findUnsentParamsList = listOf(ParameterSpec.builder("destClientId", INT).build(),
                    ParameterSpec.builder("limit", INT).build())
            val (abstractLocalUnsentChangeFun, implLocalUnsetChangeFun) =
                    generateAbstractAndImplQueryFunSpecs(findLocalUnsentSql,
                            "_findLocalUnsent${entityType.simpleName}",
                            entityListClassName, findUnsentParamsList)
            abstractDaoTypeSpec.addFunction(abstractLocalUnsentChangeFun)
            implDaoTypeSpec.addFunction(implLocalUnsetChangeFun)

            //generate an upsert function for the entity itself
            val (abstractInsertEntityFun, implInsertEntityFun, abstractInterfaceInsertEntityFun) =
                generateAbstractAndImplUpsertFuns(
                    "_replace${entityType.simpleName}",
                    ParameterSpec.builder("entities", entityListClassName).build(),
                    entityType.asEntityTypeSpec(),
                    implDaoTypeSpec, abstractFunIsOverride = true)
            abstractDaoTypeSpec.addFunction(abstractInsertEntityFun)
            abstractDaoInterfaceTypeSpec.addFunction(abstractInterfaceInsertEntityFun)
            implDaoTypeSpec.addFunction(implInsertEntityFun)

            val (abstractInsertTrackerFun, implInsertTrackerFun, abstractInterfaceInsertTrackerFun) =
                    generateAbstractAndImplUpsertFuns(
                    "_replace${syncableEntityInfo.tracker.simpleName}",
                    ParameterSpec.builder("entities", entitySyncTrackerListClassName).build(),
                    generateTrackerEntity(entityType, processingEnv),
                    implDaoTypeSpec, abstractFunIsOverride = true,
                            pgOnConflict = " ON CONFLICT(epk, clientId) DO UPDATE SET csn = excluded.csn")
            abstractDaoTypeSpec.addFunction(abstractInsertTrackerFun)
            implDaoTypeSpec.addFunction(implInsertTrackerFun)
            abstractDaoInterfaceTypeSpec.addFunction(abstractInterfaceInsertTrackerFun)

            //generate an update function that can be used to set the status of the sync tracker
//            val updateTrackerReceivedSql = "UPDATE ${syncableEntityInfo.tracker.simpleName} SET " +
//                    "${syncableEntityInfo.trackerReceivedField.name} = :status WHERE " +
//                    "${syncableEntityInfo.trackerReqIdField.name} = :requestId"
//            val (abstractUpdateTrackerFun, implUpdateTrackerFun, abstractInterfaceUpdateTrackerFun) =
//                    generateAbstractAndImplQueryFunSpecs(updateTrackerReceivedSql,
//                            "_update${syncableEntityInfo.tracker.simpleName}Received",
//                            UNIT, listOf(ParameterSpec.builder("status", BOOLEAN).build(),
//                            ParameterSpec.builder("requestId", INT).build()),
//                            addReturnStmt = false, abstractFunIsOverride = true)
//            abstractDaoTypeSpec.addFunction(abstractUpdateTrackerFun)
//            implDaoTypeSpec.addFunction(implUpdateTrackerFun)
//            abstractDaoInterfaceTypeSpec.addFunction(abstractInterfaceUpdateTrackerFun)


            syncableEntityInfo.notifyOnUpdate.forEachIndexed { index, notifyOnUpdateSql ->
                val (abstractFindDevicesFun, implFindDevicesFun, abstractInterfaceFindDevicesFun) =
                        generateAbstractAndImplQueryFunSpecs(notifyOnUpdateSql,
                                "_findDevicesToNotify${entityType.simpleName}_$index",
                                List::class.parameterizedBy(UpdateNotificationSummary::class),
                                listOf(),
                                abstractFunIsOverride = true)
                abstractDaoTypeSpec.addFunction(abstractFindDevicesFun)
                implDaoTypeSpec.addFunction(implFindDevicesFun)
                abstractDaoInterfaceTypeSpec.addFunction(abstractInterfaceFindDevicesFun)
            }
        }


        abstractFileSpec.addType(abstractDaoInterfaceTypeSpec.build())
        abstractFileSpec.addType(abstractDaoTypeSpec.build())

        implFileSpec.addType(implDaoTypeSpec.build())
        return SyncFileSpecs(abstractFileSpec.build(), implFileSpec.build(), repoImplSpec.build())
    }

    data class AbstractImplAndInterfaceFunSpecs(val abstractFunSpec: FunSpec, val implFunSpec: FunSpec,
                                                val interfaceFunSpec: FunSpec)
    private fun generateAbstractAndImplUpsertFuns(funName: String, paramSpec: ParameterSpec,
                                                  entityTypeSpec: TypeSpec,
                                                  daoTypeBuilder: TypeSpec.Builder,
                                                  abstractFunIsOverride: Boolean = false,
                                                  pgOnConflict: String? = null): AbstractImplAndInterfaceFunSpecs {
        val funBuilders = (0..2).map {
            FunSpec.builder(funName)
                    .returns(UNIT)
                    .addParameter(paramSpec)
        }
        funBuilders[0].addModifiers(KModifier.ABSTRACT)
        funBuilders[2].addModifiers(KModifier.ABSTRACT)

        if(abstractFunIsOverride) {
            funBuilders[0].addModifiers(KModifier.OVERRIDE)
        }

        funBuilders[0].addAnnotation(AnnotationSpec.builder(Insert::class)
                .addMember("onConflict = %T.REPLACE", OnConflictStrategy::class).build())
        if(pgOnConflict != null) {
            funBuilders[0].addAnnotation(AnnotationSpec.builder(PgOnConflict::class)
                    .addMember("value = %S", pgOnConflict)
                    .build())
        }

        funBuilders[1].addModifiers(KModifier.OVERRIDE)
        funBuilders[1].addCode(generateInsertCodeBlock(paramSpec, UNIT, entityTypeSpec,
                daoTypeBuilder,true, pgOnConflict = pgOnConflict))

        return AbstractImplAndInterfaceFunSpecs(funBuilders[0].build(), funBuilders[1].build(),
                funBuilders[2].build())
    }

    private fun generateAbstractAndImplQueryFunSpecs(querySql: String,
                                             funName: String,
                                             returnType: TypeName,
                                             params: List<ParameterSpec>,
                                             addReturnStmt: Boolean = true,
                                             abstractFunIsOverride: Boolean = false,
                                             suspended: Boolean = false): AbstractImplAndInterfaceFunSpecs {
        val funBuilders = (0..2).map {
            FunSpec.builder(funName)
                    .returns(returnType)
                    .addParameters(params)
                    .apply { takeIf { suspended }?.addModifiers(KModifier.SUSPEND) }
        }

        funBuilders[0].addModifiers(KModifier.ABSTRACT)
        if(abstractFunIsOverride)
            funBuilders[0].addModifiers(KModifier.OVERRIDE)
        funBuilders[1].addModifiers(KModifier.OVERRIDE)
        funBuilders[2].addModifiers(KModifier.ABSTRACT)


        funBuilders[0].addAnnotation(AnnotationSpec.builder(Query::class)
                .addMember("value = %S", querySql).build())

        funBuilders[1].addCode(generateQueryCodeBlock(returnType,
                params.map { it.name to it.type}.toMap(), querySql,
                null, null))

        if(addReturnStmt) {
            funBuilders[1].addCode("return _result\n")
        }

        return AbstractImplAndInterfaceFunSpecs(funBuilders[0].build(),
                funBuilders[1].build(), funBuilders[2].build())
    }


    companion object {

        const val SUFFIX_SYNCDAO_ABSTRACT = "SyncDao"

        const val SUFFIX_SYNCDAO_IMPL = "SyncDao_JdbcKt"

        const val SUFFIX_SYNC_ROUTE = "SyncDao_KtorRoute"

        const val SUFFIX_ENTITY_TRK = "_trk"

        /**
         * The Suffix of the generated tracker entity which is created for each entity annotated
         * with SyncableEntity
         */
        const val TRACKER_SUFFIX = "_trk"

        const val TRACKER_PK_FIELDNAME = "pk"

        const val TRACKER_ENTITY_PK_FIELDNAME = "epk"

        const val TRACKER_DESTID_FIELDNAME = "clientId"

        const val TRACKER_CHANGESEQNUM_FIELDNAME = "csn"

        const val TRACKER_RECEIVED_FIELDNAME = "rx"

        const val TRACKER_REQUESTID_FIELDNAME = "reqId"

        const val TRACKER_TIMESTAMP_FIELDNAME = "ts"

        val SYSTEMTIME_MEMBER_NAME = MemberName("com.ustadmobile.door.util", "systemTimeInMillis")

        /**
         * The path postfix to be used in the Sync HTTP Server for the url to a Server Sent Events source
         *
         * e.g. path will be DbName/DbNameSyncDao/_updateNotifications
         */
        const val ENDPOINT_POSTFIX_UPDATES = "_updateNotifications"

        /**
         * The path postfix to be used in the Sync HTTP Server for the url that will delete an update
         * notification that has been received
         */
        const val ENDPOINT_POSTFIX_DELETE_UPDATE = "_deleteUpdateNotification"

        val CLASSNAME_SYNC_HELPERENTITIES_DAO = ClassName("com.ustadmobile.door.daos",
            "SyncHelperEntitiesDao")
    }

}