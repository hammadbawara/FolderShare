package com.hz_apps.foldershare.core.discovery

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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

private val logger = Logger.withTag("DeviceDiscoveryEngine")

/**
 * mDNS discovery/broadcast engine backed by individual per-interface [JmDNS] instances.
 *
 * Architecture & Reliability Notes:
 *  - Avoids [javax.jmdns.JmmDNS] which creates a JmDNS instance for every InetAddress on the computer,
 *    causing duplicate service registrations and probed RFC 6762 name conflicts on multi-address interfaces.
 *  - Enumerates physical network interfaces and selects exactly one representative IP address per usable interface.
 *  - Periodically reconciles interfaces: creates a [JmDNS] instance per interface, auto-registers active services,
 *    auto-attaches discovery listeners, and purges stale devices when an interface disconnects.
 *  - All state mutations are synchronized via [mutex] to guarantee concurrency safety.
 */
class JvmDeviceDiscoveryEngine : DeviceDiscoveryEngine {

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _advertisedAddresses = MutableStateFlow<List<String>>(emptyList())
    override val advertisedAddresses: StateFlow<List<String>> = _advertisedAddresses.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private data class InterfaceEngine(
        val interfaceName: String,
        val address: InetAddress,
        val jmdns: JmDNS
    )

    private data class RegParams(
        val deviceName: String,
        val port: Int,
        val osDetails: String,
        val isAuthRequired: Boolean,
        val isHttpsEnabled: Boolean,
        val deviceUuid: String?
    )

    private val activeEngines = mutableMapOf<String, InterfaceEngine>()
    private val interfaceDiscoveredDevices = mutableMapOf<String, MutableMap<String, DiscoveredDevice>>()
    private val activeListeners = mutableMapOf<String, ServiceListener>()
    private val registeredServiceInfos = mutableMapOf<String, ServiceInfo>()
    private val selfServiceNames = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var isDiscovering = false
    @Volatile private var lastRegParams: RegParams? = null
    private var reconciliationJob: Job? = null

    private val shutdownHook = Thread {
        runCatching {
            unregisterServiceSynchronous()
        }
    }

    companion object {
        private const val SERVICE_TYPE = "_foldershare._tcp.local."
        private const val RECONCILE_INTERVAL_MS = 3000L
    }

    init {
        runCatching {
            Runtime.getRuntime().addShutdownHook(shutdownHook)
        }
        reconciliationJob = scope.launch {
            while (isActive) {
                runCatching {
                    mutex.withLock {
                        reconcileInterfacesLocked()
                    }
                }.onFailure { e ->
                    logger.warn(e) { "Exception during background interface reconciliation" }
                }
                delay(RECONCILE_INTERVAL_MS)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Interface enumeration & reconciliation
    // ---------------------------------------------------------------------

    private fun getUsableNetworkInterfaces(): Map<String, InetAddress> {
        val result = mutableMapOf<String, InetAddress>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }
            .getOrElse { e ->
                logger.warn(e) { "Failed to enumerate network interfaces" }
                null
            } ?: return emptyMap()

        for (iface in interfaces) {
            runCatching {
                if (!iface.isUp || iface.isLoopback || iface.isPointToPoint) return@runCatching

                val addresses = iface.inetAddresses.toList().filter { addr ->
                    !addr.isLoopbackAddress && !addr.isAnyLocalAddress && isUsableHostAddress(addr.hostAddress)
                }
                if (addresses.isEmpty()) return@runCatching

                val representative = addresses.firstOrNull { it is Inet4Address && !it.isLinkLocalAddress }
                    ?: addresses.firstOrNull { it is Inet4Address }
                    ?: addresses.firstOrNull { !it.isLinkLocalAddress }
                    ?: addresses.firstOrNull()

                if (representative != null) {
                    result[iface.name] = representative
                }
            }
        }
        if (result.isEmpty()) {
            logger.warn { "No usable network interfaces found for mDNS discovery" }
        }
        return result
    }

    private fun reconcileInterfacesLocked() {
        val currentInterfaces = getUsableNetworkInterfaces()

        // 1. Close removed or address-changed interfaces
        val removedOrChangedNames = activeEngines.keys.filter { ifaceName ->
            val currentAddr = currentInterfaces[ifaceName]
            currentAddr == null || currentAddr != activeEngines[ifaceName]?.address
        }

        for (ifaceName in removedOrChangedNames) {
            logger.info { "Closing JmDNS instance for removed/changed interface '$ifaceName'" }
            closeInterfaceEngineLocked(ifaceName)
        }

        // 2. Initialize new interfaces
        for ((ifaceName, address) in currentInterfaces) {
            if (!activeEngines.containsKey(ifaceName)) {
                val jmdns = runCatching { JmDNS.create(address, address.hostAddress) }
                    .onFailure { e ->
                        logger.error(e) { "Failed to create JmDNS instance on interface '$ifaceName' (${address.hostAddress})" }
                    }
                    .getOrNull()

                if (jmdns != null) {
                    logger.info { "Initialized JmDNS on interface '$ifaceName' (${address.hostAddress})" }
                    val engine = InterfaceEngine(ifaceName, address, jmdns)
                    activeEngines[ifaceName] = engine
                    interfaceDiscoveredDevices[ifaceName] = mutableMapOf()

                    lastRegParams?.let { params ->
                        registerServiceOnEngineLocked(engine, params)
                    }

                    if (isDiscovering) {
                        attachServiceListenerLocked(engine)
                        runCatching {
                            val existing = jmdns.list(SERVICE_TYPE, 1000L)
                            for (info in existing) {
                                processDiscoveredServiceLocked(ifaceName, info, info.name)
                            }
                        }.onFailure { e ->
                            logger.warn(e) { "Error listing existing services on new interface '$ifaceName'" }
                        }
                    }
                }
            }
        }

        updateCombinedDiscoveredDevicesLocked()
    }

    private fun closeInterfaceEngineLocked(ifaceName: String) {
        val engine = activeEngines.remove(ifaceName) ?: return

        val registeredInfo = registeredServiceInfos.remove(ifaceName)
        if (registeredInfo != null) {
            runCatching { engine.jmdns.unregisterService(registeredInfo) }
                .onFailure { e -> logger.warn(e) { "Error unregistering service on closing interface '$ifaceName'" } }
            updateAdvertisedAddressesLocked()
        }

        val listener = activeListeners.remove(ifaceName)
        if (listener != null) {
            runCatching { engine.jmdns.removeServiceListener(SERVICE_TYPE, listener) }
                .onFailure { e -> logger.warn(e) { "Error removing service listener on closing interface '$ifaceName'" } }
        }

        runCatching { engine.jmdns.close() }
            .onFailure { e -> logger.warn(e) { "Error closing JmDNS instance for interface '$ifaceName'" } }
        interfaceDiscoveredDevices.remove(ifaceName)
    }

    // ---------------------------------------------------------------------
    // Discovery lifecycle
    // ---------------------------------------------------------------------

    override fun startDiscovery() {
        if (isDiscovering) {
            logger.debug { "startDiscovery requested, but discovery is already active" }
            return
        }
        logger.info { "Starting JmDNS discovery for type: $SERVICE_TYPE across active interfaces" }
        isDiscovering = true
        scope.launch {
            mutex.withLock {
                reconcileInterfacesLocked()
                for (engine in activeEngines.values) {
                    attachServiceListenerLocked(engine)
                    runCatching {
                        val existing = engine.jmdns.list(SERVICE_TYPE, 1000L)
                        for (info in existing) {
                            processDiscoveredServiceLocked(engine.interfaceName, info, info.name)
                        }
                    }.onFailure { e ->
                        logger.warn(e) { "Error querying existing services on interface '${engine.interfaceName}'" }
                    }
                }
                updateCombinedDiscoveredDevicesLocked()
            }
        }
    }

    override fun refreshDiscovery() {
        logger.debug { "Refreshing JmDNS discovery" }
        isDiscovering = true
        scope.launch {
            mutex.withLock {
                reconcileInterfacesLocked()
                for (engine in activeEngines.values) {
                    attachServiceListenerLocked(engine)
                    runCatching {
                        val services = engine.jmdns.list(SERVICE_TYPE, 1500L)
                        for (info in services) {
                            processDiscoveredServiceLocked(engine.interfaceName, info, info.name)
                        }
                    }.onFailure { e ->
                        logger.warn(e) { "Error during refresh service query on interface '${engine.interfaceName}'" }
                    }
                }
                updateCombinedDiscoveredDevicesLocked()
            }
        }
    }

    override fun stopDiscovery() {
        if (!isDiscovering) {
            logger.debug { "stopDiscovery requested, but discovery is not active" }
            return
        }
        logger.info { "Stopping JmDNS discovery across active interfaces" }
        isDiscovering = false

        scope.launch {
            mutex.withLock {
                for (engine in activeEngines.values) {
                    val listener = activeListeners.remove(engine.interfaceName)
                    if (listener != null) {
                        runCatching { engine.jmdns.removeServiceListener(SERVICE_TYPE, listener) }
                            .onFailure { e -> logger.warn(e) { "Error removing service listener on interface '${engine.interfaceName}'" } }
                    }
                }
            }
        }
    }

    fun dispose() {
        logger.info { "Disposing JvmDeviceDiscoveryEngine" }
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
        isDiscovering = false
        lastRegParams = null

        reconciliationJob?.cancel()
        reconciliationJob = null

        runBlocking {
            runCatching {
                mutex.withLock {
                    unregisterServiceInternalLocked()
                    for (ifaceName in activeEngines.keys.toList()) {
                        closeInterfaceEngineLocked(ifaceName)
                    }
                    activeEngines.clear()
                    interfaceDiscoveredDevices.clear()
                    activeListeners.clear()
                    _discoveredDevices.value = emptyList()
                }
            }
        }

        scope.cancel()
    }

    // ---------------------------------------------------------------------
    // Service registration (broadcasting)
    // ---------------------------------------------------------------------

    override fun registerService(
        deviceName: String,
        port: Int,
        osDetails: String,
        isAuthRequired: Boolean,
        isHttpsEnabled: Boolean,
        deviceUuid: String?
    ) {
        val params = RegParams(deviceName, port, osDetails, isAuthRequired, isHttpsEnabled, deviceUuid)
        logger.info { "Registering JmDNS service: name='$deviceName', port=$port, os='$osDetails', auth=$isAuthRequired, https=$isHttpsEnabled, uuid=$deviceUuid" }
        lastRegParams = params
        scope.launch {
            mutex.withLock {
                reconcileInterfacesLocked()
                unregisterServiceInternalLocked()

                val sanitizedUuid = params.deviceUuid?.replace("-", "")?.take(12) ?: "device"
                val sanitizedName = params.deviceName.replace(Regex("[ .]"), "_")
                val uniqueServiceName = "FolderShare_${sanitizedName}_${sanitizedUuid}"
                selfServiceNames.add(uniqueServiceName)

                for (engine in activeEngines.values) {
                    registerServiceOnEngineLocked(engine, params)
                }
            }
        }
    }

    private fun registerServiceOnEngineLocked(engine: InterfaceEngine, params: RegParams) {
        try {
            val platform = com.hz_apps.foldershare.getPlatform()
            val props = buildMap {
                put("name", params.deviceName)
                put("os", params.osDetails)
                put("category", platform.category.name)
                put("platform", platform.platformType.name)
                put("app", "FolderShare")
                put("auth", params.isAuthRequired.toString())
                put("https", params.isHttpsEnabled.toString())
                if (!params.deviceUuid.isNullOrBlank()) put("uuid", params.deviceUuid)
            }

            val sanitizedUuid = params.deviceUuid?.replace("-", "")?.take(12) ?: "device"
            val sanitizedName = params.deviceName.replace(Regex("[ .]"), "_")
            val uniqueServiceName = "FolderShare_${sanitizedName}_${sanitizedUuid}"

            val info = ServiceInfo.create(SERVICE_TYPE, uniqueServiceName, params.port, 0, 0, props)
            engine.jmdns.registerService(info)
            registeredServiceInfos[engine.interfaceName] = info
            logger.info { "Registered service '$uniqueServiceName' on interface '${engine.interfaceName}' at port ${params.port}" }
            updateAdvertisedAddressesLocked()
        } catch (e: Exception) {
            logger.error(e) { "Failed to register JmDNS service on interface '${engine.interfaceName}'" }
        }
    }

    override fun unregisterService() {
        logger.info { "Unregistering JmDNS service across all interfaces" }
        lastRegParams = null
        unregisterServiceSynchronous()
    }

    private fun unregisterServiceSynchronous() {
        runBlocking {
            runCatching {
                mutex.withLock {
                    unregisterServiceInternalLocked()
                }
            }
        }
    }

    private fun unregisterServiceInternalLocked() {
        for ((ifaceName, info) in registeredServiceInfos) {
            val engine = activeEngines[ifaceName]
            if (engine != null) {
                runCatching { engine.jmdns.unregisterService(info) }
                    .onFailure { e -> logger.warn(e) { "Error unregistering service on interface '$ifaceName'" } }
            }
        }
        registeredServiceInfos.clear()
        selfServiceNames.clear()
        updateAdvertisedAddressesLocked()
    }

    private fun updateAdvertisedAddressesLocked() {
        val activeIps = registeredServiceInfos.keys
            .mapNotNull { ifaceName -> activeEngines[ifaceName]?.address?.hostAddress }
            .distinct()
        _advertisedAddresses.value = activeIps
    }

    // ---------------------------------------------------------------------
    // Service listener & device resolution
    // ---------------------------------------------------------------------

    private fun attachServiceListenerLocked(engine: InterfaceEngine) {
        if (activeListeners.containsKey(engine.interfaceName)) return

        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                logger.debug { "JmDNS service added on '${engine.interfaceName}': '${event.name}'" }
                runCatching {
                    event.dns.requestServiceInfo(event.type, event.name, 2000L)
                }.onFailure { e ->
                    logger.warn(e) { "Failed requesting service info for '${event.name}' on '${engine.interfaceName}'" }
                }
            }

            override fun serviceRemoved(event: ServiceEvent) {
                val lostName = event.name
                logger.info { "JmDNS service removed on '${engine.interfaceName}': '$lostName'" }
                scope.launch {
                    mutex.withLock {
                        val map = interfaceDiscoveredDevices[engine.interfaceName]
                        if (map != null) {
                            map.remove(lostName)
                            updateCombinedDiscoveredDevicesLocked()
                        }
                    }
                }
            }

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info ?: return
                logger.debug { "JmDNS service resolved event on '${engine.interfaceName}': '${event.name}'" }
                scope.launch {
                    mutex.withLock {
                        processDiscoveredServiceLocked(engine.interfaceName, info, event.name)
                        updateCombinedDiscoveredDevicesLocked()
                    }
                }
            }
        }

        activeListeners[engine.interfaceName] = listener
        runCatching { engine.jmdns.addServiceListener(SERVICE_TYPE, listener) }
            .onFailure { e -> logger.error(e) { "Failed to add JmDNS service listener on interface '${engine.interfaceName}'" } }
    }

    private fun processDiscoveredServiceLocked(ifaceName: String, info: ServiceInfo, eventName: String) {
        val uuid = info.getPropertyString("uuid").takeIf { !it.isNullOrBlank() } ?: eventName

        val ipv4Host = info.inet4Addresses
            .firstOrNull { it != null && !it.isLoopbackAddress && isUsableHostAddress(it.hostAddress) }
            ?.hostAddress
            ?: info.inetAddresses.filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && isUsableHostAddress(it.hostAddress) }
                ?.hostAddress
            ?: info.hostAddresses.firstOrNull { isUsableHostAddress(it) }

        val host = (ipv4Host ?: info.hostAddresses.firstOrNull { isUsableHostAddress(it) })
            ?.takeIf { isUsableHostAddress(it) }

        if (host == null) {
            logger.warn { "Service '$eventName' on '$ifaceName' resolved with unusable host '${info.hostAddresses.joinToString()}'" }
            return
        }

        val port = info.port
        if (port <= 0) {
            logger.warn { "Service '$eventName' on '$ifaceName' resolved with invalid port: $port" }
            return
        }

        val rawName = info.getPropertyString("name") ?: eventName
        val os = info.getPropertyString("os") ?: "Folder Share Device"
        val categoryAttr = info.getPropertyString("category")
        val platformAttr = info.getPropertyString("platform")
        val isAuthReq = info.getPropertyString("auth") == "true"
        val isHttps = info.getPropertyString("https") == "true"
        val scheme = if (isHttps) "https" else "http"

        val classified = DeviceClassifier.classify(
            categoryAttr = categoryAttr,
            platformAttr = platformAttr,
            osDetailsAttr = os,
            deviceName = rawName
        )

        val formattedHost = formatHostForUrl(host)
        val device = DiscoveredDevice(
            id = uuid,
            serviceName = eventName,
            name = rawName,
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

        logger.info { "Discovered device on '$ifaceName': name='$rawName', host=$host, port=$port, category=${classified.category}, platform=${classified.platformType}, auth=$isAuthReq" }
        val devMap = interfaceDiscoveredDevices.getOrPut(ifaceName) { mutableMapOf() }
        devMap[eventName] = device
    }

    private fun updateCombinedDiscoveredDevicesLocked() {
        val allDevices = interfaceDiscoveredDevices.values
            .flatMap { it.values }
            .distinctBy { "${it.id}:${it.hostAddress}:${it.port}" }
        _discoveredDevices.value = allDevices
    }
}

actual fun createDeviceDiscoveryEngine(): DeviceDiscoveryEngine = JvmDeviceDiscoveryEngine()