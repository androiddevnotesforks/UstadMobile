package com.ustadmobile.redux

import com.ustadmobile.core.db.UmAppDatabase
import org.kodein.di.DI
import redux.RAction

data class ReduxDiState(var instance: DI = DI.lazy {  }): RAction