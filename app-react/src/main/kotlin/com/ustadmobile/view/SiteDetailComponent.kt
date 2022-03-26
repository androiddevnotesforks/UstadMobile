package com.ustadmobile.view

import com.ustadmobile.core.controller.SiteDetailPresenter
import com.ustadmobile.core.controller.UstadDetailPresenter
import com.ustadmobile.core.generated.locale.MessageID
import com.ustadmobile.core.view.SiteDetailView
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.ObserverFnWrapper
import com.ustadmobile.lib.db.entities.Site
import com.ustadmobile.lib.db.entities.SiteTermsWithLanguage
import com.ustadmobile.mui.components.GridSize
import com.ustadmobile.util.StyleManager
import com.ustadmobile.util.UmProps
import com.ustadmobile.util.ext.currentBackStackEntrySavedStateMap
import com.ustadmobile.view.ext.createInformation
import com.ustadmobile.view.ext.createListSectionTitle
import com.ustadmobile.view.ext.umItem
import react.RBuilder
import react.setState
import styled.css
import styled.styledDiv

class SiteDetailComponent(props: UmProps): UstadDetailComponent<Site>(props), SiteDetailView {

    override val viewNames: List<String>
        get() = listOf(SiteDetailView.VIEW_NAME)

    private var mPresenter: SiteDetailPresenter? = null

    override val detailPresenter: UstadDetailPresenter<*, *>?
        get() = mPresenter

    private var siteTermsWithLanguageList: List<SiteTermsWithLanguage> = listOf()

    private var currentSiteList: MutableList<Site> = mutableListOf()

    private val observer = ObserverFnWrapper<List<SiteTermsWithLanguage>>{
        if(it.isEmpty()) return@ObserverFnWrapper
        setState {
            siteTermsWithLanguageList = it
        }
    }

    override var siteTermsList: DoorDataSourceFactory<Int, SiteTermsWithLanguage>? = null
        set(value) {
            field = value
            val liveData = value?.getData(0,Int.MAX_VALUE)
            liveData?.removeObserver(observer)
            liveData?.observe(this, observer)
        }


    override var entity: Site? = null
        get() = field
        set(value) {
            field = value
            if(value != null && currentSiteList.firstOrNull{it.siteUid == value.siteUid} == null){
               currentSiteList.add(value)
            }
            setState {}
        }

    override fun onCreateView() {
        super.onCreateView()
        ustadComponentTitle = getString(MessageID.site)
        mPresenter = SiteDetailPresenter(this, arguments, this, this, di)
        mPresenter?.onCreate(navController.currentBackStackEntrySavedStateMap())
    }

    override fun RBuilder.render() {
        styledDiv {
            css {
                +StyleManager.contentContainer
                +StyleManager.defaultPaddingTop
            }

            currentSiteList.forEach { site ->
                umItem(GridSize.cells12, GridSize.cells4) {
                    createInformation("account_balance",
                        site.siteName,
                        getString(MessageID.name),
                        shrink = true
                    )

                    createInformation("meeting_room",
                        getString(if(site.guestLogin) MessageID.yes else MessageID.no),
                        getString(MessageID.guest_login_enabled),
                        shrink = true
                    )


                    createInformation("person_add_alt_1",
                        getString(if(site.registrationAllowed) MessageID.yes else MessageID.no),
                        getString(MessageID.registration_allowed),
                        shrink = true
                    )

                }
            }

            umItem {
                css(StyleManager.defaultDoubleMarginTop)

                createListSectionTitle(getString(MessageID.terms_and_policies))

                renderSiteTerms(
                    terms = siteTermsWithLanguageList,
                    withDelete = false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mPresenter = null
    }
}