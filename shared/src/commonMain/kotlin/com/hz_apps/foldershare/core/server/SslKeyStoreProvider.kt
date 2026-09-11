package com.hz_apps.foldershare.core.server

import io.ktor.server.engine.ApplicationEngine

expect fun ApplicationEngine.Configuration.configureSsl(port: Int)
