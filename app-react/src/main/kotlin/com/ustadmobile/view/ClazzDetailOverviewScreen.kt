package com.ustadmobile.view

import com.ustadmobile.core.generated.locale.MessageID
import com.ustadmobile.core.hooks.useStringsXml
import com.ustadmobile.core.impl.locale.entityconstants.ScheduleConstants
import com.ustadmobile.core.util.MS_PER_HOUR
import com.ustadmobile.core.util.MS_PER_MIN
import com.ustadmobile.core.viewmodel.ClazzDetailOverviewUiState
import com.ustadmobile.hooks.useFormattedTime
import com.ustadmobile.lib.db.entities.*
import com.ustadmobile.mui.components.UstadDetailField
import com.ustadmobile.view.components.UstadBlankIcon
import csstype.Padding
import csstype.px
import mui.icons.material.*
import mui.material.*
import mui.material.Stack
import mui.material.List
import mui.material.StackDirection
import mui.system.responsive
import mui.system.sx
import react.*

external interface ClazzDetailOverviewProps : Props {

    var uiState: ClazzDetailOverviewUiState

    var onClickClassCode: (String) -> Unit

    var onClickCourseBlock: (CourseBlockWithCompleteEntity) -> Unit

}

val ClazzDetailOverviewComponent2 = FC<ClazzDetailOverviewProps> { props ->

    val strings = useStringsXml()

    val numMembers = strings[MessageID.x_teachers_y_students]
        .replace("%1\$d", (props.uiState.clazz?.numTeachers ?: 0).toString())
        .replace("%2\$d", (props.uiState.clazz?.numStudents ?: 0).toString())

    val clazzStartTime = useFormattedTime(
        timeInMillisSinceMidnight = (props.uiState.clazz?.clazzStartTime ?: 0).toInt(),
    )

    val clazzEndTime = useFormattedTime(
        timeInMillisSinceMidnight = (props.uiState.clazz?.clazzEndTime ?: 0).toInt(),
    )

    Container {
        maxWidth = "lg"

        Stack {
            direction = responsive(StackDirection.column)
            spacing = responsive(10.px)

            Typography{
                + (props.uiState.clazz?.clazzDesc ?: "")
            }

            UstadDetailField {
                icon = Group.create()
                valueText = numMembers
                labelText = strings[MessageID.members]
            }

            if (props.uiState.clazzCodeVisible) {
                UstadDetailField {
                    icon = Login.create()
                    valueText = props.uiState.clazz?.clazzCode ?: ""
                    labelText = strings[MessageID.class_code]
                    onClick = {
                        props.onClickClassCode(props.uiState.clazz?.clazzCode ?: "")
                    }
                }
            }

            if (props.uiState.clazzSchoolUidVisible){
                TextImageRow {
                    icon = mui.icons.material.School
                    text = props.uiState.clazz?.clazzSchool?.schoolName ?: ""
                }
            }

            if (props.uiState.clazzDateVisible){
                TextImageRow {
                    icon = Event
                    text = "$clazzStartTime - $clazzEndTime"
                }
            }

            if (props.uiState.clazzHolidayCalendarVisible){
                TextImageRow {
                    icon = Event
                    text = props.uiState.clazz?.clazzHolidayCalendar?.umCalendarName ?: ""
                }
            }

            List {
                Typography {
                    sx {
                        padding = Padding(
                            top = 0.px,
                            bottom = 0.px,
                            left = 44.px,
                            right = 0.px
                        )
                    }
                    + strings[MessageID.schedule]
                }

                props.uiState.scheduleList.forEach { schedule ->
                    val fromTimeFormatted = useFormattedTime(
                        timeInMillisSinceMidnight = schedule.sceduleStartTime.toInt(),
                    )

                    val toTimeFormatted = useFormattedTime(
                        timeInMillisSinceMidnight = schedule.scheduleEndTime.toInt(),
                    )

                    val text = "${strings[ScheduleConstants.SCHEDULE_FREQUENCY_MESSAGE_ID_MAP[schedule.scheduleFrequency] ?: 0]} " +
                            " ${strings[ScheduleConstants.DAY_MESSAGE_ID_MAP[schedule.scheduleDay] ?: 0]  } " +
                            " $fromTimeFormatted - $toTimeFormatted "

                    ListItem{
                        sx {
                            padding = Padding(
                                top = 0.px,
                                bottom = 0.px,
                                left = 52.px,
                                right = 0.px
                            )
                        }

                        UstadBlankIcon()

                        ListItemText{
                            primary = ReactNode(text)
                        }
                    }
                }
            }

            List {
                props.uiState.courseBlockList.forEach { courseBlockItem ->
                    CourseBlockListItem {
                        courseBlock = courseBlockItem
                        uiState = props.uiState
                        onClickItem = props.onClickCourseBlock
                    }
                }
            }
        }
    }
}

external interface TextImageRowProps : Props {

    var icon: SvgIconComponent

    var text: String

}

private val TextImageRow = FC<TextImageRowProps> { props ->

    Stack {
        direction = responsive(StackDirection.row)
        spacing = responsive(10.px)
        sx {
            padding = Padding(
                top = 10.px,
                bottom = 10.px,
                left = 38.px,
                right = 0.px
            )
        }

        Icon{
            + props.icon.create()
        }

        Typography {
            + props.text
        }
    }
}

external interface CourseBlockListItemProps : Props {

    var courseBlock: CourseBlockWithCompleteEntity

    var uiState: ClazzDetailOverviewUiState

    var onClickItem: (CourseBlockWithCompleteEntity) -> Unit

}

private val CourseBlockListItem = FC<CourseBlockListItemProps> { props ->

    when(props.courseBlock.cbType){
        CourseBlock.BLOCK_MODULE_TYPE, CourseBlock.BLOCK_DISCUSSION_TYPE  -> {

            val trailingIcon = if(props.courseBlock.expanded)
                KeyboardArrowUp.create()
            else
                KeyboardArrowDown.create()

            ListItem {
                ListItemIcon {
                    + Book.create()
                }
                ListItemText{
                    primary = ReactNode(props.courseBlock.cbTitle ?: "")
                    secondary = ReactNode(props.courseBlock.cbDescription ?: "")
                }
                secondaryAction = Icon.create {
                    + trailingIcon
                }
            }
        }
        CourseBlock.BLOCK_TEXT_TYPE -> {

//            val cbDescription = if(props.uiState.cbDescriptionVisible(props.courseBlock))
//                Html.fromHtml(courseBlock.cbDescription)
//            else
//                SpannedString.valueOf("")

            ListItem {
                ListItemIcon {
                    + Book.create()
                }
                ListItemText{
                    primary = ReactNode(props.courseBlock.cbTitle ?: "")
                    secondary = ReactNode(props.courseBlock.cbDescription ?: "")
                }
            }
        }
        CourseBlock.BLOCK_ASSIGNMENT_TYPE -> {

        }
        CourseBlock.BLOCK_CONTENT_TYPE -> {
            if(props.courseBlock.entry != null) {

            }else{

            }
        }
    }
}

val ClazzDetailOverviewScreenPreview = FC<Props> {
    ClazzDetailOverviewComponent2 {
        uiState = ClazzDetailOverviewUiState(
            clazz = ClazzWithDisplayDetails().apply {
                clazzDesc = "Description"
                clazzCode = "abc123"
                clazzSchoolUid = 1
                clazzStartTime = ((14 * MS_PER_HOUR) + (30 * MS_PER_MIN)).toLong()
                clazzEndTime = 0
                clazzSchool = School().apply {
                    schoolName = "School Name"
                }
                clazzHolidayCalendar = HolidayCalendar().apply {
                    umCalendarName = "Holiday Calendar"
                }
            },
            scheduleList = listOf(
                Schedule().apply {
                    sceduleStartTime = 0
                    scheduleEndTime = 0
                },
                Schedule().apply {
                    sceduleStartTime = 0
                    scheduleEndTime = 0
                }
            ),
            courseBlockList = listOf(
                CourseBlockWithCompleteEntity().apply {
                    cbUid = 3
                    cbTitle = "Module 1"
                    cbDescription = "Description 1"
                    cbType = CourseBlock.BLOCK_MODULE_TYPE
                },
                CourseBlockWithCompleteEntity().apply {
                    cbUid = 4
                    cbTitle = "Module 2"
                    cbType = CourseBlock.BLOCK_MODULE_TYPE
                }
            ),
            clazzCodeVisible = true
        )
    }
}
