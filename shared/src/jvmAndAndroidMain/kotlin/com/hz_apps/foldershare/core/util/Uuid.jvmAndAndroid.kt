package com.hz_apps.foldershare.core.util

import java.util.UUID

actual fun generateUuid(): String = UUID.randomUUID().toString()
