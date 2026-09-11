package com.hz_apps.foldershare.core.discovery

import com.hz_apps.foldershare.core.explorer.client.WebDavClient
import com.hz_apps.foldershare.core.explorer.model.LocalFileSource
import com.hz_apps.foldershare.core.explorer.model.RemoteTargetDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class FakeServiceBrowser : ServiceBrowser {
    val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discovered
    var refreshCalled = false

    override fun startDiscovery() {}
    override fun stopDiscovery() {}
    override fun refreshDiscovery() { refreshCalled = true }
}

class FakeWebDavClient(
    var latencyProvider: (String) -> Long? = { null }
) : WebDavClient {
    constructor(latencyMap: Map<String, Long>) : this({ latencyMap[it] })
    var probeCallCount: Int = 0

    override suspend fun propfind(targetUrl: String, username: String?, password: String?, depth: String): String = ""
    override suspend fun mkcol(targetUrl: String, username: String?, password: String?) {}
    override suspend fun putContent(targetUrl: String, content: ByteArray, username: String?, password: String?) {}
    override suspend fun putFile(
        targetUrl: String,
        localFileSource: LocalFileSource,
        username: String?,
        password: String?,
        onProgress: ((Long, Long?) -> Unit)?,
        activeUrlProvider: (() -> String)?
    ) {}
    override suspend fun downloadFile(
        targetUrl: String,
        destinationPath: okio.Path,
        username: String?,
        password: String?,
        onProgress: ((Long, Long?) -> Unit)?,
        activeUrlProvider: (() -> String)?
    ): String = ""
    override fun cleanupPartialDownload(destinationPath: okio.Path) {}
    override suspend fun delete(targetUrl: String, username: String?, password: String?) {}
    override suspend fun move(targetUrl: String, destinationPath: String, username: String?, password: String?) {}
    override suspend fun head(targetUrl: String, username: String?, password: String?): Map<String, List<String>> = emptyMap()
    override suspend fun probeDeviceUuid(targetUrl: String): String? = null
    override suspend fun probeLatency(targetUrl: String): Long? {
        probeCallCount++
        return latencyProvider(targetUrl)
    }
}

class DeviceResolverTest {

    @Test
    fun testResolveEndpointFromDiscoveredDevice() {
        val browser = FakeServiceBrowser()
        val resolver = DefaultDeviceResolver(browser)

        browser._discovered.value = listOf(
            DiscoveredDevice(
                id = "dev_1",
                name = "Discovered Laptop",
                hostAddress = "192.168.1.50",
                port = 34857,
                osDetails = "Linux",
                httpUrl = "http://192.168.1.50:34857",
                webDavUrl = "http://192.168.1.50:34857/",
                isHttps = false
            )
        )

        val resolved = resolver.resolveEndpoint("dev_1")
        assertNotNull(resolved)
        assertEquals("dev_1", resolved.id)
        assertEquals("192.168.1.50", resolved.hostAddress)
        assertEquals(34857, resolved.port)
    }

    @Test
    fun testResolveEndpointFromManualRegistration() {
        val browser = FakeServiceBrowser()
        val resolver = DefaultDeviceResolver(browser)

        val manualDev = RemoteTargetDevice(
            id = "manual_1",
            name = "Manual PC",
            hostAddress = "10.0.0.12",
            port = 8080,
            isHttps = true
        )

        resolver.registerManualDevice(manualDev)

        val resolved = resolver.resolveEndpoint("manual_1")
        assertNotNull(resolved)
        assertEquals("10.0.0.12", resolved.hostAddress)
        assertEquals(8080, resolved.port)
        assertEquals(true, resolved.isHttps)
    }

    @Test
    fun testFallbackRegistration() {
        val browser = FakeServiceBrowser()
        val resolver = DefaultDeviceResolver(browser)

        val fallback = RemoteTargetDevice(
            id = "fallback_dev",
            name = "Fallback Device",
            hostAddress = "192.168.1.99",
            port = 34857
        )

        val resolved = resolver.resolveEndpoint("fallback_dev", fallback)
        assertNotNull(resolved)
        assertEquals("192.168.1.99", resolved.hostAddress)

        // Verify subsequent calls resolve without fallback parameter
        val secondResolved = resolver.resolveEndpoint("fallback_dev")
        assertNotNull(secondResolved)
        assertEquals("192.168.1.99", secondResolved.hostAddress)
    }

    @Test
    fun testObserveDeviceEndpoint() = runBlocking {
        val browser = FakeServiceBrowser()
        val resolver = DefaultDeviceResolver(browser)

        browser._discovered.value = listOf(
            DiscoveredDevice(
                id = "dev_stream",
                name = "Phone",
                hostAddress = "192.168.1.10",
                port = 34857,
                osDetails = "Android",
                httpUrl = "http://192.168.1.10:34857",
                webDavUrl = "http://192.168.1.10:34857/"
            )
        )

        val initialEndpoint = resolver.observeDeviceEndpoint("dev_stream").first()
        assertNotNull(initialEndpoint)
        assertEquals("192.168.1.10", initialEndpoint.hostAddress)

        // Simulate IP address change via mDNS discovery
        browser._discovered.value = listOf(
            DiscoveredDevice(
                id = "dev_stream",
                name = "Phone",
                hostAddress = "192.168.1.25",
                port = 34857,
                osDetails = "Android",
                httpUrl = "http://192.168.1.25:34857",
                webDavUrl = "http://192.168.1.25:34857/"
            )
        )

        val updatedEndpoint = resolver.observeDeviceEndpoint("dev_stream").first()
        assertNotNull(updatedEndpoint)
        assertEquals("192.168.1.25", updatedEndpoint.hostAddress)
    }

    @Test
    fun testMultiNetworkLatencyProbePicksFastest() = runBlocking {
        val browser = FakeServiceBrowser()
        val fakeClient = FakeWebDavClient(
            latencyMap = mapOf(
                "http://192.168.1.10:8080" to 45L,   // Wi-Fi: 45ms
                "http://192.168.10.20:8080" to 2L    // Ethernet: 2ms
            )
        )
        val resolver = DefaultDeviceResolver(browser, fakeClient)

        val devWifi = DiscoveredDevice(
            id = "pixel-7-uuid",
            name = "Pixel 7",
            hostAddress = "192.168.1.10",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.1.10:8080",
            webDavUrl = "http://192.168.1.10:8080/"
        )
        val devEthernet = DiscoveredDevice(
            id = "pixel-7-uuid",
            name = "Pixel 7",
            hostAddress = "192.168.10.20",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.10.20:8080",
            webDavUrl = "http://192.168.10.20:8080/"
        )

        browser._discovered.value = listOf(devWifi, devEthernet)
        delay(100.milliseconds)

        val optimal = resolver.resolveDevice("pixel-7-uuid")
        assertNotNull(optimal)
        assertEquals("192.168.10.20", optimal.hostAddress)
    }

    @Test
    fun testMultiNetworkDynamicFailoverWhenActiveNetworkRemoved() = runBlocking {
        val browser = FakeServiceBrowser()
        val fakeClient = FakeWebDavClient(
            latencyMap = mapOf(
                "http://192.168.1.10:8080" to 2L,
                "http://192.168.10.20:8080" to 15L
            )
        )
        val resolver = DefaultDeviceResolver(browser, fakeClient)

        val dev1 = DiscoveredDevice(
            id = "pixel-7-uuid",
            name = "Pixel 7",
            hostAddress = "192.168.1.10",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.1.10:8080",
            webDavUrl = "http://192.168.1.10:8080/"
        )
        val dev2 = DiscoveredDevice(
            id = "pixel-7-uuid",
            name = "Pixel 7",
            hostAddress = "192.168.10.20",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.10.20:8080",
            webDavUrl = "http://192.168.10.20:8080/"
        )

        browser._discovered.value = listOf(dev1, dev2)
        delay(100.milliseconds)

        val optimalBefore = resolver.resolveDevice("pixel-7-uuid")
        assertEquals("192.168.1.10", optimalBefore?.hostAddress)

        // Simulate dev1 (192.168.1.10) dropping out
        browser._discovered.value = listOf(dev2)

        val optimalAfter = resolver.resolveDevice("pixel-7-uuid")
        assertNotNull(optimalAfter)
        assertEquals("192.168.10.20", optimalAfter.hostAddress)
    }

    @Test
    fun testImmediateFailoverOnInvalidateEndpoint() = runBlocking {
        val browser = FakeServiceBrowser()
        val fakeClient = FakeWebDavClient(
            latencyMap = mapOf(
                "http://192.168.1.10:8080" to 2L,
                "http://192.168.10.20:8080" to 15L
            )
        )
        val resolver = DefaultDeviceResolver(browser, fakeClient)

        val dev1 = DiscoveredDevice(
            id = "pixel-7-uuid",
            name = "Pixel 7",
            hostAddress = "192.168.1.10",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.1.10:8080",
            webDavUrl = "http://192.168.1.10:8080/"
        )
        val dev2 = DiscoveredDevice(
            id = "pixel-7-uuid",
            name = "Pixel 7",
            hostAddress = "192.168.10.20",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.10.20:8080",
            webDavUrl = "http://192.168.10.20:8080/"
        )

        browser._discovered.value = listOf(dev1, dev2)
        delay(100.milliseconds)

        val optimalBefore = resolver.resolveDevice("pixel-7-uuid")
        assertEquals("192.168.1.10", optimalBefore?.hostAddress)

        // Simulate real I/O failure triggering invalidateEndpoint
        resolver.invalidateEndpoint("192.168.1.10", 8080)

        val optimalAfter = resolver.resolveDevice("pixel-7-uuid")
        assertNotNull(optimalAfter)
        assertEquals("192.168.10.20", optimalAfter.hostAddress)
    }

    @Test
    fun testProbingDoesNotLoopInfinitelyOnResolution() = runBlocking {
        val browser = FakeServiceBrowser()
        val fakeClient = FakeWebDavClient(
            latencyMap = mapOf(
                "http://192.168.1.10:8080" to 10L,
                "http://192.168.10.20:8080" to 20L
            )
        )
        val resolver = DefaultDeviceResolver(browser, fakeClient)

        val dev1 = DiscoveredDevice(
            id = "dev-abc",
            name = "Device ABC",
            hostAddress = "192.168.1.10",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.1.10:8080",
            webDavUrl = "http://192.168.1.10:8080/"
        )
        val dev2 = DiscoveredDevice(
            id = "dev-abc",
            name = "Device ABC",
            hostAddress = "192.168.10.20",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.10.20:8080",
            webDavUrl = "http://192.168.10.20:8080/"
        )

        browser._discovered.value = listOf(dev1, dev2)
        delay(100.milliseconds)

        // Repeated queries should not trigger additional probe storms
        repeat(10) {
            resolver.resolveDevice("dev-abc")
        }

        // Only 1 probe per candidate (total 2) should have been issued due to cooldown
        assertEquals(2, fakeClient.probeCallCount)
    }

    @Test
    fun testLoadSpikeDoesNotCauseOscillationToSlowerLink() = runBlocking {
        var fastLinkLatency = 15L // Baseline 15ms
        val slowLinkLatency = 50L // Baseline 50ms

        val browser = FakeServiceBrowser()
        val fakeClient = FakeWebDavClient { url ->
            if (url.contains("10.202.215.47")) fastLinkLatency else slowLinkLatency
        }
        val resolver = DefaultDeviceResolver(browser, fakeClient)

        val devFast = DiscoveredDevice(
            id = "phone-uuid",
            name = "Phone",
            hostAddress = "10.202.215.47",
            port = 45678,
            osDetails = "Android",
            httpUrl = "http://10.202.215.47:45678",
            webDavUrl = "http://10.202.215.47:45678/"
        )
        val devSlow = DiscoveredDevice(
            id = "phone-uuid",
            name = "Phone",
            hostAddress = "192.168.1.3",
            port = 45678,
            osDetails = "Android",
            httpUrl = "http://192.168.1.3:45678",
            webDavUrl = "http://192.168.1.3:45678/"
        )

        browser._discovered.value = listOf(devSlow, devFast)
        delay(100.milliseconds)

        // Initial selection should pick fast link (10.202.215.47)
        val initialSelected = resolver.resolveDevice("phone-uuid")
        assertNotNull(initialSelected)
        assertEquals("10.202.215.47", initialSelected.hostAddress)

        // Simulate active file transfer causing a temporary spike (350ms) on the fast link
        fastLinkLatency = 350L
        resolver.refresh()
        delay(100.milliseconds)

        // Resolver should still select 10.202.215.47 or stay on it without oscillating to slower 50ms link
        val afterSpikeSelected = resolver.resolveDevice("phone-uuid")
        assertNotNull(afterSpikeSelected)
        assertEquals("10.202.215.47", afterSpikeSelected.hostAddress)
    }

    @Test
    fun testGenuineDegradationSwitchesLinkAfterHistoryEviction() = runBlocking {
        var fastLinkLatency = 10L
        val backupLinkLatency = 40L

        val browser = FakeServiceBrowser()
        val fakeClient = FakeWebDavClient { url ->
            if (url.contains("10.0.0.1")) fastLinkLatency else backupLinkLatency
        }
        val resolver = DefaultDeviceResolver(browser, fakeClient)

        val dev1 = DiscoveredDevice(
            id = "test-uuid",
            name = "Test Device",
            hostAddress = "10.0.0.1",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://10.0.0.1:8080",
            webDavUrl = "http://10.0.0.1:8080/"
        )
        val dev2 = DiscoveredDevice(
            id = "test-uuid",
            name = "Test Device",
            hostAddress = "192.168.1.2",
            port = 8080,
            osDetails = "Android",
            httpUrl = "http://192.168.1.2:8080",
            webDavUrl = "http://192.168.1.2:8080/"
        )

        browser._discovered.value = listOf(dev1, dev2)
        delay(100.milliseconds)
        assertEquals("10.0.0.1", resolver.resolveDevice("test-uuid")?.hostAddress)

        // Simulate link 10.0.0.1 permanently degrading to 300ms across 5 probe cycles (evicting the 10ms baseline)
        fastLinkLatency = 300L
        repeat(5) {
            resolver.refresh()
            delay(100.milliseconds)
        }

        // Now backup link (40ms) is genuinely much better than degraded link (300ms), so it switches
        val finalSelected = resolver.resolveDevice("test-uuid")
        assertNotNull(finalSelected)
        assertEquals("192.168.1.2", finalSelected.hostAddress)
    }
}

