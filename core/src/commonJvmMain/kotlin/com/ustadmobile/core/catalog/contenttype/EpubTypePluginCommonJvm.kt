package com.ustadmobile.core.catalog.contenttype

import com.ustadmobile.core.contentformats.epub.opf.OpfDocument
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.core.util.ext.alternative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException
import com.ustadmobile.core.io.ext.addEntriesToContainerFromZip
import java.io.File
import java.io.IOException
import java.util.*
import java.util.zip.ZipInputStream
import com.ustadmobile.core.container.ContainerAddOptions
import com.ustadmobile.core.contentformats.epub.ocf.OcfDocument
import com.ustadmobile.core.contentjob.ProcessResult
import com.ustadmobile.core.impl.getOs
import com.ustadmobile.core.impl.getOsVersion
import com.ustadmobile.core.io.ext.skipToEntry
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.toDoorUri
import com.ustadmobile.door.ext.openInputStream
import com.ustadmobile.lib.db.entities.*
import com.ustadmobile.lib.util.getSystemTimeInMillis
import org.xmlpull.v1.XmlPullParserFactory

class EpubTypePluginCommonJvm(val context: Any) : EpubTypePlugin() {

    /*  suspend fun extractMetadata(uri: String, context: Any): ContentEntry? {
          return withContext(Dispatchers.Default) {
              val xppFactory = XmlPullParserFactory.newInstance()
              try {

                  val doorUri = DoorUri.parse(uri)
                  val inputStream = doorUri.openInputStream(context)

                  val opfPath: String = ZipInputStream(inputStream).use {
                      val metaDataEntry = it.skipToEntry { it.name == "META-INF/container.xml" }
                      if(metaDataEntry != null) {
                          val ocfContainer = OcfDocument()
                          val xpp = xppFactory.newPullParser()
                          xpp.setInput(it, "UTF-8")
                          ocfContainer.loadFromParser(xpp)

                          ocfContainer.rootFiles.firstOrNull()?.fullPath
                      }else {
                          null
                      }
                  } ?: return@withContext null

                  return@withContext ZipInputStream(doorUri.openInputStream(context)).use {
                      val entry = it.skipToEntry { it.name == opfPath } ?: return@use null

                      val xpp = xppFactory.newPullParser()
                      xpp.setInput(it, "UTF-8")
                      val opfDocument = OpfDocument()
                      opfDocument.loadFromOPF(xpp)

                      ContentEntryWithLanguage().apply {
                          contentFlags = ContentEntry.FLAG_IMPORTED
                          contentTypeFlag = ContentEntry.TYPE_EBOOK
                          licenseType = ContentEntry.LICENSE_TYPE_OTHER
                          title = if(opfDocument.title.isNullOrEmpty()) doorUri.getFileName(context)
                                  else opfDocument.title
                          author = opfDocument.getCreator(0)?.creator
                          description = opfDocument.description
                          leaf = true
                          entryId = opfDocument.id.alternative(UUID.randomUUID().toString())
                          val languageCode = opfDocument.getLanguage(0)
                          if (languageCode != null) {
                              this.language = Language().apply {
                                  iso_639_1_standard = languageCode
                              }
                          }
                      }
                  }
              } catch (e: IOException) {
                  e.printStackTrace()
              } catch (e: XmlPullParserException) {
                  e.printStackTrace()
              }

              null
          }
      }*/

    /* suspend fun importToContainer(uri: String, conversionParams: Map<String, String>,
                                            contentEntryUid: Long, mimeType: String,
                                            containerBaseDir: String, context: Any,
                                            db: UmAppDatabase, repo: UmAppDatabase,
                                            progressListener: (Int) -> Unit): Container {

         return withContext(Dispatchers.Default) {

             val doorUri = DoorUri.parse(uri)
             val container = Container().apply {
                 containerContentEntryUid = contentEntryUid
                 cntLastModified = System.currentTimeMillis()
                 this.mimeType = mimeType
                 containerUid = repo.containerDao.insert(this)
             }

             repo.addEntriesToContainerFromZip(container.containerUid,
                     doorUri,
                     ContainerAddOptions(storageDirUri = File(containerBaseDir).toDoorUri()), context)

             val containerWithSize = repo.containerDao.findByUid(container.containerUid) ?: container

             containerWithSize
         }
     }*/

    override val jobType: Int
        get() = TODO("Not yet implemented")

    override suspend fun canProcess(doorUri: DoorUri): Boolean {
        return getOpfPath(doorUri) != null
    }

    override suspend fun extractMetadata(uri: DoorUri): ContentEntryWithLanguage? {
        val opfPath = getOpfPath(uri)
        return withContext(Dispatchers.Default) {
            val xppFactory = XmlPullParserFactory.newInstance()
            try {
                ZipInputStream(uri.openInputStream(context)).use {
                    it.skipToEntry { it.name == opfPath } ?: return@use null

                    val xpp = xppFactory.newPullParser()
                    xpp.setInput(it, "UTF-8")
                    val opfDocument = OpfDocument()
                    opfDocument.loadFromOPF(xpp)

                    ContentEntryWithLanguage().apply {
                        contentFlags = ContentEntry.FLAG_IMPORTED
                        contentTypeFlag = ContentEntry.TYPE_EBOOK
                        licenseType = ContentEntry.LICENSE_TYPE_OTHER
                        title = if (opfDocument.title.isNullOrEmpty()) uri.getFileName(context)
                        else opfDocument.title
                        author = opfDocument.getCreator(0)?.creator
                        description = opfDocument.description
                        leaf = true
                        entryId = opfDocument.id.alternative(UUID.randomUUID().toString())
                        val languageCode = opfDocument.getLanguage(0)
                        if (languageCode != null) {
                            this.language = Language().apply {
                                iso_639_1_standard = languageCode
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                repo.errorReportDao.logErrorReport(ErrorReport.SEVERITY_ERROR, e)
            }
        }
    }

    override suspend fun processJob(jobItem: ContentJobItem): ProcessResult {
        val container = withContext(Dispatchers.Default) {
            val uri = jobItem.djiFromUri ?: return@withContext
            val doorUri = DoorUri.parse(uri)
            val container = Container().apply {
                containerContentEntryUid = jobItem.cjiContentEntryUid
                cntLastModified = System.currentTimeMillis()
                this.mimeType = this@EpubTypePluginCommonJvm.supportedMimeTypes.first()
                containerUid = repo.containerDao.insert(this)
                jobItem.cjiContainerUid = containerUid
            }

            repo.addEntriesToContainerFromZip(container.containerUid,
                    doorUri,
                    ContainerAddOptions(storageDirUri = File(containerBaseDir).toDoorUri()), context)

            val containerWithSize = repo.containerDao.findByUid(container.containerUid) ?: container

            containerWithSize
        }
        return ProcessResult(200)
    }


    fun getOpfPath(uri: DoorUri): String? {
        return withContext(Dispatchers.Default) {
            val xppFactory = XmlPullParserFactory.newInstance()
            try {
                val inputStream = uri.openInputStream(context)

                return@withContext ZipInputStream(inputStream).use {
                    val metaDataEntry = it.skipToEntry { it.name == "META-INF/container.xml" }
                    if (metaDataEntry != null) {
                        val ocfContainer = OcfDocument()
                        val xpp = xppFactory.newPullParser()
                        xpp.setInput(it, "UTF-8")
                        ocfContainer.loadFromParser(xpp)

                        ocfContainer.rootFiles.firstOrNull()?.fullPath
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                repo.errorReportDao.logErrorReport(ErrorReport.SEVERITY_ERROR, e)
            }
        }
    }
}