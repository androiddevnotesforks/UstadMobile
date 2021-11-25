package com.ustadmobile.view

import com.ustadmobile.core.controller.OnSearchSubmitted
import com.ustadmobile.core.controller.TimeZoneListPresenter
import com.ustadmobile.core.view.TimeZoneListView
import com.ustadmobile.util.TimeZone
import com.ustadmobile.util.TimeZonesUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import react.RBuilder
import com.ustadmobile.util.*
import react.setState

class TimeZoneListComponent(mProps: UmProps): UstadBaseComponent<UmProps, UmState>(mProps) , TimeZoneListView,
    OnSearchSubmitted {

    private var mPresenter: TimeZoneListPresenter? = null

    private var timeZoneList: List<TimeZone> = TimeZonesUtil.getTimeZones()

    override val viewName: String
        get() = TimeZoneListView.VIEW_NAME

    override fun onCreateView() {
        super.onCreateView()
        mPresenter = TimeZoneListPresenter(this, arguments,
            this, di)
        mPresenter?.onCreate(mapOf())
    }

    override fun RBuilder.render() {
        renderZoneList(timeZoneList){ timezone ->
            mPresenter?.handleClickTimeZone(timezone.id)
        }

    }

    override fun onSearchSubmitted(text: String?) {
        if(text == null){
            return
        }

        GlobalScope.launch {
            val searchWords = text.split(Regex("\\s+"))
            val filteredItems = TimeZonesUtil.getTimeZones().filter { timeZone ->
                searchWords.any { timeZone.id.contains(it, ignoreCase = true) }
                        || searchWords.any { timeZone.timeName.contains(it, ignoreCase = true) }
            }
            withContext(Dispatchers.Main) {
               setState {
                   timeZoneList = filteredItems
               }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mPresenter?.onDestroy()
        mPresenter = null
    }
}

class ZonesListComponent(mProps: ListProps<TimeZone>):
    UstadSimpleList<ListProps<TimeZone>>(mProps){

    override fun RBuilder.renderListItem(item: TimeZone) {
        /*createItemWithIconTitleAndDescription("query_builder",item.name, item.timeName)*/
    }
}

fun RBuilder.renderZoneList(zones: List<TimeZone>,
                           onEntryClicked: ((TimeZone) -> Unit)? = null) = child(ZonesListComponent::class) {
    attrs.entries = zones
    attrs.onEntryClicked = onEntryClicked
    attrs.mainList = true
}