package com.ustadmobile.core.controller

import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.core.view.WebChunkView
import org.kodein.di.DI

expect class WebChunkPresenter(context: Any, arguments: Map<String, String>, view: WebChunkView,
                               di: DI, isDownloadEnabled: Boolean, appRepo: UmAppDatabase, umAppDb: UmAppDatabase): WebChunkPresenterCommon {

    override suspend fun handleMountChunk()

}