package com.ustadmobile.core.controller

import com.ustadmobile.core.contentformats.scorm.ScormManifest
import com.ustadmobile.core.impl.UmCallback
import com.ustadmobile.core.impl.UstadMobileSystemImpl
import com.ustadmobile.core.impl.http.UmHttpCall
import com.ustadmobile.core.impl.http.UmHttpRequest
import com.ustadmobile.core.impl.http.UmHttpResponse
import com.ustadmobile.core.impl.http.UmHttpResponseCallback
import com.ustadmobile.core.util.UMFileUtil
import com.ustadmobile.core.view.ScormPackageView
import com.ustadmobile.core.view.UstadView
import com.ustadmobile.core.view.UstadViewWithNotifications

import org.xmlpull.v1.XmlPullParserException

import java.io.IOException
import java.util.HashMap

/**
 *
 * Present a SCORM package. Scorm tech specs as per :
 * https://scorm.com/scorm-explained/technical-scorm/
 *
 * Created by mike on 1/6/18.
 */
class ScormPackagePresenter(context: Any, private val scormPackageView: ScormPackageView) : UstadBaseController<ScormPackageView>(context) {

    private var scormManifest: ScormManifest? = null

    private var mountedPath: String? = null

    private val zipMountedCallback = object : UmCallback<String> {
        override fun onSuccess(result: String) {
            mountedPath = result
            UstadMobileSystemImpl.getInstance().makeRequestAsync(UmHttpRequest(
                    getContext(),
                    UMFileUtil.joinPaths(mountedPath, "imsmanifest.xml")),
                    manifestLoadedCallback)
        }

        override fun onFailure(exception: Throwable) {
            scormPackageView.showNotification("ERROR: failed to open package file",
                    UstadViewWithNotifications.LENGTH_LONG)
        }
    }

    private val manifestLoadedCallback = object : UmHttpResponseCallback {
        override fun onComplete(call: UmHttpCall, response: UmHttpResponse) {
            scormManifest = ScormManifest()
            try {
                scormManifest!!.loadFromInputStream(response.responseAsStream)
                val defaultOrg = scormManifest!!.defaultOrganization
                val startRes = scormManifest!!.getResourceByIdentifier(
                        defaultOrg.items[0].identifierRef!!)
                scormPackageView.runOnUiThread {
                    scormPackageView.setTitle(scormManifest!!.defaultOrganization.title)
                    scormPackageView.loadUrl(UMFileUtil.joinPaths(mountedPath,
                            startRes.href))
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (x: XmlPullParserException) {
                x.printStackTrace()
            }

        }

        override fun onFailure(call: UmHttpCall, exception: IOException) {

        }
    }

    fun onCreate(args: HashMap<String, String>) {
        scormPackageView.mountZip(args[UstadView.ARG_CONTAINER_UID],
                zipMountedCallback)
    }

}
