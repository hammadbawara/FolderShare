package com.hz_apps.foldershare.core.discovery

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class JvmDeviceDiscoveryEngineTest {

    @Test
    fun testEngineInstantiationAndLifecycle() = runBlocking {
        val engine = createDeviceDiscoveryEngine() as JvmDeviceDiscoveryEngine
        assertNotNull(engine.discoveredDevices.value)

        // Test start & refresh & stop discovery
        engine.startDiscovery()
        engine.refreshDiscovery()
        engine.stopDiscovery()

        // Test register & unregister service
        engine.registerService(
            deviceName = "TestDevice",
            port = 8080,
            osDetails = "Linux Test OS",
            isAuthRequired = false,
            isHttpsEnabled = false,
            deviceUuid = "test-uuid-1234"
        )
        engine.unregisterService()

        // Teardown
        engine.dispose()
        assertTrue(engine.discoveredDevices.value.isEmpty())
    }

    @Test
    fun testMultipleRegisterAndUnregisterService() = runBlocking {
        val engine = createDeviceDiscoveryEngine() as JvmDeviceDiscoveryEngine

        engine.registerService(
            deviceName = "Device 1",
            port = 8080,
            osDetails = "Linux",
            deviceUuid = "uuid-1111"
        )
        delay(200.milliseconds)

        // Re-register with different parameters
        engine.registerService(
            deviceName = "Device 2",
            port = 8081,
            osDetails = "Linux Updated",
            deviceUuid = "uuid-2222"
        )
        delay(200.milliseconds)

        engine.unregisterService()
        engine.dispose()
        assertTrue(engine.discoveredDevices.value.isEmpty())
    }

    @Test
    fun testDiscoveryAndDisposalCleanState() = runBlocking {
        val engine = createDeviceDiscoveryEngine() as JvmDeviceDiscoveryEngine

        engine.startDiscovery()
        engine.registerService(
            deviceName = "DiscoveryDevice",
            port = 9090,
            osDetails = "Desktop",
            deviceUuid = "disc-uuid-99"
        )
        delay(300.milliseconds)

        engine.stopDiscovery()
        engine.unregisterService()
        engine.dispose()

        assertEquals(emptyList(), engine.discoveredDevices.value)
    }

    @Test
    fun testUnregisterServiceOnClose() = runBlocking {
        val engine = createDeviceDiscoveryEngine() as JvmDeviceDiscoveryEngine

        engine.registerService(
            deviceName = "CloseTestDevice",
            port = 9091,
            osDetails = "Linux",
            deviceUuid = "close-uuid-100"
        )
        delay(200.milliseconds)

        // Simulate app closing: unregisterService and dispose
        engine.unregisterService()
        engine.dispose()

        assertEquals(emptyList(), engine.discoveredDevices.value)
    }
}

