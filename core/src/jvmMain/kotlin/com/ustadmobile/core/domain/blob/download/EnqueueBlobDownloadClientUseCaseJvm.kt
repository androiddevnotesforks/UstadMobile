package com.ustadmobile.core.domain.blob.download

import com.ustadmobile.core.account.Endpoint
import com.ustadmobile.core.connectivitymonitor.ConnectivityTriggerGroupController
import com.ustadmobile.core.db.UmAppDatabase
import io.github.aakira.napier.Napier
import org.quartz.JobBuilder
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey

class EnqueueBlobDownloadClientUseCaseJvm(
    private val scheduler: Scheduler,
    private val endpoint: Endpoint,
    db: UmAppDatabase,
): AbstractEnqueueBlobDownloadClientUseCase(db) {
    override suspend fun invoke(
        items: List<EnqueueBlobDownloadClientUseCase.EnqueueBlobDownloadItem>
    ) {
        val transferJob = createTransferJob(items)
        val quartzJob = JobBuilder.newJob(BlobDownloadJob::class.java)
            .usingJobData(DATA_ENDPOINT, endpoint.url)
            .usingJobData(DATA_JOB_UID, transferJob.tjUid)
            .build()
        val triggerKey = TriggerKey("blob-download-${endpoint.url}-${transferJob.tjUid}",
            ConnectivityTriggerGroupController.TRIGGERKEY_CONNECTIVITY_REQUIRED_GROUP)

        scheduler.unscheduleJob(triggerKey)
        val jobTrigger = TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .startNow()
            .build()

        Napier.d { "EnqueueBlobDownloadClientUseCaseJvm: scheduled job via quartz. JobId=${transferJob.tjUid} " }
        scheduler.scheduleJob(quartzJob, jobTrigger)
    }
}