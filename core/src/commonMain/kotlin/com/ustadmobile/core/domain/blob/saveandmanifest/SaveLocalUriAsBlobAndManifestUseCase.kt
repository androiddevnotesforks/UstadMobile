package com.ustadmobile.core.domain.blob.saveandmanifest

import com.ustadmobile.core.contentformats.manifest.ContentManifestEntry
import com.ustadmobile.core.domain.blob.savelocaluris.SaveLocalUrisAsBlobsUseCase

/**
 * UseCase that handles a common requirement when importing content: assets stored locally or in
 * temporary paths need to be saved as a blob and be converted into a manifest entry linked to the
 * given blob
 */
interface SaveLocalUriAsBlobAndManifestUseCase {

    /**
     * @param manifestMimeType The mime-type that will be used for this item in the ContentManifestEntry
     *
     */
    data class SaveLocalUriAsBlobAndManifestItem(
        val blobItem: SaveLocalUrisAsBlobsUseCase.SaveLocalUriAsBlobItem,
        val manifestUri: String,
        val manifestMimeType: String? = null,
    )

    data class BlobAndContentManifestEntry(
        val savedBlob: SaveLocalUrisAsBlobsUseCase.SavedBlob,
        val manifestEntry: ContentManifestEntry,
    )

    suspend operator fun invoke(
        items: List<SaveLocalUriAsBlobAndManifestItem>
    ): List<BlobAndContentManifestEntry>

}