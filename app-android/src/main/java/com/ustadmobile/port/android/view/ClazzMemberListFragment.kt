package com.ustadmobile.port.android.view

import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.paging.DataSource
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.MergeAdapter
import androidx.recyclerview.widget.RecyclerView
import com.toughra.ustadmobile.R
import com.toughra.ustadmobile.databinding.*
import com.ustadmobile.core.controller.ClazzMemberListPresenter
import com.ustadmobile.core.controller.UstadListPresenter
import com.ustadmobile.core.impl.UMAndroidUtil
import com.ustadmobile.core.util.ext.observeResult
import com.ustadmobile.core.util.ext.toListFilterOptions
import com.ustadmobile.core.view.ClazzMemberListView
import com.ustadmobile.core.view.PersonListView.Companion.ARG_FILTER_EXCLUDE_MEMBERSOFCLAZZ
import com.ustadmobile.core.view.UstadView.Companion.ARG_CODE_TABLE
import com.ustadmobile.core.view.UstadView.Companion.ARG_FILTER_BY_CLAZZUID
import com.ustadmobile.door.ext.asRepositoryLiveData
import com.ustadmobile.lib.db.entities.*
import com.ustadmobile.port.android.view.ext.navigateToPickEntityFromList
import com.ustadmobile.port.android.view.ext.setSelectedIfInList
import com.ustadmobile.port.android.view.util.ListHeaderRecyclerViewAdapter
import com.ustadmobile.port.android.view.util.PagedListSubmitObserver
import com.ustadmobile.port.android.view.util.PresenterViewLifecycleObserver
import com.ustadmobile.port.android.view.util.SelectablePagedListAdapter

class ClazzMemberListFragment() : UstadListViewFragment<Person, PersonWithClazzEnrolmentDetails>(),
        ClazzMemberListView, MessageIdSpinner.OnMessageIdOptionSelectedListener, View.OnClickListener {

    private var mPresenter: ClazzMemberListPresenter? = null

    override val listPresenter: UstadListPresenter<*, in PersonWithClazzEnrolmentDetails>?
        get() = mPresenter

    override var autoMergeRecyclerViewAdapter: Boolean = false

    override var studentList: DataSource.Factory<Int, PersonWithClazzEnrolmentDetails>? = null
        get() = field
        set(value) {
            val studentObserverVal = mStudentListObserver ?: return
            val repoDao = displayTypeRepo ?: return
            mCurrentStudentListLiveData?.removeObserver(studentObserverVal)
            mCurrentStudentListLiveData = value?.asRepositoryLiveData(repoDao)
            mCurrentStudentListLiveData?.observe(viewLifecycleOwner, studentObserverVal)
        }

    private val pendingStudentsObserver = object : Observer<PagedList<PersonWithClazzEnrolmentDetails>> {
        override fun onChanged(t: PagedList<PersonWithClazzEnrolmentDetails>?) {
            mPendingStudentListRecyclerViewAdapter?.submitList(t)
            mPendingStudentsHeaderRecyclerViewAdapter?.headerLayoutId = if (t != null && !t.isEmpty()) {
                R.layout.item_simple_list_header
            } else {
                0
            }
        }
    }

    override var pendingStudentList: DataSource.Factory<Int, PersonWithClazzEnrolmentDetails>? = null
        get() = field
        set(value) {
            val repoDao = displayTypeRepo ?: return

            mCurrentPendingStudentListLiveData?.removeObserver(pendingStudentsObserver)
            mCurrentStudentListLiveData = value?.asRepositoryLiveData(repoDao)
            mCurrentStudentListLiveData?.observe(viewLifecycleOwner, pendingStudentsObserver)
            field = value
        }


    private var mNewStudentListRecyclerViewAdapter: ListHeaderRecyclerViewAdapter? = null

    private var mStudentListRecyclerViewAdapter: ClazzMemberListRecyclerAdapter? = null

    private var mStudentListObserver: Observer<PagedList<PersonWithClazzEnrolmentDetails>>? = null

    private var mCurrentStudentListLiveData: LiveData<PagedList<PersonWithClazzEnrolmentDetails>>? = null

    private var mPendingStudentsHeaderRecyclerViewAdapter: ListHeaderRecyclerViewAdapter? = null

    private var mPendingStudentListRecyclerViewAdapter: PendingClazzEnrolmentListRecyclerAdapter? = null

    //private var mPendingStudentListObserver: Observer<PagedList<ClazzMemberWithPerson>>? = null

    private var mCurrentPendingStudentListLiveData: LiveData<PagedList<PersonWithClazzEnrolmentDetails>>? = null

    private var filterByClazzUid: Long = 0

    private val mOnClickAddStudent: View.OnClickListener = View.OnClickListener {
        navigateToPickNewMember(KEY_STUDENT_SELECTED)
    }

    private val mOnClickAddTeacher: View.OnClickListener = View.OnClickListener {
        navigateToPickNewMember(KEY_TEACHER_SELECTED)
    }

    override var addTeacherVisible: Boolean = false
        set(value) {
            field = value
            mUstadListHeaderRecyclerViewAdapter?.newItemVisible = value
        }

    override var addStudentVisible: Boolean = false
        set(value) {
            field = value
            mNewStudentListRecyclerViewAdapter?.newItemVisible = value
        }

    class ClazzMemberListViewHolder(val itemBinding: ItemClazzmemberListItemBinding) : RecyclerView.ViewHolder(itemBinding.root)

    class ClazzMemberListRecyclerAdapter(var presenter: ClazzMemberListPresenter?)
        : SelectablePagedListAdapter<PersonWithClazzEnrolmentDetails, ClazzMemberListViewHolder>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClazzMemberListViewHolder {
            val itemBinding = ItemClazzmemberListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            itemBinding.presenter = presenter
            itemBinding.selectablePagedListAdapter = this
            return ClazzMemberListViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ClazzMemberListViewHolder, position: Int) {
            val item = getItem(position)
            holder.itemBinding.personWithEnrolmentDetails = item
            holder.itemView.setSelectedIfInList(item, selectedItems, DIFF_CALLBACK)
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            presenter = null
        }
    }

    class PendingClazzEnrolmentListViewHolder(val itemBinding: ItemClazzmemberPendingListItemBinding) : RecyclerView.ViewHolder(itemBinding.root)

    class PendingClazzEnrolmentListRecyclerAdapter(var presenter: ClazzMemberListPresenter?) : PagedListAdapter<PersonWithClazzEnrolmentDetails, PendingClazzEnrolmentListViewHolder>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingClazzEnrolmentListViewHolder {
            val itemBinding = ItemClazzmemberPendingListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            itemBinding.presenter = presenter
            return PendingClazzEnrolmentListViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: PendingClazzEnrolmentListViewHolder, position: Int) {
            holder.itemBinding.clazzEnrolment = getItem(position)
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            presenter = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        filterByClazzUid = arguments?.getString(ARG_FILTER_BY_CLAZZUID)?.toLong() ?: 0
        mPresenter = ClazzMemberListPresenter(requireContext(), UMAndroidUtil.bundleToMap(arguments),
                this, di, viewLifecycleOwner)

        mDataRecyclerViewAdapter = ClazzMemberListRecyclerAdapter(mPresenter)
        val createNewText = requireContext().getString(R.string.add_a_teacher)
        mStudentListRecyclerViewAdapter = ClazzMemberListRecyclerAdapter(mPresenter).also {
            mStudentListObserver = PagedListSubmitObserver(it)
        }
        mUstadListHeaderRecyclerViewAdapter = ListHeaderRecyclerViewAdapter(mOnClickAddTeacher, createNewText,
                headerStringId = R.string.teachers_literal,
                headerLayoutId = R.layout.item_simple_list_header,
                filterOptions = ClazzMemberListPresenter.FILTER_OPTIONS.toListFilterOptions(requireContext(), di),
                onClickSort = this, sortOrderOption = mPresenter?.sortOptions?.get(0))
        val addStudentText = requireContext().getString(R.string.add_a_student)
        mNewStudentListRecyclerViewAdapter = ListHeaderRecyclerViewAdapter(mOnClickAddStudent,
                addStudentText, headerStringId = R.string.students,
                headerLayoutId = R.layout.item_simple_list_header)

        mPendingStudentListRecyclerViewAdapter = PendingClazzEnrolmentListRecyclerAdapter(mPresenter)
        mPendingStudentsHeaderRecyclerViewAdapter = ListHeaderRecyclerViewAdapter(null,
                "", R.string.pending_requests, headerLayoutId = 0)

        mMergeRecyclerViewAdapter = MergeAdapter(mUstadListHeaderRecyclerViewAdapter,
                mDataRecyclerViewAdapter, mNewStudentListRecyclerViewAdapter,
                mStudentListRecyclerViewAdapter, mPendingStudentsHeaderRecyclerViewAdapter,
                mPendingStudentListRecyclerViewAdapter)
        mDataBinding?.fragmentListRecyclerview?.adapter = mMergeRecyclerViewAdapter

        presenterLifecycleObserver = PresenterViewLifecycleObserver(mPresenter).also {
            viewLifecycleOwner.lifecycle.addObserver(it)
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navController = findNavController()
        navController.currentBackStackEntry?.savedStateHandle?.observeResult(this,
                Person::class.java, KEY_TEACHER_SELECTED) {
            val teacherAdded = it.firstOrNull() ?: return@observeResult
            mPresenter?.handleEnrolMember(teacherAdded, ClazzEnrolment.ROLE_TEACHER)
        }

        navController.currentBackStackEntry?.savedStateHandle?.observeResult(this,
                Person::class.java, KEY_STUDENT_SELECTED) {
            val studentAdded = it.firstOrNull() ?: return@observeResult
            mPresenter?.handleEnrolMember(studentAdded, ClazzEnrolment.ROLE_STUDENT)
        }

        super.onViewCreated(view, savedInstanceState)
        fabManager?.visible = false
    }

    private fun navigateToPickNewMember(keyName: String) {

        val bundle = if(keyName == KEY_TEACHER_SELECTED){
            bundleOf(ARG_FILTER_EXCLUDE_MEMBERSOFCLAZZ to filterByClazzUid.toString())
        }else{
            bundleOf(ARG_FILTER_EXCLUDE_MEMBERSOFCLAZZ to filterByClazzUid.toString(),
                    ARG_CODE_TABLE to Clazz.TABLE_ID.toString())
        }
        navigateToPickEntityFromList(Person::class.java, R.id.personlist_dest,
                bundle, keyName, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.findItem(R.id.menu_search).isVisible = true
    }

    private var presenterLifecycleObserver: PresenterViewLifecycleObserver? = null


    /**
     * OnClick function that will handle when the user clicks to create a new item
     */
    override fun onClick(view: View?) {
        super.onClick(view)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        mPresenter = null
        dbRepo = null
        presenterLifecycleObserver?.also {
            viewLifecycleOwner.lifecycle.removeObserver(it)
        }
        presenterLifecycleObserver = null
    }

    override val displayTypeRepo: Any?
        get() = dbRepo?.personDao

    companion object {
        const val KEY_TEACHER_SELECTED = "Person_Teacher"

        const val KEY_STUDENT_SELECTED = "Person_Student"

        val DIFF_CALLBACK: DiffUtil.ItemCallback<PersonWithClazzEnrolmentDetails> = object
            : DiffUtil.ItemCallback<PersonWithClazzEnrolmentDetails>() {
            override fun areItemsTheSame(oldItem: PersonWithClazzEnrolmentDetails,
                                         newItem: PersonWithClazzEnrolmentDetails): Boolean {
                return oldItem.personUid == newItem.personUid
            }

            override fun areContentsTheSame(oldItem: PersonWithClazzEnrolmentDetails,
                                            newItem: PersonWithClazzEnrolmentDetails): Boolean {
                return oldItem == newItem
            }
        }
    }
}