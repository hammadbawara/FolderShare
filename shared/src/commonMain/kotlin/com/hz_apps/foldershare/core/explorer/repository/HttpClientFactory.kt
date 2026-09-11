package com.hz_apps.foldershare.core.explorer.repository

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
