package com.ustadmobile.view

import com.ustadmobile.core.generated.locale.MessageID
import com.ustadmobile.core.view.ContentEntryList2View
import com.ustadmobile.core.view.ContentEntryList2View.Companion.ARG_DISPLAY_CONTENT_BY_DOWNLOADED
import com.ustadmobile.core.view.ContentEntryList2View.Companion.ARG_DISPLAY_CONTENT_BY_OPTION
import com.ustadmobile.core.view.ContentEntryList2View.Companion.ARG_DISPLAY_CONTENT_BY_PARENT
import com.ustadmobile.core.view.ContentEntryListTabsView
import com.ustadmobile.core.view.UstadView
import com.ustadmobile.core.view.UstadView.Companion.ARG_PARENT_ENTRY_UID
import com.ustadmobile.core.view.UstadView.Companion.MASTER_SERVER_ROOT_ENTRY_UID
import com.ustadmobile.util.ext.toArgumentsMap
import react.RBuilder
import react.setState
import com.ustadmobile.util.*

class ContentEntryListTabsComponent(mProps: UmProps) :UstadBaseComponent<UmProps, UmState>(mProps),
    ContentEntryListTabsView {

    override val viewName: String
        get() = ContentEntryListTabsView.VIEW_NAME

    private var tabsToRender: List<UstadTab>? = null

    override fun onCreateView() {
        super.onCreateView()
        title = getString(MessageID.contents)
        val parentUid = arguments[ARG_PARENT_ENTRY_UID]?.toLong() ?: MASTER_SERVER_ROOT_ENTRY_UID

        val defArgs = "?${ARG_PARENT_ENTRY_UID}=" +
                "$parentUid&${ARG_DISPLAY_CONTENT_BY_OPTION}="

        setState {
            tabsToRender = listOf(
                UstadTab(0, ContentEntryList2View.VIEW_NAME,
                    "$defArgs${ARG_DISPLAY_CONTENT_BY_PARENT}".toArgumentsMap(),
                    getString(MessageID.libraries)),
                UstadTab(1,ContentEntryList2View.VIEW_NAME,
                    "$defArgs${ARG_DISPLAY_CONTENT_BY_DOWNLOADED}".toArgumentsMap(),
                    getString(MessageID.downloaded))
            )
        }

    }


    override fun RBuilder.render() {
        tabsToRender?.let {
            renderTabs(it, true, arguments[UstadView.ARG_ACTIVE_TAB_INDEX]?.toInt() ?: 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabsToRender = null
    }
}