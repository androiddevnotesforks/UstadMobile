package com.ustadmobile.test.port.android;

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.ustadmobile.core.impl.UstadMobileSystemImpl;
import com.ustadmobile.port.android.netwokmanager.NetworkServiceAndroid;
import com.ustadmobile.test.sharedse.TestWifiDirectGroupConnection;

import org.junit.BeforeClass;

/**
 * Created by mike on 6/2/17.
 */

public class TestWifiDirectGroupConnectionAndroidTmp extends TestWifiDirectGroupConnection {

    public static final ServiceTestRule mServiceRule = new ServiceTestRule();

    @BeforeClass
    public static void startNetworkService() throws Exception{
        Context context = InstrumentationRegistry.getTargetContext();
        UstadMobileSystemImpl.getInstance().init(context);
        Intent serviceIntent = new Intent(context, NetworkServiceAndroid.class);
        mServiceRule.bindService(serviceIntent);
    }

}
