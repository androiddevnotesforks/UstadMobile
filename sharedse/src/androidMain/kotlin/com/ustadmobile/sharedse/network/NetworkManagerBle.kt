package com.ustadmobile.sharedse.network

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper.getMainLooper
import android.os.ParcelUuid
import androidx.core.net.ConnectivityManagerCompat
import com.ustadmobile.core.impl.UMAndroidUtil.normalizeAndroidWifiSsid
import com.ustadmobile.core.impl.UMLog
import com.ustadmobile.lib.db.entities.ConnectivityStatus
import com.ustadmobile.port.sharedse.impl.http.EmbeddedHTTPD
import com.ustadmobile.port.sharedse.util.AsyncServiceManager
import com.ustadmobile.sharedse.network.containerfetcher.ConnectionOpener
import fi.iki.elonen.NanoHTTPD
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import java.net.HttpURLConnection
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * This class provides methods to perform android network related communications.
 * All Bluetooth Low Energy and WiFi direct communications will be handled here.
 * Also, this is maintained as a singleton by all activities binding to NetworkServiceAndroid,
 * which is responsible to call the onCreate method of this class.
 *
 * **Note:** Most of the scan / advertise methods here require
 * [android.Manifest.permission.BLUETOOTH_ADMIN] permission.
 *
 * @see NetworkManagerBle
 *
 *
 * @author kileha3
 */
actual open class NetworkManagerBle
/**
 * Constructor to be used when creating new instance
 *
 * @param context Platform specific application context
 */
actual constructor(context: Any, di: DI, singleThreadDispatcher: CoroutineDispatcher)
    : NetworkManagerBleCommon(context, di, singleThreadDispatcher), EmbeddedHTTPD.ResponseListener, NetworkManagerWithConnectionOpener {

    val httpd: EmbeddedHTTPD by instance()

    private lateinit var wifiManager: WifiManager

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val mContext: Context = context as Context

    private val parcelServiceUuid = ParcelUuid(UUID.fromString(USTADMOBILE_BLE_SERVICE_UUID))

    private var wifiP2pChannel: WifiP2pManager.Channel? = null

    private var wifiP2pManager: WifiP2pManager? = null

    private var connectivityManager: ConnectivityManager? = null

    private val wifiP2PCapable = AtomicBoolean(false)

    private val wifiLockReference = AtomicReference<WifiManager.WifiLock>()

    internal lateinit var managerHelper : NetworkManagerBleHelper

    private val wifiDirectGroupLastRequestedTime = AtomicLong()

    private val numActiveRequests = AtomicInteger()

    val enablePromptsSnackbarManager = EnablePromptsSnackbarManager()

    override var localConnectionOpener: ConnectionOpener? = null
        get() = field
        protected set

    override val localHttpPort: Int
        get() = httpd.listeningPort


    private val delayedExecutor = Executors.newSingleThreadScheduledExecutor()

    /**
     * Handle network state change events for android version < Lollipop
     */
    private val networkStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val info = connectivityManager!!.activeNetworkInfo
            if (info != null && info.isConnected) {
                handleNetworkAvailable(null)
            } else {
                handleDisconnected()
            }
        }
    }


    private class WifiDirectGroupAndroid(group: WifiP2pGroup, endpointPort: Int) : WiFiDirectGroupBle(group.networkName, group.passphrase) {
        init {
            port = endpointPort
            ipAddress = "192.168.49.1"
        }
    }

    private class WifiP2PGroupServiceManager(private val networkManager: NetworkManagerBle) : AsyncServiceManager(STATE_STOPPED, { runnable, delay -> networkManager.delayedExecutor.schedule(runnable, delay, TimeUnit.MILLISECONDS) }) {

        private val wiFiDirectGroup = AtomicReference<WiFiDirectGroupBle>()

        //Disable this temporarily because creating a handler interferes with Espresso testing
        //private val timeoutCheckHandler = Handler()

        //it's working on it, and hasn't failed yet, don't notify status change
        val wifiP2pBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                networkManager.wifiP2pManager!!.requestGroupInfo(
                        networkManager.wifiP2pChannel) { group ->
                    UMLog.l(UMLog.DEBUG, 0, "NetworkManagerBle: WiFi direct group " +
                            "broadcast received: group = $group")
                    wiFiDirectGroup.set(if (group != null)
                        WifiDirectGroupAndroid(group,
                                networkManager.httpd.listeningPort)
                    else
                        null)
                    if (group == null && state == STATE_STARTING || group != null && state == STATE_STOPPING) {
                        return@requestGroupInfo
                    }

                    if(group != null)
                        networkManager.lockWifi(this@WifiP2PGroupServiceManager)

                    notifyStateChanged(if (group != null) STATE_STARTED else STATE_STOPPED)
                }
            }
        }

        override fun start() {
            //timeoutCheckHandler.postDelayed(CheckTimeoutRunnable(), TIMEOUT_CHECK_INTERVAL.toLong())
            networkManager.wifiP2pManager!!.requestGroupInfo(networkManager.wifiP2pChannel
            ) { wifiP2pGroup ->
                if (wifiP2pGroup != null) {
                    val existingGroup = WifiDirectGroupAndroid(wifiP2pGroup,
                            networkManager.httpd.listeningPort)
                    UMLog.l(UMLog.VERBOSE, 0, "NetworkManagerBle: group already exists: $existingGroup")
                    wiFiDirectGroup.set(existingGroup)
                    notifyStateChanged(STATE_STARTED)
                } else {
                    UMLog.l(UMLog.VERBOSE, 0, "NetworkManagerBle: Creating new WiFi direct group")
                    createNewGroup()
                }
            }
        }

        private fun createNewGroup() {
            networkManager.wifiP2pManager!!.createGroup(networkManager.wifiP2pChannel,
                    object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            UMLog.l(UMLog.INFO, 692, "NetworkManagerBle: Group created successfully")
                            /* wait for the broadcast. OnSuccess might be called before the group is really ready */
                        }

                        override fun onFailure(reason: Int) {
                            UMLog.l(UMLog.ERROR, 692,
                                    "NetworkManagerBle: Failed to create a group with error code: $reason")
                            notifyStateChanged(STATE_STOPPED, STATE_STOPPED)
                        }
                    })
        }

        override fun stop() {
            UMLog.l(UMLog.VERBOSE, 0, "NetworkManagerBle: stopping group")
            networkManager.wifiP2pManager!!.removeGroup(
                    networkManager.wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    UMLog.l(UMLog.INFO, 693,
                            "NetworkManagerBle: Group removed successfully")
                    wiFiDirectGroup.set(null)
                    networkManager.releaseWifiLock(this@WifiP2PGroupServiceManager)
                    notifyStateChanged(STATE_STOPPED)
                }

                override fun onFailure(reason: Int) {
                    UMLog.l(UMLog.ERROR, 693,
                            "NetworkManagerBle: Failed to remove a group with error code $reason")

                    //check if the group is still active
                    networkManager.wifiP2pManager!!.requestGroupInfo(
                            networkManager.wifiP2pChannel
                    ) { wifiP2pGroup ->
                        if (wifiP2pGroup != null) {
                            wiFiDirectGroup.set(WifiDirectGroupAndroid(wifiP2pGroup,
                                    networkManager.httpd.listeningPort))
                            notifyStateChanged(STATE_STARTED, STATE_STARTED)
                        } else {
                            wiFiDirectGroup.set(null)
                            networkManager.releaseWifiLock(this@WifiP2PGroupServiceManager)
                            notifyStateChanged(STATE_STOPPED)
                        }
                    }
                }
            })
        }

        companion object {

            private const val TIMEOUT_AFTER_GROUP_CREATION = 5 * 60 * 1000

            private const val TIMEOUT_AFTER_LAST_REQUEST = 30 * 1000

            private const val TIMEOUT_CHECK_INTERVAL = 30 * 1000
        }
    }


    /**
     * Callback for the network connectivity changes for android version >= Lollipop
     */
    private inner class UmNetworkCallback : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            handleNetworkAvailable(network)
        }


        override fun onLost(network: Network) {
            super.onLost(network)
            UMLog.l(UMLog.VERBOSE, 42, "NetworkCallback: onAvailable" + prettyPrintNetwork(connectivityManager!!.getNetworkInfo(network)))
            handleDisconnected()
        }

        override fun onUnavailable() {
            UMLog.l(UMLog.VERBOSE, 42, "NetworkCallback: onUnavailable")
            super.onUnavailable()
            handleDisconnected()
        }
    }


    private fun handleDisconnected() {
        localHttpClient = null
        localConnectionOpener = null

        UMLog.l(UMLog.VERBOSE, 42, "NetworkCallback: handleDisconnected")
        _connectivityStatus.postValue(ConnectivityStatus(ConnectivityStatus.STATE_DISCONNECTED,
                false, null))
    }


    private fun handleNetworkAvailable(network: Network?) {

        val isMeteredConnection = ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager!!)
        val state = if (isMeteredConnection)
            ConnectivityStatus.STATE_METERED
        else
            ConnectivityStatus.STATE_UNMETERED

        val networkInfo: NetworkInfo? = connectivityManager!!.getNetworkInfo(network)

        UMLog.l(UMLog.VERBOSE, 42, "NetworkCallback: onAvailable" + prettyPrintNetwork(networkInfo))
        val networkExtraInfo = networkInfo?.extraInfo
        val wifiManagerConnectionInfo = wifiManager?.connectionInfo
        val ssid = when {
            Build.VERSION.SDK_INT < 29 && networkExtraInfo != null -> normalizeAndroidWifiSsid(networkExtraInfo)
            wifiManagerConnectionInfo != null -> normalizeAndroidWifiSsid(wifiManagerConnectionInfo.ssid)
            else -> null
        }

        //val ssid = if (networkInfo != null) normalizeAndroidWifiSsid(networkInfo.extraInfo) else null
        val status = ConnectivityStatus(state, true, ssid)
        addLogs("changed to $state")


        //get network SSID
        if (ssid != null /*&& ssid.startsWith(WIFI_DIRECT_GROUP_SSID_PREFIX)*/) {
            if(ssid.startsWith(WIFI_DIRECT_GROUP_SSID_PREFIX)) {
                status.connectivityState = ConnectivityStatus.STATE_CONNECTED_LOCAL
                //TODO: set repo status
            }


            //only on main thread
            //first - check if the old client exists and close it
            val socketFactory = network!!.socketFactory
            UMLog.l(UMLog.DEBUG, 0, "NetworkManager: create local network http " +
                    "client for $ssid using $socketFactory")

            val localOkHttpClientVal = di.direct.instance<OkHttpClient>().newBuilder()
                    .socketFactory(socketFactory)
                    .build()

            //closing localHttpClient would stop the underlying shared OkHttpClient's executors,
            // pools, etc we don't want to do that
            localHttpClient = HttpClient(OkHttp) {
                engine {
                    preconfigured = localOkHttpClientVal
                }
                install(ContentNegotiation) {
                    gson()
                }
            }

            localConnectionOpener = { network.openConnection(it) as HttpURLConnection }

        }

        _connectivityStatus.postValue(status)
    }


    private fun addLogs(message : String){
        println("NetworkConnectivityStatus: $message")
    }


    private fun prettyPrintNetwork(networkInfo: NetworkInfo?): String {
        var `val` = "Network : "
        if (networkInfo != null) {
            `val` += " type: " + networkInfo.typeName
            `val` += " extraInfo: " + networkInfo.extraInfo
        } else {
            `val` += " (null network info)"
        }

        return `val`
    }


    override fun onCreate() {
        managerHelper = NetworkManagerBleHelper(mContext)
        connectivityManager = managerHelper.connectivityManager
        wifiManager = managerHelper.wifiManager

        if (wifiP2pManager == null) {
            wifiP2pManager = mContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
        }

        wifiP2PCapable.set(wifiP2pManager != null)



        startMonitoringNetworkChanges()

        updateEnableServicesPromptsRequired()

        super.onCreate()
    }


    override fun responseStarted(session: NanoHTTPD.IHTTPSession, response: NanoHTTPD.Response) {
        if (session.remoteIpAddress != null && session.remoteIpAddress.startsWith("192.168.49")) {
            numActiveRequests.incrementAndGet()
        }
    }

    override fun responseFinished(session: NanoHTTPD.IHTTPSession, response: NanoHTTPD.Response?) {
        if (session.remoteIpAddress != null && session.remoteIpAddress.startsWith("192.168.49")) {
            numActiveRequests.decrementAndGet()
            wifiDirectGroupLastRequestedTime.set(System.currentTimeMillis())
        }
    }

    fun updateEnableServicesPromptsRequired() {
        val cBluetoothAdapter = bluetoothAdapter
        enablePromptsSnackbarManager.setPromptRequired(EnablePromptsSnackbarManager.BLUETOOTH,
                cBluetoothAdapter != null && cBluetoothAdapter.state !in BLUETOOTH_ON_OR_TURNING_ON_STATES)
        enablePromptsSnackbarManager.setPromptRequired(EnablePromptsSnackbarManager.WIFI,
                wifiManager.wifiState !in WIFI_ON_OR_TURNING_ON_STATES)
    }

    /**
     * {@inheritDoc}
     */
    actual override val isWiFiEnabled: Boolean
        get() = wifiManager.isWifiEnabled


    /**
     * {@inheritDoc}
     */
    actual override val isBluetoothEnabled: Boolean
        get() = (bluetoothAdapter != null && bluetoothAdapter!!.isEnabled
                && bluetoothAdapter!!.state == BluetoothAdapter.STATE_ON)


    /**
     * {@inheritDoc}
     */
    actual override fun openBluetoothSettings() {
        mContext.startActivity(Intent(
                android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
    }


    /**
     * Start monitoring network changes
     */
    private fun startMonitoringNetworkChanges() {
        val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
        if (connectivityManager != null) {
            connectivityManager!!.requestNetwork(networkRequest, UmNetworkCallback())
        }
    }

    override fun lockWifi(lockHolder: Any) {
        super.lockWifi(lockHolder)

        if (wifiLockReference.get() == null) {
            val newLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "UstadMobile-Wifi-Lock-Tag")
            wifiLockReference.set(newLock)
            newLock.acquire()
            wifiLockHolders.add(lockHolder)
            UMLog.l(UMLog.INFO, 699, "WiFi lock acquired for $lockHolder")
        }
    }

    override fun releaseWifiLock(lockHolder: Any) {
        super.releaseWifiLock(lockHolder)

        val lock = wifiLockReference.get()
        wifiLockHolders.remove(lockHolder)
        if (wifiLockHolders.isEmpty() && lock != null) {
            wifiLockReference.set(null)
            lock.release()
            UMLog.l(UMLog.ERROR, 699,
                    "WiFi lock released from object $lockHolder")
        }

    }

    /**
     * {@inheritDoc}
     */
    override fun onDestroy() {


        mContext.unregisterReceiver(networkStateChangeReceiver)

        super.onDestroy()
    }


    companion object {

        @JvmStatic
        private val BLUETOOTH_ON_OR_TURNING_ON_STATES = listOf(BluetoothAdapter.STATE_ON,
                BluetoothAdapter.STATE_TURNING_ON)

        @JvmStatic
        private val WIFI_ON_OR_TURNING_ON_STATES = listOf(WifiManager.WIFI_STATE_ENABLED,
                WifiManager.WIFI_STATE_ENABLING)
    }
}
