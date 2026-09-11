package com.hz_apps.foldershare.core.server

import io.ktor.server.engine.ApplicationEngineFactory

expect val serverEngineFactory: ApplicationEngineFactory<*, *>
