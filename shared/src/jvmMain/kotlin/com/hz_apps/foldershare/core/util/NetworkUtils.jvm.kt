package com.hz_apps.foldershare.core.util

import java.net.Inet4Address
import java.net.NetworkInterface

actual fun getLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

actual fun getAllLocalIpAddresses(): List<String> {
    val ips = mutableListOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    ips.add(addr.hostAddress)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return ips
}
