package com.hz_apps.foldershare.core.discovery

import co.touchlab.kermit.Logger
import com.hz_apps.foldershare.core.explorer.client.WebDavClient
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Health statistics and latency measurements for a candidate endpoint.
 */
data class EndpointHealth(
    val recentRtts: List<Long> = emptyList(),
    val lastProbedAt: Long = 0L,
    val consecutiveFailures: Int = 0,
    val isAlive: Boolean = true
) {
    val baselineRtt: Double
        get() = if (recentRtts.isEmpty()) Double.MAX_VALUE else (recentRtts.minOrNull()?.toDouble() ?: Double.MAX_VALUE)
}

/**
 * Abstraction layer for resolving active network endpoints of target devices by their unique ID.
 * Dynamically resolves multi-network candidates, probes link latencies, and routes traffic
 * to the lowest-latency, reachable endpoint with automatic failover and anti-flapping hysteresis.
 */
interface DeviceResolver {
    fun resolveDevice(deviceId: String): DiscoveredDevice?
    fun resolveEndpoint(deviceId: String, fallback: RemoteTargetDevice? = null): RemoteTargetDevice?
    fun observeDevice(deviceId: String): Flow<DiscoveredDevice?>
    fun observeDeviceEndpoint(deviceId: String): Flow<RemoteTargetDevice?>
    fun registerManualDevice(device: RemoteTargetDevice)
    fun invalidateEndpoint(hostAddress: String, port: Int) {}
    fun refresh()
}

class DefaultDeviceResolver(
    private val serviceBrowser: ServiceBrowser,
    private val webDavClient: WebDavClient? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : DeviceResolver {

    private val logger = Logger.withTag("DefaultDeviceResolver")
    private val manualDevices = MutableStateFlow<Map<String, RemoteTargetDevice>>(emptyMap())
    private val endpointHealthMap = MutableStateFlow<Map<String, EndpointHealth>>(emptyMap())
    private val activeSelectedKeys = MutableStateFlow<Map<String, String>>(emptyMap())

    private val probeMutex = Mutex()
    private val probingEndpoints = mutableSetOf<String>()

    private var pollerJob: Job? = null
    private var discoveryObserverJob: Job? = null

    companion object {
        private const val PROBE_INTERVAL_MS = 10_000L
        private const val PROBE_COOLDOWN_MS = 8_000L
        private const val UNPROBED_DEFAULT_LATENCY = 300.0
        private const val MAX_RECENT_RTT_HISTORY = 5
        private const val MAX_CONSECUTIVE_FAILURES = 2
        private const val HYSTERESIS_SWITCH_RATIO = 0.6
    }

    init {
        pollerJob = scope.launch {
            while (isActive) {
                delay(PROBE_INTERVAL_MS)
                probeEligibleCandidates()
            }
        }
        discoveryObserverJob = scope.launch {
            serviceBrowser.discoveredDevices.collect {
                probeEligibleCandidates()
            }
        }
    }

    private fun getEndpointKey(device: DiscoveredDevice): String = "${device.hostAddress}:${device.port}"
    private fun getEndpointKey(device: RemoteTargetDevice): String = "${device.hostAddress}:${device.port}"

    override fun registerManualDevice(device: RemoteTargetDevice) {
        manualDevices.update { current -> current + (device.id to device) }
    }

    override fun invalidateEndpoint(hostAddress: String, port: Int) {
        val key = "$hostAddress:$port"
        endpointHealthMap.update { current ->
            val prev = current[key]
            val failures = (prev?.consecutiveFailures ?: 0) + MAX_CONSECUTIVE_FAILURES
            current + (key to EndpointHealth(
                recentRtts = emptyList(),
                lastProbedAt = System.currentTimeMillis(),
                consecutiveFailures = failures,
                isAlive = false
            ))
        }
        probeEligibleCandidates(force = true)
    }

    private fun probeEligibleCandidates(force: Boolean = false) {
        if (webDavClient == null) return
        val allDevices = serviceBrowser.discoveredDevices.value
        val multiCandidateDevices = allDevices.groupBy { it.id }.filter { it.value.size > 1 }
        if (multiCandidateDevices.isEmpty()) return

        val now = System.currentTimeMillis()
        val currentHealth = endpointHealthMap.value

        for ((_, candidates) in multiCandidateDevices) {
            for (candidate in candidates) {
                val key = getEndpointKey(candidate)
                val health = currentHealth[key]
                val isExpired = health == null || (now - health.lastProbedAt) >= PROBE_COOLDOWN_MS
                if (force || isExpired) {
                    launchProbe(candidate)
                }
            }
        }
    }

    private fun launchProbe(candidate: DiscoveredDevice) {
        if (webDavClient == null) return
        val key = getEndpointKey(candidate)

        scope.launch {
            val shouldProbe = probeMutex.withLock {
                if (key in probingEndpoints) {
                    false
                } else {
                    probingEndpoints.add(key)
                    true
                }
            }

            if (!shouldProbe) return@launch

            try {
                val rtt = webDavClient.probeLatency(candidate.httpUrl)
                val now = System.currentTimeMillis()
                endpointHealthMap.update { current ->
                    val prev = current[key]
                    if (rtt != null) {
                        val newRecent = (prev?.recentRtts ?: emptyList()).takeLast(MAX_RECENT_RTT_HISTORY - 1) + rtt
                        logger.d { "Probe latency for '${candidate.name}' ($key): ${rtt}ms (baseline min: ${newRecent.minOrNull()}ms)" }
                        current + (key to EndpointHealth(
                            recentRtts = newRecent,
                            lastProbedAt = now,
                            consecutiveFailures = 0,
                            isAlive = true
                        ))
                    } else {
                        val failures = (prev?.consecutiveFailures ?: 0) + 1
                        val alive = failures < MAX_CONSECUTIVE_FAILURES
                        logger.d { "Probe failed/timeout for $key (failures=$failures, alive=$alive)" }
                        current + (key to EndpointHealth(
                            recentRtts = if (!alive) emptyList() else (prev?.recentRtts ?: emptyList()),
                            lastProbedAt = now,
                            consecutiveFailures = failures,
                            isAlive = alive
                        ))
                    }
                }
            } catch (e: Exception) {
                val now = System.currentTimeMillis()
                logger.d { "Probe exception for $key: ${e.message}" }
                endpointHealthMap.update { current ->
                    val prev = current[key]
                    val failures = (prev?.consecutiveFailures ?: 0) + 1
                    val alive = failures < MAX_CONSECUTIVE_FAILURES
                    current + (key to EndpointHealth(
                        recentRtts = if (!alive) emptyList() else (prev?.recentRtts ?: emptyList()),
                        lastProbedAt = now,
                        consecutiveFailures = failures,
                        isAlive = alive
                    ))
                }
            } finally {
                probeMutex.withLock {
                    probingEndpoints.remove(key)
                }
            }
        }
    }

    private fun selectOptimalCandidate(
        deviceId: String,
        candidates: List<DiscoveredDevice>,
        healthMap: Map<String, EndpointHealth>,
        activeKey: String?
    ): DiscoveredDevice? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        val aliveCandidates = candidates.filter { candidate ->
            healthMap[getEndpointKey(candidate)]?.isAlive != false
        }
        val pool = if (aliveCandidates.isNotEmpty()) aliveCandidates else candidates

        fun candidateScore(candidate: DiscoveredDevice): Double {
            val health = healthMap[getEndpointKey(candidate)]
            return health?.baselineRtt ?: UNPROBED_DEFAULT_LATENCY
        }

        val bestCandidate = pool.minByOrNull { candidateScore(it) } ?: pool.first()
        val bestKey = getEndpointKey(bestCandidate)

        val currentActiveCandidate = if (activeKey != null) {
            pool.firstOrNull { getEndpointKey(it) == activeKey }
        } else null

        val selected = if (currentActiveCandidate != null) {
            val activeScore = candidateScore(currentActiveCandidate)
            val bestScore = candidateScore(bestCandidate)
            // Hysteresis: only switch away from active link if best candidate is > 40% faster or active is dead
            if (bestKey != activeKey && (activeScore == Double.MAX_VALUE || bestScore < (activeScore * HYSTERESIS_SWITCH_RATIO))) {
                bestCandidate
            } else {
                currentActiveCandidate
            }
        } else {
            bestCandidate
        }

        return selected
    }

    override fun resolveDevice(deviceId: String): DiscoveredDevice? {
        val candidates = serviceBrowser.discoveredDevices.value.filter { it.id == deviceId }
        val selected = selectOptimalCandidate(deviceId, candidates, endpointHealthMap.value, activeSelectedKeys.value[deviceId])
        if (selected != null) {
            val selectedKey = getEndpointKey(selected)
            if (activeSelectedKeys.value[deviceId] != selectedKey) {
                activeSelectedKeys.update { it + (deviceId to selectedKey) }
            }
        }
        return selected
    }

    override fun resolveEndpoint(deviceId: String, fallback: RemoteTargetDevice?): RemoteTargetDevice? {
        val discovered = resolveDevice(deviceId)
        if (discovered != null) {
            return RemoteTargetDevice(
                id = discovered.id,
                name = discovered.name,
                hostAddress = discovered.hostAddress,
                port = discovered.port,
                isHttps = discovered.isHttps
            )
        }

        val manual = manualDevices.value[deviceId]
        if (manual != null) {
            return manual
        }

        if (fallback != null) {
            registerManualDevice(fallback)
            return fallback
        }

        return null
    }

    override fun observeDevice(deviceId: String): Flow<DiscoveredDevice?> {
        return combine(
            serviceBrowser.discoveredDevices,
            endpointHealthMap
        ) { discoveredList, healthMap ->
            val candidates = discoveredList.filter { it.id == deviceId }
            val currentActiveKey = activeSelectedKeys.value[deviceId]
            val selected = selectOptimalCandidate(deviceId, candidates, healthMap, currentActiveKey)
            if (selected != null) {
                val selectedKey = getEndpointKey(selected)
                if (currentActiveKey != selectedKey) {
                    activeSelectedKeys.update { it + (deviceId to selectedKey) }
                }
            }
            selected
        }.distinctUntilChanged()
    }

    override fun observeDeviceEndpoint(deviceId: String): Flow<RemoteTargetDevice?> {
        return combine(
            serviceBrowser.discoveredDevices,
            manualDevices,
            endpointHealthMap
        ) { discoveredList, manualMap, healthMap ->
            val candidates = discoveredList.filter { it.id == deviceId }
            val currentActiveKey = activeSelectedKeys.value[deviceId]
            val optimal = selectOptimalCandidate(deviceId, candidates, healthMap, currentActiveKey)
            if (optimal != null) {
                val optimalKey = getEndpointKey(optimal)
                if (currentActiveKey != optimalKey) {
                    activeSelectedKeys.update { it + (deviceId to optimalKey) }
                }
                RemoteTargetDevice(
                    id = optimal.id,
                    name = optimal.name,
                    hostAddress = optimal.hostAddress,
                    port = optimal.port,
                    isHttps = optimal.isHttps
                )
            } else {
                manualMap[deviceId]
            }
        }.distinctUntilChanged()
    }

    override fun refresh() {
        endpointHealthMap.update { current ->
            current.mapValues { (_, health) ->
                health.copy(lastProbedAt = 0L, consecutiveFailures = 0)
            }
        }
        serviceBrowser.refreshDiscovery()
        probeEligibleCandidates(force = true)
    }
}



