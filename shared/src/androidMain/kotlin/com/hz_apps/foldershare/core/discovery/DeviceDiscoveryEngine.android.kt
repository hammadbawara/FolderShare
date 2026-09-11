package com.hz_apps.foldershare.core.discovery

import android.content.Context
import android.os.Build
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.annotation.RequiresApi
import co.touchlab.kermit.Logger
import com.hz_apps.foldershare.core.util.debug
import com.hz_apps.foldershare.core.util.error
import com.hz_apps.foldershare.core.util.formatHostForUrl
import com.hz_apps.foldershare.core.util.info
import com.hz_apps.foldershare.core.util.isUsableHostAddress
import com.hz_apps.foldershare.core.util.warn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("DeviceDiscoveryEngine")

class AndroidDeviceDiscoveryEngine : DeviceDiscoveryEngine {
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    override val advertisedAddresses: Flow<List<String>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(com.hz_apps.foldershare.core.util.getAllLocalIpAddresses())
            delay(3.seconds)
        }
    }.flowOn(Dispatchers.IO)

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var nsdManager: NsdManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false
    private val resolveChannel = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var resolveJob: Job? = null

    companion object {
        private const val SERVICE_TYPE = "_foldershare._tcp."
    }

    private fun getNsdManager(): NsdManager? {
        if (nsdManager == null) {
            val ctx = AndroidContextProvider.applicationContext
            if (ctx == null) {
                logger.warn { "AndroidContextProvider.applicationContext is null; NsdManager unavailable" }
                return null
            }
            nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsdManager == null) {
                logger.warn { "NsdManager system service is unavailable" }
            }
        }
        return nsdManager
    }

    private fun acquireMulticastLock() {
        try {
            val ctx = AndroidContextProvider.applicationContext ?: return
            val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi != null && (multicastLock == null || multicastLock?.isHeld == false)) {
                multicastLock = wifi.createMulticastLock("FolderShareMulticastLock").apply {
                    setReferenceCounted(true)
                    acquire()
                }
                logger.debug { "Acquired Wifi MulticastLock" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to acquire Wifi MulticastLock" }
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                logger.debug { "Released Wifi MulticastLock" }
            }
            multicastLock = null
        } catch (e: Exception) {
            logger.warn(e) { "Error releasing Wifi MulticastLock" }
        }
    }

    override fun startDiscovery() {
        if (isDiscovering) {
            logger.debug { "startDiscovery requested, but discovery is already active" }
            return
        }
        logger.info { "Starting mDNS service discovery ($SERVICE_TYPE)" }
        isDiscovering = true
        acquireMulticastLock()
        startResolveQueue()
        startDiscoveryInternal()
    }

    private fun startDiscoveryInternal() {
        val nsd = getNsdManager()
        if (nsd == null) {
            logger.warn { "Cannot start discovery: NsdManager is null" }
            return
        }
        if (discoveryListener != null) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                logger.debug { "NSD discovery started for regType: $regType" }
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                logger.debug { "NSD service found: name='${service.serviceName}', type='${service.serviceType}'" }
                if (service.serviceType.contains("_foldershare._tcp")) {
                    resolveChannel.trySend(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val lostName = service.serviceName
                logger.info { "NSD service lost: '$lostName'" }
                _discoveredDevices.update { list ->
                    list.filterNot { dev ->
                        dev.serviceName == lostName ||
                        (dev.serviceName != null && dev.serviceName.equals(lostName, ignoreCase = true))
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                logger.debug { "NSD discovery stopped for serviceType: $serviceType" }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.error { "NSD start discovery failed for $serviceType, errorCode: $errorCode" }
                try { nsd.stopServiceDiscovery(this) } catch (e: Exception) {
                    logger.warn(e) { "Failed to stop discovery after start discovery failure" }
                }
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.warn { "NSD stop discovery failed for $serviceType, errorCode: $errorCode" }
                try { nsd.stopServiceDiscovery(this) } catch (e: Exception) {
                    logger.warn(e) { "Failed to stop discovery after stop discovery failure" }
                }
                discoveryListener = null
            }
        }

        discoveryListener = listener
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            logger.error(e) { "Failed to initiate nsd.discoverServices" }
            discoveryListener = null
        }
    }

    override fun refreshDiscovery() {
        logger.debug { "Refreshing mDNS discovery" }
        isDiscovering = true
        acquireMulticastLock()
        startResolveQueue()
        scope.launch {
            val nsd = getNsdManager()
            discoveryListener?.let { listener ->
                try { nsd?.stopServiceDiscovery(listener) } catch (e: Exception) {
                    logger.warn(e) { "Error stopping service discovery during refresh" }
                }
            }
            discoveryListener = null
            delay(200.milliseconds)
            startDiscoveryInternal()
        }
    }

    private val nsdCallbackExecutor = java.util.concurrent.Executor { command ->
        try {
            scope.launch(Dispatchers.IO) {
                try {
                    command.run()
                } catch (t: Throwable) {
                    logger.warn(t) { "Error in NSD callback task" }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun startResolveQueue() {
        if (resolveJob != null) return
        resolveJob = scope.launch {
            for (serviceInfo in resolveChannel) {
                kotlinx.coroutines.withTimeoutOrNull(5.seconds) {
                    resolveServiceSingle(serviceInfo)
                }
                delay(150.milliseconds)
            }
        }
    }

    private suspend fun resolveServiceSingle(serviceInfo: NsdServiceInfo) = suspendCancellableCoroutine { continuation ->
        val nsd = getNsdManager()
        if (nsd == null) {
            logger.warn { "Cannot resolve service '${serviceInfo.serviceName}': NsdManager is null" }
            if (continuation.isActive) continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolveServiceModern(nsd, serviceInfo, continuation)
        } else {
            resolveServiceLegacy(nsd, serviceInfo, continuation)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun resolveServiceModern(
        nsd: NsdManager,
        serviceInfo: NsdServiceInfo,
        continuation: kotlinx.coroutines.CancellableContinuation<Unit>
    ) {
        val isDone = java.util.concurrent.atomic.AtomicBoolean(false)
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                if (isDone.compareAndSet(false, true)) {
                    logger.warn { "Failed to register ServiceInfoCallback for '${serviceInfo.serviceName}', errorCode: $errorCode" }
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }

            override fun onServiceUpdated(service: NsdServiceInfo) {
                if (isDone.compareAndSet(false, true)) {
                    try {
                        processResolvedService(service)
                    } finally {
                        try { nsd.unregisterServiceInfoCallback(this) } catch (_: Exception) {}
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }

            override fun onServiceLost() {
                if (isDone.compareAndSet(false, true)) {
                    try { nsd.unregisterServiceInfoCallback(this) } catch (_: Exception) {}
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }

            override fun onServiceInfoCallbackUnregistered() {
                logger.debug { "ServiceInfoCallback unregistered for '${serviceInfo.serviceName}'" }
            }
        }

        continuation.invokeOnCancellation {
            if (isDone.compareAndSet(false, true)) {
                try { nsd.unregisterServiceInfoCallback(callback) } catch (_: Exception) {}
            }
        }

        try {
            nsd.registerServiceInfoCallback(serviceInfo, nsdCallbackExecutor, callback)
        } catch (e: Exception) {
            if (isDone.compareAndSet(false, true)) {
                logger.warn(e) { "Exception calling registerServiceInfoCallback for '${serviceInfo.serviceName}'" }
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveServiceLegacy(
        nsd: NsdManager,
        serviceInfo: NsdServiceInfo,
        continuation: kotlinx.coroutines.CancellableContinuation<Unit>
    ) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                logger.warn { "Failed to resolve NSD service '${service.serviceName}', errorCode: $errorCode" }
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onServiceResolved(service: NsdServiceInfo) {
                try {
                    processResolvedService(service)
                } finally {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }

        try {
            nsd.resolveService(serviceInfo, listener)
        } catch (e: Exception) {
            logger.warn(e) { "Exception calling nsd.resolveService for '${serviceInfo.serviceName}'" }
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    private fun processResolvedService(service: NsdServiceInfo) {
        try {
            val rawHost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                getHostModern(service)
            } else {
                getHostLegacy(service)
            }
            var host: String? = null
            if (rawHost != null) {
                val rawHostAddr = rawHost.hostAddress
                if (rawHost is Inet4Address && isUsableHostAddress(rawHostAddr)) {
                    host = rawHostAddr
                } else {
                    val ipv4FromDns = runCatching {
                        val hostName = rawHost.hostName.substringBefore("%")
                        if (hostName.isNotBlank() && isUsableHostAddress(hostName)) {
                            InetAddress.getAllByName(hostName)
                                .filterIsInstance<Inet4Address>()
                                .firstOrNull { !it.isLoopbackAddress && isUsableHostAddress(it.hostAddress) }
                                ?.hostAddress
                        } else null
                    }.getOrNull()

                    val candidateHost = ipv4FromDns ?: rawHostAddr
                    if (isUsableHostAddress(candidateHost)) {
                        host = candidateHost
                    }
                }
            }

            val port = service.port
            if (host == null) {
                logger.warn { "Service '${service.serviceName}' resolved but host address '${rawHost?.hostAddress}' is unusable" }
            } else if (port <= 0) {
                logger.warn { "Service '${service.serviceName}' resolved with invalid port: $port" }
            } else if (isUsableHostAddress(host)) {
                val attrs = service.attributes
                val name = attrs["name"]?.let { String(it) } ?: service.serviceName
                val os = attrs["os"]?.let { String(it) } ?: "Android"
                val categoryAttr = attrs["category"]?.let { String(it) }
                val platformAttr = attrs["platform"]?.let { String(it) }
                val isAuthReq = attrs["auth"]?.let { String(it) == "true" } ?: false
                val isHttps = attrs["https"]?.let { String(it) == "true" } ?: false
                val scheme = if (isHttps) "https" else "http"

                val classified = DeviceClassifier.classify(
                    categoryAttr = categoryAttr,
                    platformAttr = platformAttr,
                    osDetailsAttr = os,
                    deviceName = name
                )

                val uuid = attrs["uuid"]?.let { String(it) } ?: service.serviceName
                val formattedHost = formatHostForUrl(host)
                val device = DiscoveredDevice(
                    id = uuid,
                    serviceName = service.serviceName,
                    name = name,
                    hostAddress = host,
                    port = port,
                    osDetails = os,
                    category = classified.category,
                    platformType = classified.platformType,
                    httpUrl = "$scheme://$formattedHost:$port",
                    webDavUrl = "$scheme://$formattedHost:$port",
                    isAuthRequired = isAuthReq,
                    isHttps = isHttps,
                    statusText = "Sharing Active",
                    isOnline = true
                )
                logger.info { "Discovered device: name='$name', host=$host, port=$port, category=${classified.category}, platform=${classified.platformType}, auth=$isAuthReq" }
                _discoveredDevices.update { current ->
                    val idx = current.indexOfFirst {
                        (it.serviceName != null && it.serviceName == service.serviceName) ||
                        (it.id == device.id && it.hostAddress == host && it.port == port)
                    }
                    if (idx >= 0) {
                        current.toMutableList().apply { set(idx, device) }
                    } else {
                        current + device
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing resolved service '${service.serviceName}'" }
        }
    }

    @RequiresApi(34)
    private fun getHostModern(service: NsdServiceInfo): InetAddress? {
        val addresses = service.hostAddresses
        return addresses.firstOrNull { it is Inet4Address && isUsableHostAddress(it.hostAddress) }
            ?: addresses.firstOrNull { isUsableHostAddress(it.hostAddress) }
    }

    @Suppress("DEPRECATION")
    private fun getHostLegacy(service: NsdServiceInfo): InetAddress? {
        return service.host
    }

    override fun stopDiscovery() {
        if (!isDiscovering) {
            logger.debug { "stopDiscovery requested, but discovery is not active" }
            return
        }
        logger.info { "Stopping mDNS service discovery" }
        isDiscovering = false

        val nsd = getNsdManager()
        discoveryListener?.let {
            try { nsd?.stopServiceDiscovery(it) } catch (e: Exception) {
                logger.warn(e) { "Error stopping service discovery" }
            }
        }
        discoveryListener = null
        resolveJob?.cancel()
        resolveJob = null
        if (registrationListener == null) {
            releaseMulticastLock()
        }
    }

    override fun registerService(
        deviceName: String,
        port: Int,
        osDetails: String,
        isAuthRequired: Boolean,
        isHttpsEnabled: Boolean,
        deviceUuid: String?
    ) {
        logger.info { "Registering NSD service: name='$deviceName', port=$port, os='$osDetails', auth=$isAuthRequired, https=$isHttpsEnabled, uuid=$deviceUuid" }
        acquireMulticastLock()
        val nsd = getNsdManager()
        if (nsd == null) {
            logger.warn { "Cannot register service '$deviceName': NsdManager is null" }
            return
        }
        unregisterServiceInternal()

        val sanitizedUuid = deviceUuid?.replace("-", "")?.take(12) ?: "device"
        val sanitizedName = deviceName.replace(" ", "_")

        val platform = com.hz_apps.foldershare.getPlatform()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "FolderShare_${sanitizedName}_${sanitizedUuid}"
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("name", deviceName)
            setAttribute("os", osDetails)
            setAttribute("category", platform.category.name)
            setAttribute("platform", platform.platformType.name)
            setAttribute("app", "FolderShare")
            setAttribute("auth", if (isAuthRequired) "true" else "false")
            setAttribute("https", if (isHttpsEnabled) "true" else "false")
            if (!deviceUuid.isNullOrBlank()) {
                setAttribute("uuid", deviceUuid)
            }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                logger.info { "NSD service successfully registered: '${NsdServiceInfo.serviceName}'" }
            }

            override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                logger.error { "NSD service registration failed for '${arg0.serviceName}', errorCode: $arg1" }
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                logger.info { "NSD service unregistered: '${arg0.serviceName}'" }
            }

            override fun onUnregistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                logger.warn { "NSD service unregistration failed for '${arg0.serviceName}', errorCode: $arg1" }
            }
        }

        registrationListener = listener
        try {
            nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            logger.error(e) { "Failed to call nsd.registerService" }
        }
    }

    override fun unregisterService() {
        logger.info { "Unregistering NSD service" }
        unregisterServiceInternal()
        scope.launch {
            delay(1.seconds)
            if (!isDiscovering && registrationListener == null) {
                releaseMulticastLock()
            }
        }
    }

    private fun unregisterServiceInternal() {
        val nsd = getNsdManager()
        registrationListener?.let {
            try { nsd?.unregisterService(it) } catch (e: Exception) {
                logger.warn(e) { "Error calling nsd.unregisterService" }
            }
        }
        registrationListener = null
    }


}

actual fun createDeviceDiscoveryEngine(): DeviceDiscoveryEngine = AndroidDeviceDiscoveryEngine()
