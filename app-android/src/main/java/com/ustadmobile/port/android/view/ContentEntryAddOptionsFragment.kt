package com.ustadmobile.port.android.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavArgument
import androidx.navigation.fragment.findNavController
import com.toughra.ustadmobile.R
import com.ustadmobile.core.impl.UMAndroidUtil
import com.ustadmobile.core.impl.UstadMobileSystemImpl
import com.ustadmobile.core.view.ContentEntryAddOptionsView
import com.ustadmobile.core.view.ContentEntryAddOptionsView.Companion.CONTENT_CREATE_FOLDER
import com.ustadmobile.core.view.ContentEntryAddOptionsView.Companion.CONTENT_IMPORT_FILE
import com.ustadmobile.core.view.ContentEntryAddOptionsView.Companion.CONTENT_IMPORT_LINK
import com.ustadmobile.core.view.ContentEntryEdit2View
import com.ustadmobile.core.view.ContentEntryEdit2View.Companion.CONTENT_ENTRY_LEAF
import com.ustadmobile.core.view.ContentEntryEdit2View.Companion.CONTENT_ENTRY_PARENT_UID
import com.ustadmobile.core.view.ContentEntryEdit2View.Companion.CONTENT_TYPE
import com.ustadmobile.core.view.ContentEntryImportLinkView
import com.ustadmobile.core.view.UstadView
import com.ustadmobile.core.view.UstadView.Companion.ARG_CONTENT_ENTRY_UID
import com.ustadmobile.core.view.UstadView.Companion.ARG_ENTITY_UID
import com.ustadmobile.core.view.UstadView.Companion.MASTER_SERVER_ROOT_ENTRY_UID
import kotlinx.android.synthetic.main.fragment_content_entry_add_options.view.*

/**
 * Fragment class responsible for content creation selection, you can create content by one of the following
 * CONTENT_CREATE_FOLDER = Create new content category
 * CONTENT_IMPORT_FILE = Create content from file (epub, h5p e.t.c)
 * CONTENT_CREATE_CONTENT = create content from out content editor
 * CONTENT_IMPORT_LINK = Create content from a link
 */

class ContentEntryAddOptionsFragment : UstadBottomSheetFragment(), ContentEntryAddOptionsView, View.OnClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val rootView = inflater.inflate(R.layout.fragment_content_entry_add_options, container, false)
        rootView.content_create_folder.setOnClickListener(this)
        rootView.content_import_file.setOnClickListener(this)
        return rootView;
    }

    override fun onClick(view: View?) {
       val contentType =  when(view?.id){
            R.id.content_create_folder -> CONTENT_CREATE_FOLDER
            R.id.content_import_file -> CONTENT_IMPORT_FILE
            R.id.content_import_link -> CONTENT_IMPORT_LINK
           else -> -1
       }
        findNavController().navigate(R.id.content_entry_edit_dest, UMAndroidUtil.mapToBundle(mapOf(
                CONTENT_ENTRY_PARENT_UID to MASTER_SERVER_ROOT_ENTRY_UID.toString(),
                CONTENT_TYPE to contentType.toString(),
                CONTENT_ENTRY_LEAF to (CONTENT_CREATE_FOLDER != contentType).toString())))
        dismiss()
    }

    override val viewContext: Any
        get() = requireContext()

}
