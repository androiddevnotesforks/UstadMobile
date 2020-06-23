package com.ustadmobile.port.sharedse.contentformats

import com.ustadmobile.core.catalog.contenttype.ContentTypePlugin.Companion.CONTENT_ENTRY
import com.ustadmobile.core.catalog.contenttype.ContentTypePlugin.Companion.CONTENT_MIMETYPE
import com.ustadmobile.core.container.ContainerManager
import com.ustadmobile.core.container.addEntriesFromZipToContainer
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.core.impl.UmAccountManager
import com.ustadmobile.core.impl.UmCallback
import com.ustadmobile.lib.db.entities.Container
import com.ustadmobile.lib.db.entities.ContentEntry
import com.ustadmobile.lib.db.entities.ContentEntryWithLanguage
import com.ustadmobile.port.sharedse.contentformats.epub.EpubTypePlugin
import com.ustadmobile.port.sharedse.contentformats.xapi.plugin.TinCanTypePlugin
import java.io.File
import java.io.IOException
import java.util.*
import java.util.zip.ZipException

import java.nio.file.Paths
import java.nio.file.Files

import java.util.regex.Pattern

/**
 * Class which handles entries and containers for all imported content
 *
 * @author kileha3
 */


private val CONTENT_PLUGINS = listOf(EpubTypePlugin(), TinCanTypePlugin(), VideoTypePlugin())

suspend fun extractContentEntryMetadataFromFile(file: File, db: UmAppDatabase, plugins: List<ContentTypePlugin> = CONTENT_PLUGINS): ImportedContentEntryMetaData? {
    plugins.forEach {
        val pluginResult = it.getContentEntry(file)
        val languageCode = pluginResult?.language?.iso_639_1_standard
        if (languageCode != null) {
            pluginResult?.language = db.languageDao.findByTwoCode(languageCode)
        }
        if (pluginResult != null) {
            pluginResult.contentFlags = ContentEntry.FLAG_IMPORTED
            return ImportedContentEntryMetaData(pluginResult, it.mimeTypes[0], file)
        }
    }

    return null
}

suspend fun importContainerFromZippedFile(contentEntryUid: Long, mimeType: String?, containerBaseDir: String,
                                          file: File, db: UmAppDatabase, dbRepo: UmAppDatabase): Container {

    val container = Container().apply {
        containerContentEntryUid = contentEntryUid
    }

    container.cntLastModified = System.currentTimeMillis()
    container.fileSize = file.length()
    container.mimeType = mimeType
    container.containerUid = dbRepo.containerDao.insert(container)

    val containerManager = ContainerManager(container, db, dbRepo, containerBaseDir)
    try {
        if (file.isDirectory()) {
            val fileMap = HashMap<File, String>()
            createContainerFromDirectory(file, fileMap)
            fileMap.forEach {
                containerManager.addEntries(ContainerManager.FileEntrySource(it.component1(), it.component2()))
            }
        } else {
            addEntriesFromZipToContainer(file.absolutePath, containerManager)
        }
    } catch (e: ZipException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return container
}

fun createContainerFromDirectory(directory: File, filemap: MutableMap<File, String>): Map<File, String> {
    val sourceDirPath = Paths.get(directory.toURI())
    try {
        Files.walk(sourceDirPath).filter { path -> !Files.isDirectory(path) }
                .forEach { path ->
                    val relativePath = sourceDirPath.relativize(path).toString()
                            .replace(Pattern.quote("\\").toRegex(), "/")
                    filemap[path.toFile()] = relativePath

                }
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return filemap
}


/**
 * file: add a directory of files or a zipped file
 */
suspend fun importContentEntryFromFile(file: File, db: UmAppDatabase, dbRepo: UmAppDatabase,
                                       containerBaseDir: String, plugins: List<ContentTypePlugin> = CONTENT_PLUGINS): Pair<ContentEntry, Container>? {
    val (contentEntry, mimeType) = extractContentEntryMetadataFromFile(file, db, plugins)
            ?: return null

    contentEntry.contentEntryUid = dbRepo.contentEntryDao.insert(contentEntry)
    val container = importContainerFromZippedFile(contentEntry.contentEntryUid, mimeType, containerBaseDir, file,
            db, dbRepo)

    return Pair(contentEntry, container)
}

/**
 *
 */
data class ImportedContentEntryMetaData(var contentEntry: ContentEntryWithLanguage, var mimeType: String, var file: File)
