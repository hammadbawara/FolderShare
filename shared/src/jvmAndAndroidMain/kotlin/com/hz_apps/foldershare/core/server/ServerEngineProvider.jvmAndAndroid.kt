package com.hz_apps.foldershare.core.server

import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.netty.Netty

actual val serverEngineFactory: ApplicationEngineFactory<*, *> = Netty
