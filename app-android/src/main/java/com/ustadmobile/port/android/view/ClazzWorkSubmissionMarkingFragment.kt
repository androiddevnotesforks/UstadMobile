package com.ustadmobile.port.android.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.paging.DataSource
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.MergeAdapter
import androidx.recyclerview.widget.RecyclerView
import com.toughra.ustadmobile.R
import com.toughra.ustadmobile.databinding.FragmentClazzWorkSubmissionMarkingBinding
import com.ustadmobile.core.account.UstadAccountManager
import com.ustadmobile.core.controller.ClazzWorkSubmissionMarkingPresenter
import com.ustadmobile.core.controller.UstadEditPresenter
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.core.util.ext.toStringMap
import com.ustadmobile.core.view.ClazzWorkSubmissionMarkingView
import com.ustadmobile.door.DoorMutableLiveData
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.lib.db.entities.*
import com.ustadmobile.lib.db.entities.ClazzWork.Companion.CLAZZ_WORK_SUBMISSION_TYPE_NONE
import com.ustadmobile.lib.db.entities.ClazzWork.Companion.CLAZZ_WORK_SUBMISSION_TYPE_QUIZ
import com.ustadmobile.lib.db.entities.ClazzWork.Companion.CLAZZ_WORK_SUBMISSION_TYPE_SHORT_TEXT
import com.ustadmobile.port.android.util.ext.currentBackStackEntrySavedStateMap
import com.ustadmobile.port.android.view.ext.observeIfFragmentViewIsReady
import com.ustadmobile.port.android.view.util.PagedListSubmitObserver
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.on


class ClazzWorkSubmissionMarkingFragment: UstadEditFragment<ClazzMemberAndClazzWorkWithSubmission>(),
        ClazzWorkSubmissionMarkingView, NewCommentHandler, SimpleButtonHandler,
        SimpleTwoButtonHandler{

    internal var mBinding: FragmentClazzWorkSubmissionMarkingBinding? = null

    private var mPresenter: ClazzWorkSubmissionMarkingPresenter? = null

    override val mEditPresenter: UstadEditPresenter<*, ClazzMemberAndClazzWorkWithSubmission>?
        get() = mPresenter

    private lateinit var dbRepo : UmAppDatabase

    override val viewContext: Any
        get() = requireContext()

    private var submissionHeadingRecyclerAdapter: SimpleHeadingRecyclerAdapter?= null
    private var submissionResultRecyclerAdapter: SubmissionResultRecyclerAdapter? = null

    private var markingEditRecyclerAdapter
            : ClazzWorkSubmissionScoreEditRecyclerAdapter? = null
    private var submissionFreeTextRecyclerAdapter
            : SubmissionTextEntryWithResultRecyclerAdapter? = null

    private var markingHeadingRecyclerAdapter: SimpleHeadingRecyclerAdapter? = null
    private var questionsHeadingRecyclerAdapter: SimpleHeadingRecyclerAdapter?= null
    private var quizViewRecyclerAdapter: ClazzWorkQuestionAndOptionsWithResponseViewRecyclerAdapter? = null
    private val quizQuestionAndResponseObserver = Observer<List<ClazzWorkQuestionAndOptionWithResponse>?> {
        t -> quizViewRecyclerAdapter?.submitList(t)
    }
    private var quizEditRecyclerAdapter: ClazzWorkQuestionAndOptionsWithResponseEditRecyclerAdapter? = null

    private var privateCommentsHeadingRecyclerAdapter: SimpleHeadingRecyclerAdapter? = null
    private var privateCommentsObserver: Observer<PagedList<CommentsWithPerson>>? = null
    private var privateCommentsRecyclerAdapter: CommentsRecyclerAdapter? = null
    private var privateCommentsLiveData: LiveData<PagedList<CommentsWithPerson>>? = null
    private var newPrivateCommentRecyclerAdapter: NewCommentRecyclerViewAdapter? = null
    private var privateCommentsMergerRecyclerAdapter: MergeAdapter? = null
    private var submitWithMetricsRecyclerAdapter: ClazzWorkSubmissionMarkingSubmitWithMetricsRecyclerAdapter ? = null
    private var recordForStudentButtonRecyclerAdapter: SimpleButtonRecyclerAdapter? = null
    private var simpleTwoButtonRecyclerAdapter: SimpleTwoButtonRecyclerAdapter? = null

    private var detailMergerRecyclerAdapter: MergeAdapter? = null
    private var detailMergerRecyclerView: RecyclerView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView: View
        mBinding = FragmentClazzWorkSubmissionMarkingBinding.inflate(inflater, container,
                false).also {
            rootView = it.root
        }

        val accountManager: UstadAccountManager by instance()
        dbRepo = on(accountManager.activeAccount).direct.instance(tag = UmAppDatabase.TAG_REPO)


        detailMergerRecyclerView = rootView.findViewById(R.id.fragment_clazz_work_submission_marking_rv)

        mPresenter = ClazzWorkSubmissionMarkingPresenter(requireContext(), arguments.toStringMap(),
                this, di, this)

        val clazzWorkWithSubmission: ClazzWorkWithSubmission =
                ClazzWorkWithSubmission().generateWithClazzWorkAndClazzWorkSubmission(
                        entity?.clazzWork?: ClazzWork(), entity?.submission
                )

        submitWithMetricsRecyclerAdapter =
                ClazzWorkSubmissionMarkingSubmitWithMetricsRecyclerAdapter(
                        clazzWorkMetrics, entity, mPresenter,false, isMarkingFinished)


        quizEditRecyclerAdapter = ClazzWorkQuestionAndOptionsWithResponseEditRecyclerAdapter()

        recordForStudentButtonRecyclerAdapter =
                SimpleButtonRecyclerAdapter(getText(R.string.record_for_student).toString(),
                        this)
        recordForStudentButtonRecyclerAdapter?.isOutline = true
        recordForStudentButtonRecyclerAdapter?.visible = true

        simpleTwoButtonRecyclerAdapter = SimpleTwoButtonRecyclerAdapter(
                getText(R.string.submit).toString(),getText(R.string.cancel).toString(),
                this)
        simpleTwoButtonRecyclerAdapter?.visible = false

        submissionResultRecyclerAdapter = SubmissionResultRecyclerAdapter(
                        clazzWorkWithSubmission)
        submissionResultRecyclerAdapter?.visible = false

        markingEditRecyclerAdapter = ClazzWorkSubmissionScoreEditRecyclerAdapter(entity)
        markingEditRecyclerAdapter?.visible = true

        submissionFreeTextRecyclerAdapter = SubmissionTextEntryWithResultRecyclerAdapter()
        submissionFreeTextRecyclerAdapter?.markingMode = true


        submissionHeadingRecyclerAdapter = SimpleHeadingRecyclerAdapter(
                getText(R.string.submission).toString())
        submissionHeadingRecyclerAdapter?.visible = false

        questionsHeadingRecyclerAdapter = SimpleHeadingRecyclerAdapter(
                getText(R.string.questions).toString())
        questionsHeadingRecyclerAdapter?.visible = true

        markingHeadingRecyclerAdapter = SimpleHeadingRecyclerAdapter(getText(R.string.marking).toString())
        markingHeadingRecyclerAdapter?.visible = true

        privateCommentsHeadingRecyclerAdapter = SimpleHeadingRecyclerAdapter(
                getText(R.string.private_comments).toString()
        )
        privateCommentsHeadingRecyclerAdapter?.visible = true


        newPrivateCommentRecyclerAdapter = NewCommentRecyclerViewAdapter(this,
                requireContext().getString(R.string.add_private_comment), false, ClazzWork.CLAZZ_WORK_TABLE_ID,
                entity?.clazzWork?.clazzWorkUid?:0L, entity?.clazzMemberPersonUid?:0L
        )
        newPrivateCommentRecyclerAdapter?.visible = true

        quizViewRecyclerAdapter = ClazzWorkQuestionAndOptionsWithResponseViewRecyclerAdapter()

        privateCommentsRecyclerAdapter = CommentsRecyclerAdapter().also{
            privateCommentsObserver = PagedListSubmitObserver(it)
        }

        privateCommentsMergerRecyclerAdapter = MergeAdapter(
                privateCommentsHeadingRecyclerAdapter, privateCommentsRecyclerAdapter,
                newPrivateCommentRecyclerAdapter)

        detailMergerRecyclerAdapter = MergeAdapter(
                submissionHeadingRecyclerAdapter, submissionFreeTextRecyclerAdapter,
                quizViewRecyclerAdapter, quizEditRecyclerAdapter,
                recordForStudentButtonRecyclerAdapter, simpleTwoButtonRecyclerAdapter,
                markingHeadingRecyclerAdapter, markingEditRecyclerAdapter,
                privateCommentsMergerRecyclerAdapter, submitWithMetricsRecyclerAdapter
        )
        detailMergerRecyclerView?.adapter = detailMergerRecyclerAdapter
        detailMergerRecyclerView?.layoutManager = LinearLayoutManager(requireContext())

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()
        mPresenter?.onCreate(navController.currentBackStackEntrySavedStateMap())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mBinding = null
        mPresenter = null
        entity = null
        submissionHeadingRecyclerAdapter = null
        submissionResultRecyclerAdapter = null
        markingEditRecyclerAdapter = null
        submissionFreeTextRecyclerAdapter = null
        markingHeadingRecyclerAdapter = null
        questionsHeadingRecyclerAdapter = null
        quizViewRecyclerAdapter = null
        quizEditRecyclerAdapter = null
        privateCommentsHeadingRecyclerAdapter = null
        privateCommentsRecyclerAdapter = null
        newPrivateCommentRecyclerAdapter = null
        privateCommentsMergerRecyclerAdapter = null
        submitWithMetricsRecyclerAdapter = null
        recordForStudentButtonRecyclerAdapter = null
        simpleTwoButtonRecyclerAdapter = null
        detailMergerRecyclerAdapter = null
        detailMergerRecyclerView = null

    }

    override var entity: ClazzMemberAndClazzWorkWithSubmission? = null
        set(value) {
            field = value

            newPrivateCommentRecyclerAdapter?.entityUid = value?.clazzWork?.clazzWorkUid?:0L
            newPrivateCommentRecyclerAdapter?.commentTo = value?.clazzMemberPersonUid?:0L
            newPrivateCommentRecyclerAdapter?.commentFrom = 0L
            newPrivateCommentRecyclerAdapter?.visible = true

            ustadFragmentTitle = value?.person?.fullName()?:""

            val submission = entity?.submission
            //Don't show the button if submission exists or submission is not required.
            markingEditRecyclerAdapter?.clazzWorkVal = value

            //TODO: this should not be needed - see comment on ClazzWorkSubmissionScoreEditRecyclerAdapter
            markingEditRecyclerAdapter?.notifyDataSetChanged()


            //Show submission heading and record for student
            submissionHeadingRecyclerAdapter?.visible = true

            val clazzWorkWithSubmission: ClazzWorkWithSubmission =
                    ClazzWorkWithSubmission().generateWithClazzWorkAndClazzWorkSubmission(
                            entity?.clazzWork?: ClazzWork(), entity?.submission)

            submissionResultRecyclerAdapter?.submitList(listOf(clazzWorkWithSubmission))

            //If there is a submission
            if(submission != null && submission.clazzWorkSubmissionUid != 0L){

                //Don't show record for student
                //TODO: control the visibility of this button on the presenter
                recordForStudentButtonRecyclerAdapter?.visible = false
                //Show submission heading if type not none
                if(value?.clazzWork?.clazzWorkSubmissionType != CLAZZ_WORK_SUBMISSION_TYPE_NONE){
                    submissionHeadingRecyclerAdapter?.visible = true
                }
            }else{ //No submission.
                //Show marking if type not none and dont show record for student.
                if(value?.clazzWork?.clazzWorkSubmissionType == CLAZZ_WORK_SUBMISSION_TYPE_NONE){
                    recordForStudentButtonRecyclerAdapter?.visible = false
                }
            }

            when (value?.clazzWork?.clazzWorkSubmissionType) {
                CLAZZ_WORK_SUBMISSION_TYPE_NONE -> {

                    //Hide submission heading and record button
                    submissionHeadingRecyclerAdapter?.visible = false
                    recordForStudentButtonRecyclerAdapter?.visible = false

                }
                CLAZZ_WORK_SUBMISSION_TYPE_SHORT_TEXT -> {

                    if(submission == null){ // if not submission
                        //Don't show response
                        submissionFreeTextRecyclerAdapter?.submitList(listOf())
                    }else {
                        //Show response
                        submissionFreeTextRecyclerAdapter?.submitList(listOf(clazzWorkWithSubmission))
                    }
                }

                else -> {
                    submissionFreeTextRecyclerAdapter?.submitList(listOf())
                }
            }

        }

    override var privateComments: DataSource.Factory<Int, CommentsWithPerson>? = null
        set(value) {
            field = value
            val privateCommentsObserverVal = privateCommentsObserver?:return
            privateCommentsLiveData?.removeObserver(privateCommentsObserverVal)
            privateCommentsLiveData = value?.asRepositoryLiveData(dbRepo.commentsDao)
            privateCommentsLiveData?.observeIfFragmentViewIsReady(this, privateCommentsObserverVal)
        }


    override var quizSubmissionViewData
            : DoorMutableLiveData<List<ClazzWorkQuestionAndOptionWithResponse>>? = null
        set(value) {
            field?.removeObserver(quizQuestionAndResponseObserver)
            field = value
            value?.observeIfFragmentViewIsReady(this, quizQuestionAndResponseObserver)
        }


    override var quizSubmissionEditData
            : DoorMutableLiveData<List<ClazzWorkQuestionAndOptionWithResponse>>? = null

    override var isMarkingFinished: Boolean = false

    override var clazzWorkMetrics: ClazzWorkWithMetrics? = null
        set(value) {

            field = value
            submitWithMetricsRecyclerAdapter?.visible = true
            submitWithMetricsRecyclerAdapter?.showNext = isMarkingFinished
            submitWithMetricsRecyclerAdapter?.submitList(listOf(clazzWorkMetrics))

        }

    override var fieldsEnabled: Boolean = true

    override fun addNewComment2(view: View, entityType: Int, entityUid: Long, comment: String,
                                public: Boolean, to: Long, from: Long) {
        (view.parent as View).findViewById<EditText>(R.id.item_comment_new_comment_et).setText("")
        mPresenter?.addComment(entityType, entityUid, comment, public, to, from)
    }

    //On click "Record for student" button
    //TODO: Handle this event in the presenter, let it do the thinking and set properties on the view
    override fun onClickButton(view: View) {

        simpleTwoButtonRecyclerAdapter?.visible = true
        recordForStudentButtonRecyclerAdapter?.visible = false
        submissionFreeTextRecyclerAdapter?.markingMode = false

        if(entity?.clazzWork?.clazzWorkSubmissionType ==
                CLAZZ_WORK_SUBMISSION_TYPE_SHORT_TEXT ){

            submissionFreeTextRecyclerAdapter?.visible = true
            submissionFreeTextRecyclerAdapter?.markingMode = false
            mPresenter?.createSubmissionIfDoesNotExist()

        }else if(entity?.clazzWork?.clazzWorkSubmissionType ==
                CLAZZ_WORK_SUBMISSION_TYPE_QUIZ){
            quizEditRecyclerAdapter?.submitList(
                    quizSubmissionEditData?.value)
        }
        recordForStudentButtonRecyclerAdapter?.visible = false
    }

    override var updatedSubmission: ClazzWorkWithSubmission? = null

        set(value) {
            field = value
            submissionFreeTextRecyclerAdapter?.markingMode = false
            submissionFreeTextRecyclerAdapter?.submitList(listOf(value))
        }

    //Submit class work on behalf of student
    override fun onClickPrimary(view: View) {
        simpleTwoButtonRecyclerAdapter?.visible = false
        submissionFreeTextRecyclerAdapter?.markingMode = true
        quizEditRecyclerAdapter?.submitList(listOf())
        mPresenter?.handleClickSubmitOnBehalf()
        recordForStudentButtonRecyclerAdapter?.visible = false
        quizViewRecyclerAdapter?.submitList(quizSubmissionEditData?.value)
        val clazzWorkWithSubmission: ClazzWorkWithSubmission =
                ClazzWorkWithSubmission().generateWithClazzWorkAndClazzWorkSubmission(
                        entity?.clazzWork?: ClazzWork(), entity?.submission)
        submissionFreeTextRecyclerAdapter?.submitList(listOf(clazzWorkWithSubmission))
    }

    //On click cancel for student recording on their behalf
    override fun onClickSecondary(view: View) {
        submissionFreeTextRecyclerAdapter?.markingMode = true
        submissionFreeTextRecyclerAdapter?.submitList(listOf())
        quizEditRecyclerAdapter?.submitList(listOf())
        simpleTwoButtonRecyclerAdapter?.visible = false
        recordForStudentButtonRecyclerAdapter?.visible = true
    }
}