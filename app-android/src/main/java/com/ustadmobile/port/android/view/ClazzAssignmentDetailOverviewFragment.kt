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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.toughra.ustadmobile.R
import com.toughra.ustadmobile.databinding.FragmentClazzAssignmentDetailOverviewBinding
import com.ustadmobile.core.account.UstadAccountManager
import com.ustadmobile.core.controller.ClazzAssignmentDetailOverviewPresenter
import com.ustadmobile.core.controller.DefaultContentEntryListItemListener
import com.ustadmobile.core.controller.UstadDetailPresenter
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.core.db.dao.ClazzWorkDao
import com.ustadmobile.core.util.ext.toStringMap
import com.ustadmobile.core.view.ClazzAssignmentDetailOverviewView
import com.ustadmobile.core.view.ListViewMode
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.lib.db.entities.ClazzAssignment
import com.ustadmobile.lib.db.entities.CommentsWithPerson
import com.ustadmobile.lib.db.entities.ContentEntryWithParentChildJoinAndStatusAndMostRecentContainer
import com.ustadmobile.port.android.util.ext.currentBackStackEntrySavedStateMap
import com.ustadmobile.port.android.view.ext.observeIfFragmentViewIsReady
import com.ustadmobile.port.android.view.util.PagedListSubmitObserver
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.on


interface ClazzAssignmentDetailOverviewFragmentEventHandler {

}

class ClazzAssignmentDetailOverviewFragment : UstadDetailFragment<ClazzAssignment>(),
        ClazzAssignmentDetailOverviewView, ClazzAssignmentDetailFragmentEventHandler,
        NewCommentHandler, OpenSheetListener {


    private var dbRepo: UmAppDatabase? = null
    private var mBinding: FragmentClazzAssignmentDetailOverviewBinding? = null

    private var mPresenter: ClazzAssignmentDetailOverviewPresenter? = null

    override val detailPresenter: UstadDetailPresenter<*, *>?
        get() = mPresenter

    val accountManager: UstadAccountManager by instance()

    private var detailMergerRecyclerView: RecyclerView? = null
    private var detailMergerRecyclerAdapter: ConcatAdapter? = null
    private var detailRecyclerAdapter: ClazzAssignmentBasicDetailRecyclerAdapter? = null

    private var scoreRecyclerAdapter: ScoreRecyclerAdapter? = null

    private var classCommentsHeadingRecyclerAdapter: SimpleHeadingRecyclerAdapter? = null
    private var classCommentsRecyclerAdapter: CommentsRecyclerAdapter? = null
    private var classCommentsObserver: Observer<PagedList<CommentsWithPerson>>? = null
    private var newClassCommentRecyclerAdapter: NewCommentRecyclerViewAdapter? = null
    private var classCommentsLiveData: LiveData<PagedList<CommentsWithPerson>>? = null


    private var privateCommentsHeadingRecyclerAdapter: SimpleHeadingRecyclerAdapter? = null
    private var privateCommentsRecyclerAdapter: CommentsRecyclerAdapter? = null
    private var privateCommentsObserver: Observer<PagedList<CommentsWithPerson>>? = null
    private var newPrivateCommentRecyclerAdapter: NewCommentRecyclerViewAdapter? = null
    private var privateCommentsLiveData: LiveData<PagedList<CommentsWithPerson>>? = null


    private var contentHeaderAdapter: SimpleHeadingRecyclerAdapter? = null
    private var contentRecyclerAdapter: ContentEntryListRecyclerAdapter? = null

    private var contentLiveData: LiveData<PagedList<
            ContentEntryWithParentChildJoinAndStatusAndMostRecentContainer>>? = null
    private val contentObserver = Observer<PagedList<
            ContentEntryWithParentChildJoinAndStatusAndMostRecentContainer>?> { t ->
        run {
            contentHeaderAdapter?.visible = t?.size ?: 0 > 0
            contentRecyclerAdapter?.submitList(t)
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView: View
        mBinding = FragmentClazzAssignmentDetailOverviewBinding.inflate(inflater, container, false).also {
            rootView = it.root
        }

        dbRepo = on(accountManager.activeAccount).direct.instance(tag = UmAppDatabase.TAG_REPO)

        detailMergerRecyclerView =
                rootView.findViewById(R.id.fragment_clazz_assignment_detail_overview)

        // 1
        detailRecyclerAdapter = ClazzAssignmentBasicDetailRecyclerAdapter()

        // 2
        contentHeaderAdapter = SimpleHeadingRecyclerAdapter(getText(R.string.content).toString()).apply {
            visible = false
        }

        //3
        contentRecyclerAdapter = ContentEntryListRecyclerAdapter(
                DefaultContentEntryListItemListener(context = requireContext(), di = di),
                ListViewMode.BROWSER.toString(), viewLifecycleOwner, di)

        // 4 score
        scoreRecyclerAdapter = ScoreRecyclerAdapter()


        // 5 class
        classCommentsHeadingRecyclerAdapter = SimpleHeadingRecyclerAdapter(
                getText(R.string.class_comments).toString()
        ).apply {
            visible = false
        }

        //12 - Class comments list + new comment
        classCommentsRecyclerAdapter = CommentsRecyclerAdapter().also {
            this.classCommentsObserver = PagedListSubmitObserver(it)
        }

        newClassCommentRecyclerAdapter = NewCommentRecyclerViewAdapter(this,this,
                requireContext().getString(R.string.add_class_comment),
                true, ClazzAssignment.TABLE_ID, entity?.caUid?:0L,
                0, accountManager.activeAccount.personUid).apply {
                    visible = false
        }

        // 13 - Private
        privateCommentsHeadingRecyclerAdapter = SimpleHeadingRecyclerAdapter(
                getText(R.string.private_comments).toString()
        ).apply {
            visible = false
        }

        //16 - Private comments list
        privateCommentsRecyclerAdapter = CommentsRecyclerAdapter().also{
            privateCommentsObserver = PagedListSubmitObserver(it)
        }

        //17 - New Private comments section:
        newPrivateCommentRecyclerAdapter = NewCommentRecyclerViewAdapter(
                this, this,
                requireContext().getString(R.string.add_private_comment), false, ClazzAssignment.TABLE_ID,
                entity?.caUid?:0L, 0, accountManager.activeAccount.personUid
        ).apply{
                    visible = false
        }

        mPresenter = ClazzAssignmentDetailOverviewPresenter(requireContext(),
                arguments.toStringMap(), this, viewLifecycleOwner, di)

        detailMergerRecyclerAdapter = ConcatAdapter(detailRecyclerAdapter, contentHeaderAdapter,
            contentRecyclerAdapter, scoreRecyclerAdapter, classCommentsHeadingRecyclerAdapter,
            classCommentsRecyclerAdapter, newClassCommentRecyclerAdapter, privateCommentsHeadingRecyclerAdapter,
            privateCommentsRecyclerAdapter, newPrivateCommentRecyclerAdapter)
        detailMergerRecyclerView?.adapter = detailMergerRecyclerAdapter
        detailMergerRecyclerView?.layoutManager = LinearLayoutManager(requireContext())


        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        mPresenter?.onCreate(findNavController().currentBackStackEntrySavedStateMap())
    }



    override fun onDestroyView() {
        super.onDestroyView()
        mBinding = null
        mPresenter = null
        entity = null


        detailMergerRecyclerView?.adapter = null
        contentHeaderAdapter = null
        contentRecyclerAdapter = null
        contentLiveData = null
        scoreRecyclerAdapter = null

        privateCommentsLiveData = null
        classCommentsLiveData = null
        newPrivateCommentRecyclerAdapter = null
        classCommentsRecyclerAdapter = null
        privateCommentsRecyclerAdapter = null
        newClassCommentRecyclerAdapter = null
        classCommentsHeadingRecyclerAdapter = null
        privateCommentsHeadingRecyclerAdapter = null

    }

    override var clazzAssignmentContent: DataSource.Factory<Int, ContentEntryWithParentChildJoinAndStatusAndMostRecentContainer>? = null
        set(value) {
            contentLiveData?.removeObserver(contentObserver)
            contentLiveData = value?.asRepositoryLiveData(ClazzWorkDao)
            field = value
            contentLiveData?.observeIfFragmentViewIsReady(this, contentObserver)
        }


    override var timeZone: String? = null
        get() = field
        set(value) {
            field = value
            detailRecyclerAdapter?.timeZone = value
        }

    override var clazzAssignmentClazzComments: DataSource.Factory<Int, CommentsWithPerson>? = null
        set(value) {
            val dvRepoVal = dbRepo?: return
            val publicCommentsObserverVal = this.classCommentsObserver
                    ?:return
            classCommentsLiveData?.removeObserver(publicCommentsObserverVal)
            classCommentsLiveData = value?.asRepositoryLiveData(dvRepoVal.commentsDao)
            classCommentsLiveData?.observeIfFragmentViewIsReady(this, publicCommentsObserverVal)
            field = value
        }
    override var clazzAssignmentPrivateComments: DataSource.Factory<Int, CommentsWithPerson>? = null
        set(value) {
            val dbRepoVal = dbRepo?: return
            val privateCommentsObserverVal = privateCommentsObserver?:return
            privateCommentsLiveData?.removeObserver(privateCommentsObserverVal)
            privateCommentsLiveData = value?.asRepositoryLiveData(dbRepoVal.commentsDao)
            privateCommentsLiveData?.observeIfFragmentViewIsReady(this, privateCommentsObserverVal)
            field = value
        }

    override var showPrivateComments: Boolean = false

    override var entity: ClazzAssignment? = null
        get() = field
        set(value) {
            field = value
            detailRecyclerAdapter?.clazzAssignment = value
            detailRecyclerAdapter?.visible = true

            newClassCommentRecyclerAdapter?.entityUid = entity?.caUid?:0L
            newPrivateCommentRecyclerAdapter?.entityUid = entity?.caUid?:0L

            newPrivateCommentRecyclerAdapter?.visible = showPrivateComments
            privateCommentsHeadingRecyclerAdapter?.visible = showPrivateComments

            newClassCommentRecyclerAdapter?.visible = value?.caClassCommentEnabled ?: false
            classCommentsHeadingRecyclerAdapter?.visible = value?.caClassCommentEnabled ?: false

        }

    override fun addNewComment2(view: View, entityType: Int, entityUid: Long, comment: String, public: Boolean, to: Long, from: Long) {
        (view.parent as View).findViewById<EditText>(R.id.item_comment_new_comment_et).setText("")
        mPresenter?.addComment(entityType, entityUid, comment, public, to, from)
    }

    override fun open(publicComment: Boolean) {
        val adapter = if(publicComment){
            newClassCommentRecyclerAdapter
        }else{
            newPrivateCommentRecyclerAdapter
        }
        val entryAddOption = CommentsBottomSheet(adapter)
        entryAddOption.show(childFragmentManager, entryAddOption.tag)
    }

}