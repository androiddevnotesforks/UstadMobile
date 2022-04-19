package com.ustadmobile.core.controller

import com.ustadmobile.core.contentformats.xapi.endpoints.XapiStatementEndpoint
import com.ustadmobile.core.contentformats.xapi.endpoints.storeSubmitFileSubmissionStatement
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.core.generated.locale.MessageID
import com.ustadmobile.core.impl.NavigateForResultOptions
import com.ustadmobile.core.impl.NoAppFoundException
import com.ustadmobile.core.io.ext.guessMimeType
import com.ustadmobile.core.util.UmPlatformUtil
import com.ustadmobile.core.util.ext.effectiveTimeZone
import com.ustadmobile.core.util.ext.observeWithLifecycleOwner
import com.ustadmobile.core.util.ext.putEntityAsJson
import com.ustadmobile.core.util.safeParse
import com.ustadmobile.core.util.safeParseList
import com.ustadmobile.core.view.*
import com.ustadmobile.core.view.TextAssignmentEditView.Companion.EDIT_ENABLED
import com.ustadmobile.core.view.UstadView.Companion.ARG_ENTITY_UID
import com.ustadmobile.door.DoorLifecycleOwner
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.attachments.retrieveAttachment
import com.ustadmobile.door.doorMainDispatcher
import com.ustadmobile.door.ext.doorPrimaryKeyManager
import com.ustadmobile.door.ext.onRepoWithFallbackToDb
import com.ustadmobile.door.util.randomUuid
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.di.on
import kotlin.jvm.JvmStatic


class ClazzAssignmentDetailOverviewPresenter(context: Any,
                                             arguments: Map<String, String>, view: ClazzAssignmentDetailOverviewView,
                                             lifecycleOwner: DoorLifecycleOwner,
                                             di: DI,val newPrivateCommentListener: DefaultNewCommentItemListener =
                                                     DefaultNewCommentItemListener(di, context,
                                                             arguments[ARG_ENTITY_UID]?.toLong() ?: 0L,
                                                     ClazzAssignment.TABLE_ID, false),
                                             val newClassCommentListener: DefaultNewCommentItemListener =
                                                     DefaultNewCommentItemListener(di, context,
                                                             arguments[ARG_ENTITY_UID]?.toLong() ?: 0L,
                                                             ClazzAssignment.TABLE_ID, true))
    : UstadDetailPresenter<ClazzAssignmentDetailOverviewView, ClazzAssignmentWithCourseBlock>(context, arguments, view, di, lifecycleOwner){


    val statementEndpoint by on(accountManager.activeAccount).instance<XapiStatementEndpoint>()

    val submissionList = mutableListOf<CourseAssignmentSubmissionWithAttachment>()

    override val persistenceMode: PersistenceMode
          get() = PersistenceMode.DB

    override suspend fun onCheckEditPermission(account: UmAccount?): Boolean {
        return false
    }

    override suspend fun onLoadEntityFromDb(db: UmAppDatabase): ClazzAssignmentWithCourseBlock? {
        val entityUid = arguments[ARG_ENTITY_UID]?.toLong() ?: 0L

        val clazzAssignment = db.onRepoWithFallbackToDb(2000){
            it.clazzAssignmentDao.findByUidWithBlockAsync(entityUid)
        } ?: ClazzAssignmentWithCourseBlock()

        loadAssignment(clazzAssignment, db)

        return clazzAssignment
    }

    private suspend fun loadAssignment(clazzAssignment: ClazzAssignmentWithCourseBlock, db: UmAppDatabase) {
        val loggedInPersonUid = accountManager.activeAccount.personUid

        val clazzWithSchool = db.onRepoWithFallbackToDb(2000) {
            it.clazzDao.getClazzWithSchool(clazzAssignment.caClazzUid)
        } ?: ClazzWithSchool()

        view.timeZone = clazzWithSchool.effectiveTimeZone()

        val clazzEnrolment: ClazzEnrolment? = db.onRepoWithFallbackToDb(2000) {
            it.clazzEnrolmentDao.findByPersonUidAndClazzUidAsync(loggedInPersonUid,
                    clazzAssignment.caClazzUid)
        }

        val isStudent = ClazzEnrolment.ROLE_STUDENT == clazzEnrolment?.clazzEnrolmentRole ?: 0

        if(isStudent) {
            view.showSubmission = clazzAssignment.caRequireFileSubmission || clazzAssignment.caRequireTextSubmission
            view.maxNumberOfFilesSubmission = clazzAssignment.caNumberOfFiles
            view.hasPassedDeadline = hasPassedDeadline(clazzAssignment)
            view.submittedCourseAssignmentSubmission = db.onRepoWithFallbackToDb(2000){
                it.courseAssignmentSubmissionDao.getAllFileSubmissionsFromStudent(
                        clazzAssignment.caUid, loggedInPersonUid)
            }
            db.courseAssignmentSubmissionDao
                    .getStatusOfAssignmentForStudent(
                            clazzAssignment.caUid, loggedInPersonUid)
                    .observeWithLifecycleOwner(lifecycleOwner){
                        view.submissionStatus = it ?: 0
                    }

            db.courseAssignmentMarkDao.getMarkOfAssignmentForStudent(
                    clazzAssignment.caUid, loggedInPersonUid)
                    .observeWithLifecycleOwner(lifecycleOwner){
                        view.submissionMark = it
                    }
        }else{
            // isTeacher/admin
            view.showSubmission = false
        }


        if(isStudent && clazzAssignment.caPrivateCommentsEnabled){
            view.clazzAssignmentPrivateComments = db.commentsDao.findPrivateByEntityTypeAndUidAndForPersonLive2(
                    ClazzAssignment.TABLE_ID, clazzAssignment.caUid,
                    loggedInPersonUid)
            view.showPrivateComments = true
        }else{
            view.showPrivateComments = false
        }

        if(clazzAssignment.caClassCommentEnabled){
            view.clazzAssignmentClazzComments = db.commentsDao.findPublicByEntityTypeAndUidLive(
                    ClazzAssignment.TABLE_ID, clazzAssignment.caUid)
        }
    }

    override fun onLoadFromJson(bundle: Map<String, String>): ClazzAssignmentWithCourseBlock? {
        super.onLoadFromJson(bundle)

        val entity = safeParse(di, ClazzAssignmentWithCourseBlock.serializer(), bundle[UstadEditView.ARG_ENTITY_JSON].toString())
        presenterScope.launch {
            loadAssignment(entity, db)
        }
        submissionList.addAll(
                safeParseList(di, ListSerializer(CourseAssignmentSubmissionWithAttachment.serializer()),
                CourseAssignmentSubmissionWithAttachment::class, bundle[SAVED_STATE_ADD_SUBMISSION_LIST]
                ?: ""))
        view.addedCourseAssignmentSubmission = submissionList

        return entity
    }

    override fun onSaveInstanceState(savedState: MutableMap<String, String>) {
        super.onSaveInstanceState(savedState)
        savedState.putEntityAsJson(UstadEditView.ARG_ENTITY_JSON, ClazzAssignment.serializer(), entity)
        savedState.putEntityAsJson(SAVED_STATE_ADD_SUBMISSION_LIST,
                ListSerializer(CourseAssignmentSubmissionWithAttachment.serializer()),
                submissionList)
    }


    private fun hasPassedDeadline(course: ClazzAssignmentWithCourseBlock): Boolean {
        val currentTime = systemTimeInMillis()
        return currentTime > (course.block?.cbGracePeriodDate ?: Long.MAX_VALUE)
    }

    override fun onLoadDataComplete() {
        super.onLoadDataComplete()

        observeSavedStateResult(SAVED_STATE_KEY_URI, ListSerializer(String.serializer()),
                String::class) {
            val uri = it.firstOrNull() ?: return@observeSavedStateResult
            presenterScope.launch(doorMainDispatcher()) {
                val doorUri = DoorUri.parse(uri)
                val submission = CourseAssignmentSubmissionWithAttachment().apply {
                    casSubmitterUid = accountManager.activeAccount.personUid
                    casAssignmentUid = entity?.caUid ?: 0
                    casText = doorUri.getFileName(context)
                    casType = CourseAssignmentSubmission.SUBMISSION_TYPE_FILE
                    casUid = db.doorPrimaryKeyManager.nextIdAsync(CourseAssignmentSubmission.TABLE_ID)
                }
                val attachment = CourseAssignmentSubmissionAttachment().apply {
                    casaUid =  db.doorPrimaryKeyManager.nextIdAsync(CourseAssignmentSubmissionAttachment.TABLE_ID)
                    casaSubmissionUid = submission.casUid
                    casaUri = uri
                    casaMd5
                    casaMimeType = doorUri.guessMimeType(context, di)
                }
                submission.attachment = attachment
                submissionList.add(submission)
                view.addedCourseAssignmentSubmission = submissionList
            }
            UmPlatformUtil.run {
                requireSavedStateHandle()[SAVED_STATE_KEY_URI] = null
            }
        }

        observeSavedStateResult(SAVED_STATE_KEY_TEXT, ListSerializer(CourseAssignmentSubmissionWithAttachment.serializer()),
            CourseAssignmentSubmissionWithAttachment::class){
            val submission = it.firstOrNull() ?: return@observeSavedStateResult

            // find existing and remove it
            val existingSubmission = submissionList.find { subList -> subList.casUid == submission.casUid }
            submissionList.remove(existingSubmission)

            submission.casAssignmentUid = entity?.caUid ?: 0
            submission.casSubmitterUid = accountManager.activeAccount.personUid
            submissionList.add(submission)
            view.addedCourseAssignmentSubmission = submissionList
            UmPlatformUtil.run {
                requireSavedStateHandle()[SAVED_STATE_KEY_TEXT] = null
            }
        }

    }

    fun handleDeleteSubmission(submissionCourse: CourseAssignmentSubmissionWithAttachment) {
        submissionList.remove(submissionCourse)
        view.addedCourseAssignmentSubmission = submissionList
    }

    fun handleEditSubmission(courseSubmission: CourseAssignmentSubmissionWithAttachment){
        if(courseSubmission.casType == CourseAssignmentSubmission.SUBMISSION_TYPE_TEXT){
            val args = mutableMapOf<String, String>()
            args.putEntityAsJson(UstadEditView.ARG_ENTITY_JSON,
                CourseAssignmentSubmissionWithAttachment.serializer(), courseSubmission)
            args[EDIT_ENABLED] = true.toString()

            navigateForResult(
                NavigateForResultOptions(
                    this@ClazzAssignmentDetailOverviewPresenter,
                    courseSubmission,
                    TextAssignmentEditView.VIEW_NAME,
                    CourseAssignmentSubmission::class,
                    CourseAssignmentSubmission.serializer(),
                    SAVED_STATE_KEY_TEXT,
                    arguments = args))
        }else{
            presenterScope.launch {
                openAssignmentFileAttachment(courseSubmission)
            }
        }
    }

    fun handleOpenSubmission(
        courseSubmission: CourseAssignmentSubmissionWithAttachment
    ){
        presenterScope.launch {
            if(courseSubmission.casType == CourseAssignmentSubmission.SUBMISSION_TYPE_TEXT){
                val args = mutableMapOf<String, String>()
                args[HtmlTextViewDetailView.DISPLAY_TEXT] = courseSubmission.casText ?: ""

                ustadNavController.navigate(
                    HtmlTextViewDetailView.VIEW_NAME, args)

            }else if(courseSubmission.casType == CourseAssignmentSubmission.SUBMISSION_TYPE_FILE){
                openAssignmentFileAttachment(courseSubmission)
            }
        }
    }

    private suspend fun openAssignmentFileAttachment(courseSubmission: CourseAssignmentSubmissionWithAttachment){
        val fileSubmission = courseSubmission.attachment ?: return
        val uri = fileSubmission.casaUri ?: return
        val doorUri = if(uri.startsWith("door-attachment://")) repo.retrieveAttachment(uri) else DoorUri.parse(uri)
        try{
            systemImpl.openFileInDefaultViewer(context, doorUri, fileSubmission.casaMimeType)
        }catch (e: Exception){
            if (e is NoAppFoundException) {
                view.showSnackBar(systemImpl.getString(MessageID.no_app_found, context))
            }else{
                val message = e.message
                if (message != null) {
                    view.showSnackBar(message)
                }
            }
        }
    }

    fun handleSubmitButtonClicked(){
        presenterScope.launch {
            val hasPassedDeadline = entity?.let { hasPassedDeadline(it) } ?: true
            if(hasPassedDeadline) {
                // TODO mew message for passing deadline
                view.showSnackBar(systemImpl.getString(MessageID.after_deadline_date_error, context))
                return@launch
            }
            repo.courseAssignmentSubmissionDao.insertListAsync(submissionList)
            repo.courseAssignmentSubmissionAttachmentDao.insertListAsync(submissionList.mapNotNull { it.attachment })
            submissionList.clear()
            view.addedCourseAssignmentSubmission = submissionList
            UmPlatformUtil.runAsync {
                withContext(Dispatchers.Default) {
                    val assignment = view.entity ?: return@withContext
                    statementEndpoint.storeSubmitFileSubmissionStatement(
                        accountManager.activeAccount,
                        randomUuid().toString(),
                        assignment)
                }
            }
        }
    }

    fun handleAddFileClicked(){
        val modeSelected: String = when(entity?.caFileType){
            ClazzAssignment.FILE_TYPE_DOC -> SelectFileView.SELECTION_MODE_DOC
            ClazzAssignment.FILE_TYPE_AUDIO -> SelectFileView.SELECTION_MODE_AUDIO
            ClazzAssignment.FILE_TYPE_VIDEO -> SelectFileView.SELECTION_MODE_VIDEO
            ClazzAssignment.FILE_TYPE_IMAGE -> SelectFileView.SELECTION_MODE_IMAGE
            else -> SelectFileView.SELECTION_MODE_ANY
        }

        val args = mutableMapOf(
                SelectFileView.ARG_MIMETYPE_SELECTED to modeSelected)

        navigateForResult(
                NavigateForResultOptions(this,
                        null, SelectFileView.VIEW_NAME, String::class,
                        String.serializer(), SAVED_STATE_KEY_URI,
                        arguments = args)
        )
    }

    fun handleAddTextClicked(){
        val args = mutableMapOf(TextAssignmentEditView.ASSIGNMENT_ID to entity?.caUid.toString())
        args[EDIT_ENABLED] = true.toString()
        navigateForResult(
                NavigateForResultOptions(this,
                        null,
                    TextAssignmentEditView.VIEW_NAME,
                    CourseAssignmentSubmission::class,
                    CourseAssignmentSubmission.serializer(),
                    SAVED_STATE_KEY_TEXT,
                    arguments = args))
    }


    companion object {

        @JvmStatic
        val SUBMISSION_POLICY_OPTIONS = mapOf(
            ClazzAssignment.SUBMISSION_POLICY_MULTIPLE_ALLOWED to MessageID.multiple_submission_allowed_submission_policy,
            ClazzAssignment.SUBMISSION_POLICY_SUBMIT_ALL_AT_ONCE to MessageID.submit_all_at_once_submission_policy)

        const val SAVED_STATE_KEY_URI = "URI"

        const val SAVED_STATE_KEY_TEXT = "TEXT"

        const val SAVED_STATE_ADD_SUBMISSION_LIST = "submissionList"

        //TODO: Add constants for keys that would be used for any One To Many Join helpers
        const val  SAVEDSTATE_KEY_CLAZZ_ASSIGNMENT = "ClassAssignment"
    }

}