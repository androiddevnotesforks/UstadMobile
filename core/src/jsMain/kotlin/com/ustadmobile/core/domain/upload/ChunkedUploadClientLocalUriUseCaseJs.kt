package com.ustadmobile.core.domain.upload

import com.ustadmobile.core.domain.blob.TransferJobItemStatus
import com.ustadmobile.core.util.stringvalues.asIStringValues
import com.ustadmobile.door.DoorUri
import io.github.aakira.napier.Napier
import js.core.jso
import js.promise.await
import web.http.fetch
import web.http.fetchAsync
import kotlin.js.json


class ChunkedUploadClientLocalUriUseCaseJs: ChunkedUploadClientLocalUriUseCase {

    override suspend fun invoke(
        uploadUuid: String,
        localUri: DoorUri,
        remoteUrl: String,
        fromByte: Long,
        chunkSize: Int,
        onProgress: (Long) -> Unit,
        onStatusChange: (TransferJobItemStatus) -> Unit,
    ): ChunkedUploadClientLocalUriUseCase.LastChunkResponse {
        try {
            Napier.d("ChunkedUploadClientLocalUriUseCaseJs: Starting upload " +
                    "uuid=$uploadUuid localUri=$localUri to $remoteUrl")
            val blob = fetch(localUri.uri.toString()).blob().await()
            val totalSize = blob.size
            if(totalSize <= 0)
                throw IllegalArgumentException("Upload size <= 0")

            val chunkInfo = ChunkInfo(
                totalSize = totalSize.toLong(),
                chunkSize = chunkSize,
                fromByte = fromByte
            )

            if(totalSize >= Int.MAX_VALUE)
                throw IllegalArgumentException("JS: upload size > $totalSize not supported")

            onStatusChange(TransferJobItemStatus.IN_PROGRESS)
            chunkInfo.forEach { chunk ->
                val uploadChunkBlob = blob.slice(chunk.start.toInt(), chunk.end.toInt())
                val fetchResponse = fetchAsync(
                    input = remoteUrl,
                    init = jso {
                        body = uploadChunkBlob
                        method = "POST"
                        headers = json(
                            HEADER_UPLOAD_UUID to uploadUuid,
                            HEADER_IS_FINAL_CHUNK to chunk.isLastChunk.toString(),
                        )
                    }
                ).await()
                onProgress(chunk.end)

                if(chunk.isLastChunk) {
                    onStatusChange(TransferJobItemStatus.COMPLETE)
                    Napier.d("ChunkedUploadClientLocalUriUseCaseJs: Completed upload " +
                            "uuid=$uploadUuid to $remoteUrl")
                    return ChunkedUploadClientLocalUriUseCase.LastChunkResponse(
                        body = if(fetchResponse.status == 200) {
                            fetchResponse.text().await()
                        }else {
                            null
                        },
                        statusCode = fetchResponse.status,
                        headers = fetchResponse.headers.asIStringValues(),
                    )
                }
            }

            throw IllegalStateException("Should have returned after lastChunk")
        }catch(e: Throwable) {
            Napier.e("ChunkedUploadClientLocalUriUseCaseJs: Exception", e)
            onStatusChange(TransferJobItemStatus.FAILED)
            throw e
        }
    }


}