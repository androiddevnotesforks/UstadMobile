package com.ustadmobile.core.domain.blob.upload

import kotlinx.serialization.Serializable


/**
 * A response from the server to the client for an individual blob item to be uploaded.
 *
 * @param blobUrl the URL as per the RequestItem
 * @param uploadUuid an upload UUID set by the server for this particular item. To be used for
 *        ChunkedUpload
 * @param fromByte the byte to start the upload from (inclusive). Non-zero if there is already a
 *        partial upload.
 */
@Serializable
data class BlobUploadResponseItem(
    val blobUrl: String,
    val uploadUuid: String,
    val fromByte: Long,
)
