package com.ustadmobile.core.controller

import com.ustadmobile.core.account.UstadAccountManager
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.core.db.dao.ContainerDao
import com.ustadmobile.core.db.dao.ContainerEntryDao
import com.ustadmobile.core.db.dao.ContentEntryDao
import com.ustadmobile.core.impl.UstadMobileSystemCommon.Companion.ARG_REFERRER
import com.ustadmobile.core.view.*
import com.ustadmobile.door.doorMainDispatcher
import com.ustadmobile.lib.db.entities.ContainerEntryWithContainerEntryFile
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.di.on

abstract class VideoContentPresenterCommon(context: Any, arguments: Map<String, String>, view: VideoPlayerView,
                                           di: DI)
    : UstadBaseController<VideoPlayerView>(context, arguments, view, di) {


    internal var containerUid: Long = 0

    val accountManager: UstadAccountManager by instance()

    val db: UmAppDatabase by on(accountManager.activeAccount).instance(tag = UmAppDatabase.TAG_DB)

    val repo: UmAppDatabase by on(accountManager.activeAccount).instance(tag = UmAppDatabase.TAG_REPO)


    internal lateinit var contentEntryDao: ContentEntryDao
    internal lateinit var containerDao: ContainerDao
    internal lateinit var containerEntryDao: ContainerEntryDao

    data class VideoParams(val videoPath: String? = null,
                           val audioPath: ContainerEntryWithContainerEntryFile? = null,
                           val srtLangList: MutableList<String> = mutableListOf(),
                           val srtMap: MutableMap<String, String> = mutableMapOf())

    var audioEntry: ContainerEntryWithContainerEntryFile? = null

    internal var videoPath: String? = null
    internal var srtMap = mutableMapOf<String, String>()
    internal var srtLangList = mutableListOf<String>()

    abstract fun handleOnResume()

    override fun onCreate(savedState: Map<String, String>?) {
        super.onCreate(savedState)
        containerEntryDao = db.containerEntryDao
        containerDao = db.containerDao
        contentEntryDao = db.contentEntryDao

        val entryUuid = arguments.getValue(UstadView.ARG_CONTENT_ENTRY_UID).toLong()
        containerUid = arguments.getValue(UstadView.ARG_CONTAINER_UID).toLong()

        view.loading = true
        GlobalScope.launch(doorMainDispatcher()) {
            view.entry = contentEntryDao.getContentByUuidAsync(entryUuid)
        }

    }

    override fun onResume() {
        super.onResume()
        handleOnResume()
    }

    fun handleUpNavigation() {
        //This is now handled by jetpack navigation
    }


    companion object {

        val VIDEO_EXT_LIST = listOf(".mp4", ".mkv", ".webm", ".m4v")

        var VIDEO_MIME_MAP = mapOf("video/mp4" to ".mp4", "video/x-matroska" to ".mkv", "video/webm" to ".webm", "video/x-m4v" to ".m4v")
    }
}
