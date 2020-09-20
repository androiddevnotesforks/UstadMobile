package com.ustadmobile.port.sharedse.contentformats.xapi.plugin

import com.ustadmobile.core.catalog.contenttype.XapiPackageTypePlugin
import com.ustadmobile.core.impl.UstadMobileSystemImpl
import com.ustadmobile.core.tincan.TinCanXML
import com.ustadmobile.lib.db.entities.ContentEntry
import com.ustadmobile.lib.db.entities.ContentEntryWithLanguage
import com.ustadmobile.port.sharedse.contentformats.ContentTypeFilePlugin
import com.ustadmobile.port.sharedse.contentformats.ContentTypeUtil
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class XapiPackageTypeFilePlugin : XapiPackageTypePlugin(), ContentTypeFilePlugin {

    override fun getContentEntry(file: File): ContentEntryWithLanguage? {
        var contentEntry: ContentEntryWithLanguage? = null
        try {
            ZipInputStream(FileInputStream(file)).use {
                var zipEntry: ZipEntry? = null
                while ({ zipEntry = it.nextEntry; zipEntry }() != null) {

                    val fileName = zipEntry?.name
                    if (fileName?.toLowerCase() == "tincan.xml") {
                        val xpp = UstadMobileSystemImpl.instance.newPullParser(it)
                        val activity = TinCanXML.loadFromXML(xpp).launchActivity
                        if(activity == null)
                            throw IOException("TinCanXml from ${file.name} has no launchActivity!")

                        contentEntry = ContentEntryWithLanguage().apply {
                            contentFlags = ContentEntry.FLAG_IMPORTED
                            licenseType = ContentEntry.LICENSE_TYPE_OTHER
                            title = if(activity.name.isNullOrEmpty())
                                file.nameWithoutExtension else activity.name
                            contentTypeFlag  = ContentEntry.TYPE_INTERACTIVE_EXERCISE
                            description = activity.desc
                            leaf = true
                            entryId = activity.id
                        }
                        break
                    }

                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        }

        return contentEntry
    }

    override fun importMode(): Int {
        return ContentTypeUtil.ZIPPED
    }
}