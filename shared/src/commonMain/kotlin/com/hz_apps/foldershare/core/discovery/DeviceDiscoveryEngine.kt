package com.hz_apps.foldershare.core.discovery

interface DeviceDiscoveryEngine : ServiceAdvertiser, ServiceBrowser

expect fun createDeviceDiscoveryEngine(): DeviceDiscoveryEngine


