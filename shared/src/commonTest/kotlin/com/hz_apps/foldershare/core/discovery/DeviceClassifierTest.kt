package com.hz_apps.foldershare.core.discovery

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceClassifierTest {

    @Test
    fun testExplicitAttributesTakePrecedence() {
        val classified = DeviceClassifier.classify(
            categoryAttr = "TV",
            platformAttr = "ANDROID_TV",
            osDetailsAttr = "Some Other OS",
            deviceName = "My Screen"
        )
        assertEquals(DeviceCategory.TV, classified.category)
        assertEquals(DevicePlatformType.ANDROID_TV, classified.platformType)
        assertEquals("TV • Android TV", classified.displaySubtitle)
    }

    @Test
    fun testPhoneDetectionFromAttributesAndName() {
        val classified = DeviceClassifier.classify(
            categoryAttr = "PHONE",
            platformAttr = "ANDROID",
            osDetailsAttr = "Android 14",
            deviceName = "Pixel 7"
        )
        assertEquals(DeviceCategory.PHONE, classified.category)
        assertEquals(DevicePlatformType.ANDROID, classified.platformType)
        assertEquals("Phone • Android", classified.displaySubtitle)
    }

    @Test
    fun testComputerWindowsDetection() {
        val classified = DeviceClassifier.classify(
            categoryAttr = null,
            platformAttr = null,
            osDetailsAttr = "Windows 11",
            deviceName = "Hammad's Laptop"
        )
        assertEquals(DeviceCategory.COMPUTER, classified.category)
        assertEquals(DevicePlatformType.WINDOWS, classified.platformType)
        assertEquals("Computer • Windows", classified.displaySubtitle)
    }

    @Test
    fun testComputerLinuxDetection() {
        val classified = DeviceClassifier.classify(
            categoryAttr = null,
            platformAttr = null,
            osDetailsAttr = "Linux",
            deviceName = "Ubuntu Desktop"
        )
        assertEquals(DeviceCategory.COMPUTER, classified.category)
        assertEquals(DevicePlatformType.LINUX, classified.platformType)
        assertEquals("Computer • Linux", classified.displaySubtitle)
    }

    @Test
    fun testTvDetection() {
        val classified = DeviceClassifier.classify(
            categoryAttr = null,
            platformAttr = null,
            osDetailsAttr = "Android TV",
            deviceName = "Living Room TV"
        )
        assertEquals(DeviceCategory.TV, classified.category)
        assertEquals(DevicePlatformType.ANDROID_TV, classified.platformType)
        assertEquals("TV • Android TV", classified.displaySubtitle)
    }

    @Test
    fun testTabletDetection() {
        val classified = DeviceClassifier.classify(
            categoryAttr = null,
            platformAttr = null,
            osDetailsAttr = "Android",
            deviceName = "Galaxy Tab S8"
        )
        assertEquals(DeviceCategory.TABLET, classified.category)
        assertEquals(DevicePlatformType.ANDROID, classified.platformType)
        assertEquals("Tablet • Android", classified.displaySubtitle)
    }

    @Test
    fun testManualIpFallback() {
        val classified = DeviceClassifier.classify(
            categoryAttr = null,
            platformAttr = null,
            osDetailsAttr = "Manual IP",
            deviceName = "Manual Device (192.168.1.10)"
        )
        assertEquals(DeviceCategory.UNKNOWN, classified.category)
        assertEquals(DevicePlatformType.UNKNOWN, classified.platformType)
        assertEquals("Device • Manual IP", classified.displaySubtitle)
    }
}
