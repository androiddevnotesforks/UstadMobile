package com.ustadmobile.view

import com.ccfraser.muirwik.components.*
import com.ustadmobile.navigation.RouteManager.findDestination
import com.ustadmobile.util.StyleManager.displayProperty
import com.ustadmobile.util.StyleManager.tabsContainer
import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RState
import react.setState
import styled.css
import styled.styledDiv

interface TabsProps: RProps {
    var tabs: List<UstadTab>
    var showTabs: Boolean
}

data class UstadTab(var viewName: String, val args: Map<String,String>, var title: String)


class  TabsComponent(mProps: TabsProps): UstadBaseComponent<TabsProps,RState>(mProps){

    private lateinit var selectedView: String

    override val viewName: String?
        get() = null

    private val tabChangeListener:(Any)-> Unit = {
        setState {
            selectedView = it.toString()
        }
    }

    override fun RState.init(props: TabsProps) {
        selectedView = props.tabs.first().viewName
    }

    override fun RBuilder.render() {
        mAppBar(position = MAppBarPosition.static) {
            css{
                display = displayProperty(props.showTabs)
            }
            mTabs(selectedView,
                scrollButtons = MTabScrollButtons.auto,
                variant = MTabVariant.scrollable,
                onChange = { _, value ->
                    tabChangeListener(value)
                }) {
                css {
                    padding = "0 20px 0 20px"
                }
                attrs.asDynamic().id = "um-tabs"
                props.tabs.forEachIndexed { _, it ->
                    mTab(it.title, it.viewName) {
                        css {
                            display = Display.block
                            width = LinearDimension("100%")
                        }
                    }
                }
            }
        }
        val selectedTab = props.tabs.first { it.viewName == selectedView}
        val component = findDestination(selectedView)?.component

        styledDiv {
            css(tabsContainer)
            if(component != null){
                child(component){
                    attrs.asDynamic().arguments = selectedTab.args
                }
            }
        }
    }
}

fun RBuilder.renderTabs(tabs: List<UstadTab>, showTabs: Boolean = true) = child(TabsComponent::class) {
    attrs.tabs = tabs
    attrs.showTabs = showTabs
}