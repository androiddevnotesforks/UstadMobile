package com.ustadmobile.core.controller

import com.ustadmobile.core.util.SortOrderOption
import com.ustadmobile.core.view.SessionDetailListView
import com.ustadmobile.core.view.SessionsListView.Companion.ARG_CONTEXT_REGISTRATION
import com.ustadmobile.core.view.UstadView
import com.ustadmobile.door.DoorLifecycleOwner
import com.ustadmobile.door.doorMainDispatcher
import com.ustadmobile.lib.db.entities.PersonWithSessionDetailDisplay
import com.ustadmobile.lib.db.entities.UmAccount
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.kodein.di.DI

class SessionDetailListPresenter(context: Any, arguments: Map<String, String>,
                            view: SessionDetailListView,
                            di: DI, lifecycleOwner: DoorLifecycleOwner)
    : UstadListPresenter<SessionDetailListView, PersonWithSessionDetailDisplay>(
        context, arguments, view, di, lifecycleOwner) {

    private var contentEntryUid: Long = 0L
    private var selectedPersonUid: Long = 0L
    private var selectedSession: String = ""

    override suspend fun onCheckAddPermission(account: UmAccount?): Boolean {
        return false
    }

    override suspend fun onLoadFromDb() {
        super.onLoadFromDb()
        contentEntryUid = arguments[UstadView.ARG_ENTITY_UID]?.toLong() ?: 0
        selectedPersonUid = arguments[UstadView.ARG_PERSON_UID]?.toLong() ?: 0
        selectedSession = arguments[ARG_CONTEXT_REGISTRATION] ?: ""
        mLoggedInPersonUid = accountManager.activeAccount.personUid
        GlobalScope.launch(doorMainDispatcher()) {

            val person = db.personDao.findByUidAsync(selectedPersonUid)
            val entry = db.contentEntryDao.findByUidAsync(contentEntryUid)

            view.personWithContentTitle = "${person?.fullName()} - ${entry?.title}"

        }
        updateListOnView()


    }

    override fun onClickSort(sortOption: SortOrderOption) {
        super.onClickSort(sortOption)
        updateListOnView()
    }

    private fun updateListOnView() {
        view.list = repo.statementDao.findSessionDetailForPerson(contentEntryUid, mLoggedInPersonUid,
                selectedPersonUid, selectedSession)
    }

    override fun handleClickCreateNewFab() {

    }

    fun onClickPersonWithSessionDisplay(personWithStatementDisplay: PersonWithSessionDetailDisplay) {

    }

}
