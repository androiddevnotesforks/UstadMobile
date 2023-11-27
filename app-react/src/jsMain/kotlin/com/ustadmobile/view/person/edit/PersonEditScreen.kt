package com.ustadmobile.view.person.edit

import com.ustadmobile.core.MR
import com.ustadmobile.core.hooks.collectAsState
import com.ustadmobile.core.hooks.useStringProvider
import com.ustadmobile.hooks.useUstadViewModel
import com.ustadmobile.core.impl.UstadMobileConstants
import com.ustadmobile.core.impl.locale.StringProvider
import com.ustadmobile.core.impl.locale.entityconstants.PersonConstants.GENDER_MESSAGE_IDS_AND_UNSET
import com.ustadmobile.core.viewmodel.person.edit.PersonEditUiState
import com.ustadmobile.core.viewmodel.person.edit.PersonEditViewModel
import com.ustadmobile.lib.db.entities.PersonParentJoin
import com.ustadmobile.lib.db.entities.PersonWithAccount
import com.ustadmobile.lib.db.entities.ext.shallowCopy
import com.ustadmobile.mui.components.ThemeContext
import com.ustadmobile.mui.components.UstadDateField
import com.ustadmobile.mui.components.UstadStandardContainer
import com.ustadmobile.mui.components.UstadTextEditField
import com.ustadmobile.view.components.UstadImageSelectButton
import com.ustadmobile.wrappers.muitelinput.MuiTelInput
import mui.material.FormControl
import mui.material.FormHelperText
import mui.material.InputLabel
import mui.material.MenuItem
import mui.material.Select
import mui.material.TextField
import mui.system.Stack
import mui.system.responsive
import mui.system.sx
import react.FC
import react.Props
import react.ReactNode
import react.dom.onChange
import react.useRequiredContext
import react.useState
import web.cssom.Color

external interface PersonEditScreenProps : Props{
    var uiState: PersonEditUiState

    var onPersonChanged: (PersonWithAccount?) -> Unit

    var onApprovalPersonParentJoinChanged: (PersonParentJoin?) -> Unit

    var onPersonPictureUriChanged: (String?) -> Unit
}

val PersonEditComponent2 = FC <PersonEditScreenProps> { props ->

    val strings: StringProvider = useStringProvider()

    val theme by useRequiredContext(ThemeContext)

    UstadStandardContainer {
        Stack {
            spacing = responsive(2)

            UstadImageSelectButton {
                imageUri = props.uiState.personPicture?.personPictureUri
                onImageUriChanged = {
                    props.onPersonPictureUriChanged(it)
                }
            }

            TextField {
                value = props.uiState.person?.firstNames ?: ""
                label = ReactNode(strings[MR.strings.first_names] + "*")
                error = props.uiState.firstNameError != null
                disabled = !props.uiState.fieldsEnabled
                helperText = ReactNode(props.uiState.firstNameError ?: strings[MR.strings.required])
                onChange = {
                    props.onPersonChanged(
                        props.uiState.person?.shallowCopy {
                            firstNames = it.target.asDynamic().value as? String
                        })
                }
            }

            TextField {
                value = props.uiState.person?.lastName ?: ""
                label = ReactNode(strings[MR.strings.last_name] + "*")
                error = props.uiState.lastNameError != null
                disabled = !props.uiState.fieldsEnabled
                helperText = ReactNode(props.uiState.lastNameError ?: strings[MR.strings.required])
                disabled = !props.uiState.fieldsEnabled
                onChange = {
                    props.onPersonChanged(
                        props.uiState.person?.shallowCopy {
                            lastName = it.target.asDynamic().value as? String
                        })
                }
            }


            FormControl {
                fullWidth = true
                error = props.uiState.genderError != null

                InputLabel {
                    id = "gender_label"
                    shrink = true
                    sx {
                        backgroundColor = Color(theme.palette.background.default)
                    }
                    + ReactNode(strings[MR.strings.gender_literal] + "*")
                }

                Select {
                    value = props.uiState.person?.gender?.toString()
                    id = "gender"
                    labelId = "gender_label"
                    disabled = !props.uiState.fieldsEnabled
                    fullWidth = true
                    onChange = { event, _ ->
                        val selectedVal = ("" + event.target.value)
                        props.onPersonChanged(
                            props.uiState.person?.shallowCopy {
                                gender = selectedVal.toInt()
                            }
                        )
                    }

                    GENDER_MESSAGE_IDS_AND_UNSET.forEach { option ->
                        MenuItem {
                            value = option.value.toString()
                            + ReactNode(strings[option.stringResource])
                        }
                    }
                }

                FormHelperText {
                    +ReactNode(props.uiState.genderError?: strings[MR.strings.required])
                }
            }


            if (props.uiState.parentalEmailVisible){
                UstadTextEditField {
                    value = props.uiState.approvalPersonParentJoin?.ppjEmail ?: ""
                    label = strings[MR.strings.parents_email_address]
                    error = props.uiState.parentContactError
                    enabled = props.uiState.fieldsEnabled
                    onChange = {
                        props.onApprovalPersonParentJoinChanged(
                            props.uiState.approvalPersonParentJoin?.shallowCopy {
                                ppjEmail = it
                            })
                    }
                }
            }

            UstadDateField {
                timeInMillis = props.uiState.person?.dateOfBirth ?: 0
                label = ReactNode(strings[MR.strings.birthday])
                timeZoneId = UstadMobileConstants.UTC
                error = props.uiState.dateOfBirthError != null
                helperText = ReactNode(props.uiState.dateOfBirthError)
                onChange = {
                    props.onPersonChanged(
                        props.uiState.person?.shallowCopy {
                            dateOfBirth = it
                    })
                }
            }

            UstadTextEditField {
                error = props.uiState.parentContactError
                enabled = props.uiState.fieldsEnabled
                label = strings[MR.strings.parents_email_address]
                onChange = {
                    props.onApprovalPersonParentJoinChanged(
                        props.uiState.approvalPersonParentJoin?.shallowCopy {
                            ppjEmail = it
                        })
                }
            }

            MuiTelInput {
                value = props.uiState.person?.phoneNum ?: ""
                label = ReactNode(strings[MR.strings.phone_number])
                disabled = !props.uiState.fieldsEnabled
                onChange = { number, _ ->
                    props.onPersonChanged(
                        props.uiState.person?.shallowCopy {
                            phoneNum = number
                        }
                    )
                }
                error = props.uiState.phoneNumError != null
                helperText = props.uiState.phoneNumError.let { ReactNode(it) }
            }

            TextField {
                value = props.uiState.person?.emailAddr ?: ""
                label = ReactNode(strings[MR.strings.email])
                error = props.uiState.emailError != null
                disabled = !props.uiState.fieldsEnabled
                helperText = ReactNode(props.uiState.emailError)
                onChange = {
                    props.onPersonChanged(
                        props.uiState.person?.shallowCopy {
                            emailAddr = it.target.asDynamic().value as? String
                    })
                }
            }

            UstadTextEditField {
                value = props.uiState.person?.personAddress ?: ""
                label = strings[MR.strings.address]
                enabled = props.uiState.fieldsEnabled
                onChange = {
                    props.onPersonChanged(
                        props.uiState.person?.shallowCopy {
                            personAddress = it
                    })
                }
            }

            if (props.uiState.usernameVisible){
                TextField {
                    value = props.uiState.person?.username ?: ""
                    label = ReactNode(strings[MR.strings.username])
                    disabled = !props.uiState.fieldsEnabled
                    error = props.uiState.usernameError != null
                    helperText = ReactNode(props.uiState.usernameError ?: strings[MR.strings.required])
                    onChange = {
                        props.onPersonChanged(
                            props.uiState.person?.shallowCopy {
                                username = it.target.asDynamic().value
                        })
                    }
                }
            }

            if (props.uiState.passwordVisible){
                UstadTextEditField {
                    value = props.uiState.person?.newPassword ?: ""
                    label = strings[MR.strings.password]
                    enabled = props.uiState.fieldsEnabled
                    onChange = {
                        props.onPersonChanged(
                            props.uiState.person?.shallowCopy {
                                newPassword = it
                        })
                    }
                }
            }
        }
    }
}

val PersonEditScreenPreview = FC<Props> {

    var uiStateVar by useState {
        PersonEditUiState(
            person = PersonWithAccount().apply {
                firstNames = "Bob"
                lastName = "Jones"
                phoneNum = "0799999"
                emailAddr = "Bob@gmail.com"
                gender = 1
                username = "Bob12"
                dateOfBirth = 0
                personOrgId = "123"
                personAddress = "Herat"
            }
        )
    }

    PersonEditComponent2 {
        uiState = uiStateVar
        onPersonChanged = {
            uiStateVar = uiStateVar.copy(person = it)
        }
    }
}

val PersonEditScreen = FC<Props> {
    val viewModel = useUstadViewModel { di, savedStateHandle ->
        PersonEditViewModel(di, savedStateHandle)
    }

    val uiStateVar by viewModel.uiState.collectAsState(PersonEditUiState())

    PersonEditComponent2 {
        uiState = uiStateVar
        onPersonChanged = viewModel::onEntityChanged
        onApprovalPersonParentJoinChanged = viewModel::onApprovalPersonParentJoinChanged
        onPersonPictureUriChanged = viewModel::onPersonPictureChanged
    }
}
