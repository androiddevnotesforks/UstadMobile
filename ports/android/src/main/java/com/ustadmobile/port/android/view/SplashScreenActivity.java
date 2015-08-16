/*
    This file is part of Ustad Mobile.

    Ustad Mobile Copyright (C) 2011-2014 UstadMobile Inc.

    Ustad Mobile is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version with the following additional terms:

    All names, links, and logos of Ustad Mobile and Toughra Technologies FZ
    LLC must be kept as they are in the original distribution.  If any new
    screens are added you must include the Ustad Mobile logo as it has been
    used in the original distribution.  You may not create any new
    functionality whose purpose is to diminish or remove the Ustad Mobile
    Logo.  You must leave the Ustad Mobile logo as the logo for the
    application to be used with any launcher (e.g. the mobile app launcher).

    If you want a commercial license to remove the above restriction you must
    contact us.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    Ustad Mobile is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

 */

package com.ustadmobile.port.android.view;

import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.toughra.ustadmobile.R;

import com.ustadmobile.core.controller.CatalogController;
import com.ustadmobile.core.controller.CatalogEntryInfo;
import com.ustadmobile.core.controller.ContainerController;
import com.ustadmobile.core.controller.LoginController;
import com.ustadmobile.core.impl.UMTransferJob;
import com.ustadmobile.core.impl.UstadMobileSystemImpl;
import com.ustadmobile.core.ocf.UstadOCF;
import com.ustadmobile.core.opds.UstadJSOPDSEntry;
import com.ustadmobile.core.opds.UstadJSOPDSFeed;
import com.ustadmobile.core.util.UMFileUtil;
import com.ustadmobile.port.android.impl.UstadMobileSystemImplAndroid;

import java.io.IOException;
import java.util.Hashtable;


public class SplashScreenActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
    }

    @Override
    public void onStart() {
        super.onStart();
        UstadMobileSystemImpl.getInstance();

        UstadMobileSystemImplAndroid impl = UstadMobileSystemImplAndroid.getInstanceAndroid();
        impl.handleActivityStart(this);
        //runTest();

        impl.startUI();

        /*
        Hashtable registerParams = new Hashtable();
        registerParams.put("phonenumber", "+9641234567");
        registerParams.put("gender", "f");
        registerParams.put("name", "Unit Testing");

        try {
            String serverSays = LoginController.registerNewUser(registerParams,
                    "http://umcloud1.ustadmobile.com/phoneinappreg/");
            String somethingElse = serverSays  + "!";
        } catch (IOException e) {
            e.printStackTrace();
        }
        */  
    }

    public void onStop() {
        super.onStop();
        UstadMobileSystemImplAndroid.getInstanceAndroid().handleActivityStop(this);
    }

    public void onDestroy() {
        super.onDestroy();
        UstadMobileSystemImplAndroid.getInstanceAndroid().handleActivityDestroy(this);
    }


    public void runTest(){
        new Thread(new Runnable() {
            public void run() {
                try {
                    String httpRoot = "http://192.168.0.103:5062/";

                    String acquireOPDSURL = UMFileUtil.joinPaths(new String[]{
                            httpRoot, "acquire.opds"});
                    UstadMobileSystemImpl impl = UstadMobileSystemImpl.getInstance();

                    UstadJSOPDSFeed feed = CatalogController.getCatalogByURL(acquireOPDSURL,
                            CatalogController.SHARED_RESOURCE, null, null,
                            CatalogController.CACHE_ENABLED);

                    UMTransferJob acquireJob = CatalogController.acquireCatalogEntries(feed.entries,
                            null, null, CatalogController.SHARED_RESOURCE, CatalogController.CACHE_ENABLED);
                    int totalSize = acquireJob.getTotalSize();

                    acquireJob.start();
                    int timeRemaining = 60000;
                    while(timeRemaining > 0 && !acquireJob.isFinished()) {
                        try {Thread.sleep(1000); }
                        catch(InterruptedException e) {}
                    }
                    //assertTrue("Job has completed", acquireJob.isFinished());

                    CatalogEntryInfo entryInfo = CatalogController.getEntryInfo(feed.entries[0].id,
                            CatalogController.SHARED_RESOURCE);

                    String acquiredFileURI = entryInfo.fileURI;

                    UstadJSOPDSEntry entry = feed.entries[0];

                    String openPath = impl.openContainer(entry, acquiredFileURI,
                            entryInfo.mimeType);
                    //assertNotNull("Got an open path from the system", openPath);

                    ContainerController controller = ContainerController.makeFromEntry(entry,
                            openPath, entryInfo.fileURI, entryInfo.mimeType);
                    UstadOCF ocf = controller.getOCF();
                    //assertNotNull("Controller can fetch OCF once open", ocf);
                    String helloName = "bob";
                    helloName += "joe";
                }catch(Exception e) {
                    e.printStackTrace();
                }

            }
        }).start();



    }

    @Override
    public boolean onCreateOptionsMenu(
            Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_splash_screen, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
